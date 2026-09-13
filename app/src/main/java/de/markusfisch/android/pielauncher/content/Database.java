package de.markusfisch.android.pielauncher.content;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.graphics.IconPack;

public class Database {
	private static final String APPS = "apps";
	private static final String COMPONENT_NAME = "component_name";
	private static final String PACKAGE_NAME = "package_name";
	private static final String USER_SERIAL = "user_serial";
	private static final String LABEL = "label";
	private static final String ICON = "icon";
	private static final String UPDATED_AT = "updated_at";
	private static final long NO_USER = -1L;
	private static final String APP_USAGE = "app_usage";
	private static final String SCORE = "score";

	private static final String MENU_ITEMS = "menu_items";
	private static final String MENU_NAME = "menu_name";
	private static final String POSITION = "position";
	private static final String ITEM_KEY = "item_key";

	private static final String HIDDEN_APPS = "hidden_apps";

	private static final String APP_LABELS = "app_labels";
	private static final String APP_ICONS = "app_icons";
	private static final String ID = "_id";
	private static final String FOLDERS = "folders";
	private static final String FOLDER_ITEMS = "folder_items";
	private static final String FOLDER_ID = "folder_id";
	private static final String NAME = "name";
	private static final String HIDE_CONTENTS = "hide_contents";
	private static final String APP_TAGS = "app_tags";
	private static final String TAGS = "tags";

	private static final String PINNED_SHORTCUTS = "pinned_shortcuts";
	private static final String OWNER_PACKAGE = "owner_package";
	private static final String SHORTCUT_ID = "shortcut_id";

	private static final String ICON_MAPPING_SETS = "icon_mapping_sets";
	private static final String ICON_MAPPINGS = "icon_mappings";
	private static final String ICON_PACK = "icon_pack";
	private static final String ICON_PACKAGE = "icon_package";
	private static final String DRAWABLE_NAME = "drawable_name";

	private static final String META = "meta";
	private static final String META_KEY = "meta_key";
	private static final String META_VALUE = "meta_value";
	private static final String FILES_MIGRATED = "files_migrated";
	private static final String PRIMARY_MENU_FILE = "menu";
	private static final String SECONDARY_MENU_FILE = "menu_alt";
	private static final String HIDDEN_APPS_FILE = "hidden";
	private static final String MAPPINGS_FILE = "mappings";
	private static final String MAPPINGS_PREFIX = MAPPINGS_FILE + "-";
	private static final String SEPARATOR = ";";

	// Without apps, which is a cache, and meta, which is bookkeeping.
	private static final String[] BACKUP_TABLES = {
			MENU_ITEMS,
			HIDDEN_APPS,
			APP_LABELS,
			APP_TAGS,
			APP_ICONS,
			FOLDERS,
			FOLDER_ITEMS,
			ICON_MAPPING_SETS,
			ICON_MAPPINGS,
			APP_USAGE,
			PINNED_SHORTCUTS
	};

	private final OpenHelper openHelper;
	private final Context context;

	public Database(Context context) {
		this.context = context.getApplicationContext();
		openHelper = new OpenHelper(this.context);
		migrateFiles();
	}

	public ArrayList<Apps.AppIcon> restoreMenu(
			String name,
			Map<LauncherItemKey, Apps.AppIcon> allApps) {
		ArrayList<Apps.AppIcon> icons = new ArrayList<>();
		Cursor cursor = openHelper.getReadableDatabase().query(MENU_ITEMS,
				new String[]{ITEM_KEY},
				MENU_NAME + "=?",
				new String[]{name},
				null,
				null,
				POSITION);
		try {
			while (cursor.moveToNext()) {
				Apps.AppIcon icon = allApps.get(
						LauncherItemKey.unflattenFromString(
								context, cursor.getString(0)));
				if (icon != null) {
					icons.add(icon);
				}
			}
		} finally {
			cursor.close();
		}
		return icons;
	}

	public void storeMenu(String name, List<Apps.AppIcon> icons) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(MENU_ITEMS, MENU_NAME + "=?", new String[]{name});
			int position = 0;
			for (Apps.AppIcon icon : icons) {
				ContentValues values = new ContentValues();
				values.put(MENU_NAME, name);
				values.put(POSITION, position++);
				values.put(ITEM_KEY, LauncherItemKey.flattenToString(
						context, icon.componentName, icon.userHandle));
				db.insertOrThrow(MENU_ITEMS, null, values);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void restoreHiddenApps(Set<ComponentName> componentNames) {
		componentNames.clear();
		Cursor cursor = openHelper.getReadableDatabase().query(HIDDEN_APPS,
				new String[]{COMPONENT_NAME},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				ComponentName componentName =
						ComponentName.unflattenFromString(cursor.getString(0));
				if (componentName != null) {
					componentNames.add(componentName);
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void storeHiddenApps(Set<ComponentName> componentNames) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(HIDDEN_APPS, null, null);
			for (ComponentName componentName : componentNames) {
				ContentValues values = new ContentValues();
				values.put(COMPONENT_NAME, componentName.flattenToString());
				db.insertOrThrow(HIDDEN_APPS, null, values);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void restorePinnedShortcuts(
			Context context,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		Cursor cursor = openHelper.getReadableDatabase().query(PINNED_SHORTCUTS,
				new String[]{COMPONENT_NAME, USER_SERIAL, OWNER_PACKAGE,
						SHORTCUT_ID, LABEL, ICON},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				ComponentName componentName =
						ComponentName.unflattenFromString(cursor.getString(0));
				String ownerPackage = cursor.getString(2);
				String shortcutId = cursor.getString(3);
				String label = cursor.getString(4);
				byte[] icon = cursor.getBlob(5);
				if (componentName == null || ownerPackage == null ||
						shortcutId == null || label == null || icon == null) {
					continue;
				}
				Bitmap bitmap = BitmapFactory.decodeByteArray(
						icon, 0, icon.length);
				if (bitmap == null) {
					continue;
				}
				UserHandle userHandle = null;
				long serialNumber = cursor.getLong(1);
				if (AppLauncher.HAS_LAUNCHER_APP && serialNumber != NO_USER) {
					userHandle = getUserForSerialNumber(context, serialNumber);
					if (userHandle == null) {
						continue;
					}
				}
				Apps.AppIcon appIcon = new Apps.AppIcon(
						componentName, label, bitmap, userHandle);
				appIcon.shortcutPackage = ownerPackage;
				appIcon.shortcutId = shortcutId;
				apps.put(new LauncherItemKey(componentName, userHandle),
						appIcon);
			}
		} finally {
			cursor.close();
		}
	}

	public void storePinnedShortcut(Context context, Apps.AppIcon appIcon) {
		byte[] icon = getIconBlob(appIcon);
		if (icon == null) {
			return;
		}
		ContentValues values = new ContentValues();
		values.put(COMPONENT_NAME, appIcon.componentName.flattenToString());
		putUserSerial(context, values, appIcon.userHandle);
		values.put(OWNER_PACKAGE, appIcon.shortcutPackage);
		values.put(SHORTCUT_ID, appIcon.shortcutId);
		values.put(LABEL, appIcon.label);
		values.put(ICON, icon);
		openHelper.getWritableDatabase().insertWithOnConflict(
				PINNED_SHORTCUTS, null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
	}

	public void removePinnedShortcut(Context context, Apps.AppIcon appIcon) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (AppLauncher.HAS_LAUNCHER_APP && appIcon.userHandle != null) {
			db.delete(PINNED_SHORTCUTS,
					COMPONENT_NAME + "=? AND " + USER_SERIAL + "=?",
					new String[]{
							appIcon.componentName.flattenToString(),
							String.valueOf(getSerialNumberForUser(
									context, appIcon.userHandle))
					});
		} else {
			db.delete(PINNED_SHORTCUTS, COMPONENT_NAME + "=?",
					new String[]{appIcon.componentName.flattenToString()});
		}
	}

	public void restoreIconMappings(String packageName,
			Map<ComponentName, IconPack.PackAndDrawable> mappings) {
		mappings.clear();
		String iconPack = normalizeIconPack(packageName);
		SQLiteDatabase db = openHelper.getReadableDatabase();
		if (!hasIconMappingSet(db, iconPack) &&
				!iconPack.isEmpty() && hasIconMappingSet(db, "")) {
			iconPack = "";
		}
		Cursor cursor = db.query(ICON_MAPPINGS,
				new String[]{COMPONENT_NAME, ICON_PACKAGE, DRAWABLE_NAME},
				ICON_PACK + "=?",
				new String[]{iconPack},
				null, null, null);
		try {
			while (cursor.moveToNext()) {
				ComponentName componentName =
						ComponentName.unflattenFromString(cursor.getString(0));
				if (componentName != null) {
					mappings.put(componentName,
							new IconPack.PackAndDrawable(
									cursor.getString(1), cursor.getString(2)));
				}
			}
		} finally {
			cursor.close();
		}
		if (iconPack.isEmpty() && packageName != null &&
				!packageName.isEmpty()) {
			storeIconMappings(packageName, mappings);
		}
	}

	public void storeIconMappings(
			String packageName,
			Map<ComponentName, IconPack.PackAndDrawable> mappings) {
		String iconPack = normalizeIconPack(packageName);
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			ContentValues setValues = new ContentValues();
			setValues.put(ICON_PACK, iconPack);
			db.insertWithOnConflict(ICON_MAPPING_SETS, null, setValues,
					SQLiteDatabase.CONFLICT_IGNORE);
			db.delete(ICON_MAPPINGS, ICON_PACK + "=?", new String[]{iconPack});
			for (Map.Entry<ComponentName, IconPack.PackAndDrawable> entry :
					mappings.entrySet()) {
				ContentValues values = new ContentValues();
				values.put(ICON_PACK, iconPack);
				values.put(COMPONENT_NAME,
						entry.getKey().flattenToString());
				values.put(ICON_PACKAGE, entry.getValue().packageName);
				values.put(DRAWABLE_NAME, entry.getValue().drawableName);
				db.insertOrThrow(ICON_MAPPINGS, null, values);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private void migrateFiles() {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (hasMetaValue(db, FILES_MIGRATED)) {
			return;
		}
		db.beginTransaction();
		try {
			migrateMenu(db, PRIMARY_MENU_FILE);
			migrateMenu(db, SECONDARY_MENU_FILE);
			migrateHiddenApps(db);
			migrateIconMappings(db);
			ContentValues values = new ContentValues();
			values.put(META_KEY, FILES_MIGRATED);
			values.put(META_VALUE, "1");
			db.insertOrThrow(META, null, values);
			db.setTransactionSuccessful();
		} catch (IOException e) {
			// Retry the migration on the next process start.
		} finally {
			db.endTransaction();
		}
	}

	private void migrateMenu(SQLiteDatabase db, String fileName)
			throws IOException {
		db.delete(MENU_ITEMS, MENU_NAME + "=?", new String[]{fileName});
		int position = 0;
		for (String itemKey : readLines(fileName)) {
			ContentValues values = new ContentValues();
			values.put(MENU_NAME, fileName);
			values.put(POSITION, position++);
			values.put(ITEM_KEY, itemKey);
			db.insertOrThrow(MENU_ITEMS, null, values);
		}
	}

	private void migrateHiddenApps(SQLiteDatabase db) throws IOException {
		for (String line : readLines(HIDDEN_APPS_FILE)) {
			ComponentName componentName = ComponentName.unflattenFromString(line);
			if (componentName == null) {
				componentName = AppLauncher.getLaunchComponentForPackageName(
						context, line);
			}
			if (componentName == null) {
				continue;
			}
			ContentValues values = new ContentValues();
			values.put(COMPONENT_NAME, componentName.flattenToString());
			db.insertWithOnConflict(HIDDEN_APPS, null, values,
					SQLiteDatabase.CONFLICT_IGNORE);
		}
	}

	private void migrateIconMappings(SQLiteDatabase db) throws IOException {
		for (String fileName : context.fileList()) {
			if (!MAPPINGS_FILE.equals(fileName) &&
					!fileName.startsWith(MAPPINGS_PREFIX)) {
				continue;
			}
			String iconPack = MAPPINGS_FILE.equals(fileName)
					? ""
					: fileName.substring(MAPPINGS_PREFIX.length());
			ContentValues setValues = new ContentValues();
			setValues.put(ICON_PACK, iconPack);
			db.insertWithOnConflict(ICON_MAPPING_SETS, null, setValues,
					SQLiteDatabase.CONFLICT_IGNORE);
			for (String line : readLines(fileName)) {
				String[] parts = line.split(SEPARATOR);
				if (parts.length < 3) {
					continue;
				}
				ComponentName componentName =
						ComponentName.unflattenFromString(parts[0]);
				if (componentName == null) {
					componentName =
							AppLauncher.getLaunchComponentForPackageName(
									context, parts[0]);
				}
				if (componentName == null) {
					continue;
				}
				ContentValues values = new ContentValues();
				values.put(ICON_PACK, iconPack);
				values.put(COMPONENT_NAME,
						componentName.flattenToString());
				values.put(ICON_PACKAGE, parts[1]);
				values.put(DRAWABLE_NAME, parts[2]);
				db.insertWithOnConflict(ICON_MAPPINGS, null, values,
						SQLiteDatabase.CONFLICT_REPLACE);
			}
		}
	}

	private List<String> readLines(String fileName) throws IOException {
		ArrayList<String> lines = new ArrayList<>();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(
					context.openFileInput(fileName)));
		} catch (FileNotFoundException e) {
			return lines;
		}
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} finally {
			reader.close();
		}
		return lines;
	}

	private static String normalizeIconPack(String packageName) {
		return packageName != null ? packageName : "";
	}

	private static boolean hasIconMappingSet(
			SQLiteDatabase db,
			String iconPack) {
		Cursor cursor = db.query(ICON_MAPPING_SETS,
				new String[]{ICON_PACK},
				ICON_PACK + "=?",
				new String[]{iconPack},
				null, null, null);
		try {
			return cursor.moveToFirst();
		} finally {
			cursor.close();
		}
	}

	private static boolean hasMetaValue(SQLiteDatabase db, String key) {
		Cursor cursor = db.query(META,
				new String[]{META_VALUE},
				META_KEY + "=?",
				new String[]{key},
				null, null, null);
		try {
			return cursor.moveToFirst();
		} finally {
			cursor.close();
		}
	}

	public void restoreApps(
			Context context,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		SQLiteDatabase db = openHelper.getReadableDatabase();
		Cursor cursor = db.query(APPS,
				new String[]{COMPONENT_NAME, USER_SERIAL, LABEL, ICON},
				null,
				null,
				null,
				null,
				null);
		try {
			while (cursor.moveToNext()) {
				ComponentName componentName =
						ComponentName.unflattenFromString(cursor.getString(0));
				String label = cursor.getString(2);
				byte[] icon = cursor.getBlob(3);
				if (componentName == null || label == null || icon == null) {
					continue;
				}
				Bitmap bitmap = BitmapFactory.decodeByteArray(
						icon, 0, icon.length);
				UserHandle userHandle = null;
				long serialNumber = cursor.getLong(1);
				if (AppLauncher.HAS_LAUNCHER_APP && serialNumber != NO_USER) {
					userHandle = getUserForSerialNumber(context, serialNumber);
					if (userHandle == null) {
						continue;
					}
					if (AppLauncher.isPrivateProfileLocked(context, userHandle)) {
						continue;
					}
				}
				if (bitmap == null) {
					continue;
				}
				Apps.AppIcon appIcon = new Apps.AppIcon(
						componentName,
						label,
						bitmap,
						userHandle);
				apps.put(new LauncherItemKey(componentName, userHandle),
						appIcon);
			}
		} finally {
			cursor.close();
		}
		restoreFrecency(context, apps);
	}

	public void restoreFrecency(
			Context context,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		for (Apps.AppIcon appIcon : apps.values()) {
			appIcon.frecencyScore = 0d;
			appIcon.frecencyUpdatedAt = 0L;
		}
		Cursor cursor = openHelper.getReadableDatabase().query(APP_USAGE,
				new String[]{COMPONENT_NAME, USER_SERIAL, SCORE, UPDATED_AT},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				ComponentName componentName =
						ComponentName.unflattenFromString(cursor.getString(0));
				if (componentName == null) {
					continue;
				}
				UserHandle userHandle = null;
				long serialNumber = cursor.getLong(1);
				if (AppLauncher.HAS_LAUNCHER_APP && serialNumber != NO_USER) {
					userHandle = getUserForSerialNumber(context, serialNumber);
					if (userHandle == null) {
						continue;
					}
				}
				Apps.AppIcon appIcon = apps.get(
						new LauncherItemKey(componentName, userHandle));
				if (appIcon != null) {
					appIcon.frecencyScore = cursor.getDouble(2);
					appIcon.frecencyUpdatedAt = cursor.getLong(3);
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void recordLaunch(Context context, Apps.AppIcon appIcon) {
		long now = System.currentTimeMillis();
		double score = Frecency.addLaunch(
				appIcon.frecencyScore, appIcon.frecencyUpdatedAt, now);
		ContentValues values = new ContentValues();
		values.put(COMPONENT_NAME, appIcon.componentName.flattenToString());
		values.put(PACKAGE_NAME, appIcon.componentName.getPackageName());
		putUserSerial(context, values, appIcon.userHandle);
		values.put(SCORE, score);
		values.put(UPDATED_AT, now);
		openHelper.getWritableDatabase().insertWithOnConflict(
				APP_USAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
		appIcon.frecencyScore = score;
		appIcon.frecencyUpdatedAt = now;
	}

	public void replaceAllApps(
			Context context,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(APPS, null, null);
			insertApps(context, db, apps);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void replacePackage(
			Context context,
			String packageName,
			UserHandle userHandle,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		Map<LauncherItemKey, Apps.AppIcon> packageApps = new HashMap<>();
		for (Map.Entry<LauncherItemKey, Apps.AppIcon> entry : apps.entrySet()) {
			Apps.AppIcon appIcon = entry.getValue();
			if (packageName.equals(appIcon.componentName.getPackageName()) &&
					(userHandle == null || userHandle.equals(appIcon.userHandle))) {
				packageApps.put(entry.getKey(), appIcon);
			}
		}
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			deletePackage(context, db, packageName, userHandle);
			insertApps(context, db, packageApps);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void removePackage(
			Context context,
			String packageName,
			UserHandle userHandle) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			deletePackage(context, db, packageName, userHandle);
			deleteUsage(context, db, packageName, userHandle);
			deletePinnedShortcuts(context, db, packageName, userHandle);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private void insertApps(
			Context context,
			SQLiteDatabase db,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		long now = System.currentTimeMillis();
		for (Apps.AppIcon appIcon : apps.values()) {
			// Pinned shortcuts share the apps map but are persisted in their
			// own table, so keep them out of the app cache.
			if (Apps.SHORTCUT_PACKAGE.equals(
					appIcon.componentName.getPackageName())) {
				continue;
			}
			byte[] icon = getIconBlob(appIcon);
			if (icon == null) {
				continue;
			}
			ContentValues values = new ContentValues();
			values.put(COMPONENT_NAME, appIcon.componentName.flattenToString());
			values.put(PACKAGE_NAME, appIcon.componentName.getPackageName());
			putUserSerial(context, values, appIcon.userHandle);
			values.put(LABEL, appIcon.label);
			values.put(ICON, icon);
			values.put(UPDATED_AT, now);
			db.insertWithOnConflict(APPS, null, values,
					SQLiteDatabase.CONFLICT_REPLACE);
		}
	}

	private void deletePackage(
			Context context,
			SQLiteDatabase db,
			String packageName,
			UserHandle userHandle) {
		if (AppLauncher.HAS_LAUNCHER_APP && userHandle != null) {
			db.delete(APPS,
					PACKAGE_NAME + "=? AND " + USER_SERIAL + "=?",
					new String[]{
							packageName,
							String.valueOf(getSerialNumberForUser(
									context, userHandle))
					});
		} else {
			db.delete(APPS, PACKAGE_NAME + "=?", new String[]{packageName});
		}
	}

	private void deleteUsage(
			Context context,
			SQLiteDatabase db,
			String packageName,
			UserHandle userHandle) {
		if (AppLauncher.HAS_LAUNCHER_APP && userHandle != null) {
			db.delete(APP_USAGE,
					PACKAGE_NAME + "=? AND " + USER_SERIAL + "=?",
					new String[]{
							packageName,
							String.valueOf(getSerialNumberForUser(
									context, userHandle))
					});
		} else {
			db.delete(APP_USAGE, PACKAGE_NAME + "=?",
					new String[]{packageName});
		}
	}

	private void deletePinnedShortcuts(
			Context context,
			SQLiteDatabase db,
			String packageName,
			UserHandle userHandle) {
		if (AppLauncher.HAS_LAUNCHER_APP && userHandle != null) {
			db.delete(PINNED_SHORTCUTS,
					OWNER_PACKAGE + "=? AND " + USER_SERIAL + "=?",
					new String[]{
							packageName,
							String.valueOf(getSerialNumberForUser(
									context, userHandle))
					});
		} else {
			db.delete(PINNED_SHORTCUTS, OWNER_PACKAGE + "=?",
					new String[]{packageName});
		}
	}

	private static byte[] getIconBlob(Apps.AppIcon appIcon) {
		if (appIcon.iconBytes != null) {
			return appIcon.iconBytes;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!appIcon.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
			return null;
		}
		appIcon.iconBytes = out.toByteArray();
		return appIcon.iconBytes;
	}

	private static void putUserSerial(
			Context context,
			ContentValues values,
			UserHandle userHandle) {
		if (AppLauncher.HAS_LAUNCHER_APP && userHandle != null) {
			values.put(USER_SERIAL, getSerialNumberForUser(
					context, userHandle));
		} else {
			values.put(USER_SERIAL, NO_USER);
		}
	}

	@SuppressLint("UseRequiresApi")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static long getSerialNumberForUser(
			Context context,
			UserHandle userHandle) {
		UserManager um = AppLauncher.getUserManager(context);
		return um != null ? um.getSerialNumberForUser(userHandle) : -1L;
	}

	@SuppressLint("UseRequiresApi")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static UserHandle getUserForSerialNumber(
			Context context,
			long serialNumber) {
		UserManager um = AppLauncher.getUserManager(context);
		return um != null ? um.getUserForSerialNumber(serialNumber) : null;
	}

	public boolean hasMenu(String name) {
		Cursor cursor = openHelper.getReadableDatabase().query(MENU_ITEMS,
				new String[]{ITEM_KEY},
				MENU_NAME + "=?",
				new String[]{name},
				null, null, null, "1");
		try {
			return cursor.moveToFirst();
		} finally {
			cursor.close();
		}
	}

	public void restoreAppLabels(
			Context context,
			Map<LauncherItemKey, String> labels) {
		labels.clear();
		Cursor cursor = openHelper.getReadableDatabase().query(APP_LABELS,
				new String[]{ITEM_KEY, LABEL},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				LauncherItemKey key = LauncherItemKey.unflattenFromString(
						context, cursor.getString(0));
				if (key != null && key.componentName != null) {
					labels.put(key, cursor.getString(1));
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void restoreFolders(
			Context context,
			List<Folders.Folder> folders,
			Map<Long, Set<LauncherItemKey>> items) {
		folders.clear();
		items.clear();
		SQLiteDatabase db = openHelper.getReadableDatabase();
		Cursor cursor = db.query(FOLDERS,
				new String[]{ID, NAME, HIDE_CONTENTS},
				null, null, null, null, NAME);
		try {
			while (cursor.moveToNext()) {
				folders.add(new Folders.Folder(
						cursor.getLong(0),
						cursor.getString(1),
						cursor.getInt(2) != 0));
			}
		} finally {
			cursor.close();
		}
		cursor = db.query(FOLDER_ITEMS,
				new String[]{FOLDER_ID, ITEM_KEY},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				LauncherItemKey key = LauncherItemKey.unflattenFromString(
						context, cursor.getString(1));
				if (key == null || key.componentName == null) {
					continue;
				}
				long id = cursor.getLong(0);
				Set<LauncherItemKey> keys = items.get(id);
				if (keys == null) {
					keys = new HashSet<>();
					items.put(id, keys);
				}
				keys.add(key);
			}
		} finally {
			cursor.close();
		}
	}

	public long insertFolder(String name) {
		ContentValues values = new ContentValues();
		values.put(NAME, name);
		values.put(HIDE_CONTENTS, 0);
		return openHelper.getWritableDatabase().insert(FOLDERS, null, values);
	}

	public void updateFolder(long id, String name, boolean hideContents) {
		ContentValues values = new ContentValues();
		values.put(NAME, name);
		values.put(HIDE_CONTENTS, hideContents ? 1 : 0);
		openHelper.getWritableDatabase().update(FOLDERS, values,
				ID + "=?", new String[]{String.valueOf(id)});
	}

	public void deleteFolder(long id) {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		String[] args = {String.valueOf(id)};
		db.beginTransaction();
		try {
			db.delete(FOLDER_ITEMS, FOLDER_ID + "=?", args);
			db.delete(FOLDERS, ID + "=?", args);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void addToFolder(Context context, long id, LauncherItemKey key) {
		ContentValues values = new ContentValues();
		values.put(FOLDER_ID, id);
		values.put(ITEM_KEY, LauncherItemKey.flattenToString(context,
				key.componentName, key.userHandle));
		openHelper.getWritableDatabase().insertWithOnConflict(FOLDER_ITEMS,
				null, values, SQLiteDatabase.CONFLICT_REPLACE);
	}

	public void removeFromFolder(Context context, long id, LauncherItemKey key) {
		openHelper.getWritableDatabase().delete(FOLDER_ITEMS,
				FOLDER_ID + "=? AND " + ITEM_KEY + "=?",
				new String[]{
						String.valueOf(id),
						LauncherItemKey.flattenToString(context,
								key.componentName, key.userHandle)
				});
	}

	public void restoreAppIcons(
			Context context,
			Map<LauncherItemKey, Bitmap> icons) {
		icons.clear();
		Cursor cursor = openHelper.getReadableDatabase().query(APP_ICONS,
				new String[]{ITEM_KEY, ICON},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				LauncherItemKey key = LauncherItemKey.unflattenFromString(
						context, cursor.getString(0));
				byte[] blob = cursor.getBlob(1);
				if (key == null || key.componentName == null || blob == null) {
					continue;
				}
				Bitmap bitmap = BitmapFactory.decodeByteArray(
						blob, 0, blob.length);
				if (bitmap != null) {
					icons.put(key, bitmap);
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void storeAppIcon(
			Context context,
			LauncherItemKey key,
			Bitmap bitmap) {
		String itemKey = LauncherItemKey.flattenToString(context,
				key.componentName, key.userHandle);
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (bitmap == null) {
			db.delete(APP_ICONS, ITEM_KEY + "=?", new String[]{itemKey});
			return;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
		ContentValues values = new ContentValues();
		values.put(ITEM_KEY, itemKey);
		values.put(ICON, out.toByteArray());
		db.insertWithOnConflict(APP_ICONS, null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
	}

	public void restoreAppTags(
			Context context,
			Map<LauncherItemKey, String> tags) {
		tags.clear();
		Cursor cursor = openHelper.getReadableDatabase().query(APP_TAGS,
				new String[]{ITEM_KEY, TAGS},
				null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				LauncherItemKey key = LauncherItemKey.unflattenFromString(
						context, cursor.getString(0));
				if (key != null && key.componentName != null) {
					tags.put(key, cursor.getString(1));
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void storeAppTags(
			Context context,
			LauncherItemKey key,
			String tags) {
		String itemKey = LauncherItemKey.flattenToString(context,
				key.componentName, key.userHandle);
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (tags == null) {
			db.delete(APP_TAGS, ITEM_KEY + "=?", new String[]{itemKey});
			return;
		}
		ContentValues values = new ContentValues();
		values.put(ITEM_KEY, itemKey);
		values.put(TAGS, tags);
		db.insertWithOnConflict(APP_TAGS, null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
	}

	public void storeAppLabel(
			Context context,
			LauncherItemKey key,
			String label) {
		String itemKey = LauncherItemKey.flattenToString(context,
				key.componentName, key.userHandle);
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (label == null) {
			db.delete(APP_LABELS, ITEM_KEY + "=?", new String[]{itemKey});
			return;
		}
		ContentValues values = new ContentValues();
		values.put(ITEM_KEY, itemKey);
		values.put(LABEL, label);
		db.insertWithOnConflict(APP_LABELS, null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public JSONObject exportTables() throws JSONException {
		SQLiteDatabase db = openHelper.getReadableDatabase();
		JSONObject tables = new JSONObject();
		for (String table : BACKUP_TABLES) {
			JSONArray rows = new JSONArray();
			Cursor cursor = db.query(table, null, null, null, null, null,
					null);
			try {
				String[] columns = cursor.getColumnNames();
				while (cursor.moveToNext()) {
					rows.put(rowToJson(table, columns, cursor));
				}
			} finally {
				cursor.close();
			}
			tables.put(table, rows);
		}
		return tables;
	}

	public void importTables(JSONObject tables) throws JSONException {
		SQLiteDatabase db = openHelper.getWritableDatabase();
		db.beginTransaction();
		try {
			for (String table : BACKUP_TABLES) {
				JSONArray rows = tables.optJSONArray(table);
				if (rows == null) {
					continue;
				}
				Set<String> columns = columnsOf(db, table);
				db.delete(table, null, null);
				for (int i = 0, len = rows.length(); i < len; ++i) {
					ContentValues values = jsonToRow(table, columns,
							rows.getJSONObject(i));
					if (values.size() > 0) {
						db.insertWithOnConflict(table, null, values,
								SQLiteDatabase.CONFLICT_REPLACE);
					}
				}
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private static Set<String> columnsOf(SQLiteDatabase db, String table) {
		Set<String> columns = new HashSet<>();
		Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
		try {
			int nameIndex = cursor.getColumnIndex("name");
			while (cursor.moveToNext()) {
				columns.add(cursor.getString(nameIndex));
			}
		} finally {
			cursor.close();
		}
		return columns;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static JSONObject rowToJson(
			String table,
			String[] columns,
			Cursor cursor) throws JSONException {
		JSONObject row = new JSONObject();
		for (int i = 0; i < columns.length; ++i) {
			switch (cursor.getType(i)) {
				case Cursor.FIELD_TYPE_NULL:
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					row.put(columns[i], cursor.getLong(i));
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					row.put(columns[i], cursor.getDouble(i));
					break;
				case Cursor.FIELD_TYPE_BLOB:
					row.put(columns[i], Base64.encodeToString(
							cursor.getBlob(i), Base64.NO_WRAP));
					break;
				default:
					row.put(columns[i], cursor.getString(i));
					break;
			}
		}
		return row;
	}

	private static ContentValues jsonToRow(
			String table,
			Set<String> columns,
			JSONObject row) throws JSONException {
		ContentValues values = new ContentValues();
		for (Iterator<String> it = row.keys(); it.hasNext(); ) {
			String column = it.next();
			if (!columns.contains(column)) {
				continue;
			}
			if (isBlob(table, column)) {
				values.put(column, Base64.decode(row.getString(column),
						Base64.NO_WRAP));
				continue;
			}
			Object value = row.get(column);
			if (value instanceof Boolean) {
				values.put(column, (Boolean) value ? 1 : 0);
			} else if (value instanceof Integer) {
				values.put(column, (Integer) value);
			} else if (value instanceof Long) {
				values.put(column, (Long) value);
			} else if (value instanceof Double) {
				values.put(column, (Double) value);
			} else {
				values.put(column, value.toString());
			}
		}
		return values;
	}

	private static boolean isBlob(String table, String column) {
		return ICON.equals(column);
	}

	private static class OpenHelper extends SQLiteOpenHelper {
		OpenHelper(Context context) {
			super(context, "app_cache.db", null, 8);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			createAppsTables(db);
			createStorageTables(db);
			createUsageTables(db);
			createShortcutsTable(db);
		}

		private static void createAppsTables(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + APPS + " (" +
					COMPONENT_NAME + " TEXT NOT NULL," +
					PACKAGE_NAME + " TEXT NOT NULL," +
					USER_SERIAL + " INTEGER NOT NULL," +
					LABEL + " TEXT NOT NULL," +
					ICON + " BLOB NOT NULL," +
					UPDATED_AT + " INTEGER NOT NULL," +
					"PRIMARY KEY (" + COMPONENT_NAME + "," +
					USER_SERIAL + "));");
			db.execSQL("CREATE INDEX apps_package_name ON " + APPS +
					" (" + PACKAGE_NAME + ");");
		}

		private static void createStorageTables(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + MENU_ITEMS + " (" +
					MENU_NAME + " TEXT NOT NULL," +
					POSITION + " INTEGER NOT NULL," +
					ITEM_KEY + " TEXT NOT NULL," +
					"PRIMARY KEY (" + MENU_NAME + "," + POSITION + "));");
			db.execSQL("CREATE TABLE " + HIDDEN_APPS + " (" +
					COMPONENT_NAME + " TEXT PRIMARY KEY NOT NULL);");
			createLabelsTable(db);
			createTagsTable(db);
			createIconsTable(db);
			createFolderTables(db);
			db.execSQL("CREATE TABLE " + ICON_MAPPING_SETS + " (" +
					ICON_PACK + " TEXT PRIMARY KEY NOT NULL);");
			db.execSQL("CREATE TABLE " + ICON_MAPPINGS + " (" +
					ICON_PACK + " TEXT NOT NULL," +
					COMPONENT_NAME + " TEXT NOT NULL," +
					ICON_PACKAGE + " TEXT NOT NULL," +
					DRAWABLE_NAME + " TEXT NOT NULL," +
					"PRIMARY KEY (" + ICON_PACK + "," +
					COMPONENT_NAME + "));");
			db.execSQL("CREATE TABLE " + META + " (" +
					META_KEY + " TEXT PRIMARY KEY NOT NULL," +
					META_VALUE + " TEXT NOT NULL);");
		}

		private static void createLabelsTable(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + APP_LABELS + " (" +
					ITEM_KEY + " TEXT PRIMARY KEY NOT NULL," +
					LABEL + " TEXT NOT NULL);");
		}

		private static void createTagsTable(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + APP_TAGS + " (" +
					ITEM_KEY + " TEXT PRIMARY KEY NOT NULL," +
					TAGS + " TEXT NOT NULL);");
		}

		private static void createIconsTable(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + APP_ICONS + " (" +
					ITEM_KEY + " TEXT PRIMARY KEY NOT NULL," +
					ICON + " BLOB NOT NULL);");
		}

		private static void createFolderTables(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + FOLDERS + " (" +
					ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
					NAME + " TEXT NOT NULL," +
					HIDE_CONTENTS + " INTEGER NOT NULL);");
			db.execSQL("CREATE TABLE " + FOLDER_ITEMS + " (" +
					FOLDER_ID + " INTEGER NOT NULL," +
					ITEM_KEY + " TEXT NOT NULL," +
					"PRIMARY KEY (" + FOLDER_ID + "," + ITEM_KEY + "));");
		}

		private static void createUsageTables(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + APP_USAGE + " (" +
					COMPONENT_NAME + " TEXT NOT NULL," +
					PACKAGE_NAME + " TEXT NOT NULL," +
					USER_SERIAL + " INTEGER NOT NULL," +
					SCORE + " REAL NOT NULL," +
					UPDATED_AT + " INTEGER NOT NULL," +
					"PRIMARY KEY (" + COMPONENT_NAME + "," +
					USER_SERIAL + "));");
			db.execSQL("CREATE INDEX app_usage_package_name ON " +
					APP_USAGE + " (" + PACKAGE_NAME + ");");
		}

		private static void createShortcutsTable(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + PINNED_SHORTCUTS + " (" +
					COMPONENT_NAME + " TEXT NOT NULL," +
					USER_SERIAL + " INTEGER NOT NULL," +
					OWNER_PACKAGE + " TEXT NOT NULL," +
					SHORTCUT_ID + " TEXT NOT NULL," +
					LABEL + " TEXT NOT NULL," +
					ICON + " BLOB NOT NULL," +
					"PRIMARY KEY (" + COMPONENT_NAME + "," +
					USER_SERIAL + "));");
		}

		@Override
		public void onUpgrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
			if (oldVersion < 2) {
				createStorageTables(db);
			}
			if (oldVersion < 3) {
				createUsageTables(db);
			}
			if (oldVersion < 4) {
				createShortcutsTable(db);
			}
			if (oldVersion < 5) {
				createLabelsTable(db);
			}
			if (oldVersion < 6) {
				createTagsTable(db);
			}
			if (oldVersion < 7) {
				createIconsTable(db);
			}
			if (oldVersion < 8) {
				createFolderTables(db);
			}
		}

		@Override
		public void onDowngrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
		}
	}
}
