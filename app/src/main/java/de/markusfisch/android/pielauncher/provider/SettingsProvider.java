package de.markusfisch.android.pielauncher.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import de.markusfisch.android.pielauncher.io.SettingsBackup;
import de.markusfisch.android.pielauncher.io.SettingsSync;

// Serves this app's settings to the stable or beta build installed
// beside it. A signature permission guards the provider, so only a build
// signed with the same key can read them.
public class SettingsProvider extends ContentProvider {
	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public String getType(Uri uri) {
		return SettingsBackup.MIME_TYPE;
	}

	// What the other app needs to name this one and to tell whether its
	// settings are older than its own.
	@Override
	public Cursor query(
			Uri uri,
			String[] projection,
			String selection,
			String[] selectionArgs,
			String sortOrder) {
		Context context = getContext();
		if (context == null) {
			return null;
		}
		String packageName = context.getPackageName();
		PackageManager pm = context.getPackageManager();
		String versionName;
		CharSequence label;
		try {
			versionName = pm.getPackageInfo(packageName, 0).versionName;
			label = pm.getApplicationLabel(
					pm.getApplicationInfo(packageName, 0));
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
		if (versionName == null) {
			return null;
		}
		MatrixCursor cursor = new MatrixCursor(new String[]{
				SettingsSync.COLUMN_LABEL,
				SettingsSync.COLUMN_VERSION_NAME});
		cursor.addRow(new Object[]{
				label == null ? packageName : label.toString(),
				versionName});
		return cursor;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		final Context context = getContext();
		if (context == null || !"r".equals(mode)) {
			throw new FileNotFoundException(uri.toString());
		}
		ParcelFileDescriptor[] pipe;
		try {
			pipe = ParcelFileDescriptor.createPipe();
		} catch (IOException e) {
			throw new FileNotFoundException(uri.toString());
		}
		final OutputStream out =
				new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
		// A backup with custom icons in it outgrows the pipe buffer, so
		// it cannot be written before the read end is returned.
		new Thread(() -> {
			try {
				SettingsBackup.export(context, out);
			} catch (IOException | JSONException e) {
				// The other app reads a truncated backup and fails there.
			} finally {
				try {
					out.close();
				} catch (IOException e) {
					// Nothing left to do about it.
				}
			}
		}).start();
		return pipe[0];
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(
			Uri uri,
			ContentValues values,
			String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}
}
