package de.markusfisch.android.pielauncher.io;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

// The stable and the beta build are separate apps with separate data.
// Each serves its settings to the other through SettingsProvider so one
// can be brought in line with the other without going through a file.
public class SettingsSync {
	public static class Counterpart {
		public final Uri uri;
		public final String label;
		public final String versionName;

		private Counterpart(Uri uri, String label, String versionName) {
			this.uri = uri;
			this.label = label;
			this.versionName = versionName;
		}
	}

	public static final String COLUMN_LABEL = "label";
	public static final String COLUMN_VERSION_NAME = "version_name";
	public static final String PATH = "settings";
	public static final String AUTHORITY_SUFFIX = ".settings";

	private static final String BETA_SUFFIX = ".beta";

	public static boolean isBeta(Context context) {
		return context.getPackageName().endsWith(BETA_SUFFIX);
	}

	public static Uri uriOf(String packageName) {
		return Uri.parse("content://" + packageName + AUTHORITY_SUFFIX +
				"/" + PATH);
	}

	public static String versionNameOf(Context context, String packageName) {
		try {
			return context.getPackageManager().getPackageInfo(
					packageName, 0).versionName;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	// Null when the other app is not installed, is too old to serve its
	// settings or is signed with a different key, which makes the
	// signature permission on its provider withhold them.
	public static Counterpart find(Context context) {
		String packageName = otherPackageName(context.getPackageName());
		Cursor cursor;
		try {
			cursor = context.getContentResolver().query(
					uriOf(packageName), null, null, null, null);
		} catch (RuntimeException e) {
			// A SecurityException for a mismatched signature, or an
			// IllegalArgumentException for an authority nothing serves.
			return null;
		}
		if (cursor == null) {
			return null;
		}
		try {
			int labelColumn = cursor.getColumnIndex(COLUMN_LABEL);
			int versionColumn = cursor.getColumnIndex(COLUMN_VERSION_NAME);
			if (!cursor.moveToFirst() || labelColumn < 0 ||
					versionColumn < 0) {
				return null;
			}
			String label = cursor.getString(labelColumn);
			String versionName = cursor.getString(versionColumn);
			if (label == null || versionName == null) {
				return null;
			}
			return new Counterpart(uriOf(packageName), label, versionName);
		} finally {
			cursor.close();
		}
	}

	public static String otherPackageName(String packageName) {
		return packageName.endsWith(BETA_SUFFIX)
				? packageName.substring(0,
						packageName.length() - BETA_SUFFIX.length())
				: packageName + BETA_SUFFIX;
	}

	// Version codes cannot be compared between the two apps because a
	// beta uses the workflow run number for its own ladder. Version names
	// can: a beta names the version it leads to, so it comes before the
	// release of that same version.
	public static int compareVersions(String left, String right) {
		if (left == null || right == null) {
			return 0;
		}
		String[] leftParts = baseOf(left).split("\\.");
		String[] rightParts = baseOf(right).split("\\.");
		for (int i = 0, len = Math.max(leftParts.length,
				rightParts.length); i < len; ++i) {
			int diff = numberAt(leftParts, i) - numberAt(rightParts, i);
			if (diff != 0) {
				return diff < 0 ? -1 : 1;
			}
		}
		int diff = betaOf(left) - betaOf(right);
		return diff == 0 ? 0 : diff < 0 ? -1 : 1;
	}

	private static String baseOf(String versionName) {
		int dash = versionName.indexOf('-');
		return dash < 0 ? versionName : versionName.substring(0, dash);
	}

	// Integer.MAX_VALUE for a release so it wins over every beta that
	// leads to it.
	private static int betaOf(String versionName) {
		int dash = versionName.indexOf('-');
		if (dash < 0) {
			return Integer.MAX_VALUE;
		}
		int dot = versionName.indexOf('.', dash);
		return dot < 0 ? 0 : parse(versionName.substring(dot + 1));
	}

	private static int numberAt(String[] parts, int index) {
		return index < parts.length ? parse(parts[index]) : 0;
	}

	private static int parse(String s) {
		try {
			return Math.max(0, Integer.parseInt(s.trim()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
