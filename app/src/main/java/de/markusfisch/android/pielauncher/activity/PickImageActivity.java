package de.markusfisch.android.pielauncher.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;
import de.markusfisch.android.pielauncher.graphics.Converter;

public class PickImageActivity extends Activity {
	private static final String ITEM_KEY = "item_key";
	private static final int PICK_IMAGE = 1;

	private LauncherItemKey key;

	public static void start(Context context, LauncherItemKey key) {
		Intent intent = new Intent(context, PickImageActivity.class);
		intent.putExtra(ITEM_KEY, LauncherItemKey.flattenToString(context,
				key.componentName, key.userHandle));
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		String itemKey = getIntent() != null
				? getIntent().getStringExtra(ITEM_KEY)
				: null;
		if (itemKey != null) {
			key = LauncherItemKey.unflattenFromString(this, itemKey);
		}
		if (key == null || key.componentName == null ||
				Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			finish();
			return;
		}
		if (state == null) {
			pickImage();
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void pickImage() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*");
		try {
			startActivityForResult(intent, PICK_IMAGE);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.no_file_picker,
					Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	@Override
	protected void onActivityResult(
			int requestCode,
			int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != PICK_IMAGE) {
			return;
		}
		if (resultCode == RESULT_OK && data != null && data.getData() != null) {
			setIcon(data.getData());
		}
		finish();
	}

	private void setIcon(Uri uri) {
		Bitmap bitmap = null;
		try {
			InputStream in = getContentResolver().openInputStream(uri);
			if (in != null) {
				try {
					bitmap = BitmapFactory.decodeStream(in);
				} finally {
					in.close();
				}
			}
		} catch (IOException e) {
			// Handled below.
		}
		if (bitmap == null) {
			Toast.makeText(this, R.string.pick_image_failed,
					Toast.LENGTH_SHORT).show();
			return;
		}
		PieLauncherApp.appIcons.store(this, key,
				Converter.getBitmapFromDrawable(
						new BitmapDrawable(getResources(), bitmap)));
		PieLauncherApp.apps.indexAppsAsync(this);
	}
}
