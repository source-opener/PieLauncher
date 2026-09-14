package de.markusfisch.android.pielauncher.content;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.graphics.Converter;

public class Folders {
	public static class Folder {
		public final long id;
		public final String name;
		public final boolean hideContents;

		public Folder(long id, String name, boolean hideContents) {
			this.id = id;
			this.name = name;
			this.hideContents = hideContents;
		}
	}

	// Synthetic package for the ComponentName of folders so they can be
	// ordinary AppIcons in the drawer list without ever colliding with a
	// real package.
	public static final String FOLDER_PACKAGE =
			"de.markusfisch.android.pielauncher.folder";

	private final List<Folder> folders = new ArrayList<>();
	private final Map<Long, Set<LauncherItemKey>> items = new HashMap<>();
	private final List<Apps.AppIcon> icons = new ArrayList<>();

	private Bitmap bitmap;
	// Not synchronized in invalidate() so storages can flag a change
	// without taking this lock while this class is taking theirs.
	private volatile boolean restored = false;

	public static boolean isFolder(Apps.AppIcon icon) {
		return icon != null && isFolderPackage(icon.componentName);
	}

	public static boolean isFolder(LauncherItemKey key) {
		return key != null && isFolderPackage(key.componentName);
	}

	public static long idOf(Apps.AppIcon icon) {
		return parseId(icon.componentName);
	}

	public static long idOf(LauncherItemKey key) {
		return parseId(key.componentName);
	}

	private static boolean isFolderPackage(ComponentName componentName) {
		return componentName != null &&
				FOLDER_PACKAGE.equals(componentName.getPackageName());
	}

	private static long parseId(ComponentName componentName) {
		try {
			return Long.parseLong(componentName.getClassName());
		} catch (NumberFormatException e) {
			return -1L;
		}
	}

	// Folders carry their name in their own table but share the storages
	// apps use for tags and custom icons, keyed like this.
	public static LauncherItemKey keyOf(long id) {
		return new LauncherItemKey(
				new ComponentName(FOLDER_PACKAGE, String.valueOf(id)),
				null);
	}

	public synchronized List<Folder> getFolders() {
		return new ArrayList<>(folders);
	}

	// The icon the pie menu holds for a folder, so a menu can be restored
	// from a component name like an app's.
	public synchronized Apps.AppIcon getIcon(long id) {
		for (Apps.AppIcon icon : icons) {
			if (idOf(icon) == id) {
				return icon;
			}
		}
		return null;
	}

	public synchronized Folder getFolder(long id) {
		for (Folder folder : folders) {
			if (folder.id == id) {
				return folder;
			}
		}
		return null;
	}

	public synchronized boolean contains(long id, LauncherItemKey key) {
		Set<LauncherItemKey> keys = items.get(id);
		return keys != null && keys.contains(key);
	}

	public synchronized Set<LauncherItemKey> getItems(long id) {
		Set<LauncherItemKey> keys = items.get(id);
		return keys == null
				? Collections.<LauncherItemKey>emptySet()
				: new HashSet<>(keys);
	}

	public synchronized Set<LauncherItemKey> getHiddenItems() {
		Set<LauncherItemKey> hidden = new HashSet<>();
		for (Folder folder : folders) {
			if (folder.hideContents) {
				Set<LauncherItemKey> keys = items.get(folder.id);
				if (keys != null) {
					hidden.addAll(keys);
				}
			}
		}
		return hidden;
	}

	// Query is expected to be folded and lower case, like in AppSearch.
	public synchronized List<Apps.AppIcon> match(
			String query,
			int strategy,
			Locale locale) {
		if (query.isEmpty()) {
			return new ArrayList<>(icons);
		}
		List<Apps.AppIcon> matches = new ArrayList<>();
		for (Apps.AppIcon icon : icons) {
			String name = AppSearch.fold(icon.label.toLowerCase(locale));
			if (AppSearch.matches(name, query, strategy) ||
					AppSearch.hasTag(keyOf(idOf(icon)), query, strategy)) {
				matches.add(icon);
			}
		}
		return matches;
	}

	public synchronized long create(Context context, String name) {
		long id = PieLauncherApp.getDatabase(context).insertFolder(name);
		reload(context);
		return id;
	}

	public synchronized void update(
			Context context,
			long id,
			String name,
			boolean hideContents) {
		PieLauncherApp.getDatabase(context).updateFolder(id, name,
				hideContents);
		reload(context);
	}

	public synchronized void delete(Context context, long id) {
		// Drop the tags and the custom icon too, or they would linger in
		// the database and in every settings export made afterwards.
		LauncherItemKey key = keyOf(id);
		PieLauncherApp.appTags.store(context, key, null);
		PieLauncherApp.appIcons.store(context, key, null);
		PieLauncherApp.getDatabase(context).deleteFolder(id);
		reload(context);
	}

	public synchronized void add(
			Context context,
			long id,
			LauncherItemKey key) {
		PieLauncherApp.getDatabase(context).addToFolder(context, id, key);
		reload(context);
	}

	public synchronized void remove(
			Context context,
			long id,
			LauncherItemKey key) {
		PieLauncherApp.getDatabase(context).removeFromFolder(context, id, key);
		reload(context);
	}

	public synchronized void restore(Context context) {
		if (!restored) {
			reload(context);
		}
	}

	public void invalidate() {
		restored = false;
	}

	private synchronized void reload(Context context) {
		PieLauncherApp.appIcons.restore(context);
		if (bitmap == null) {
			// One bitmap for all folders; the drawer draws hundreds of
			// icons per frame and does not need one more per folder.
			bitmap = Converter.getBitmapFromDrawable(
					context.getResources(), R.drawable.ic_folder);
		}
		PieLauncherApp.getDatabase(context).restoreFolders(context, folders,
				items);
		icons.clear();
		for (Folder folder : folders) {
			LauncherItemKey key = keyOf(folder.id);
			Bitmap custom = PieLauncherApp.appIcons.get(key);
			icons.add(new Apps.AppIcon(
					key.componentName,
					folder.name,
					custom != null ? custom : bitmap,
					null));
		}
		restored = true;
	}
}
