package de.markusfisch.android.pielauncher.content;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collection;
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
		public final boolean pinned;

		public Folder(
				long id,
				String name,
				boolean hideContents,
				boolean pinned) {
			this.id = id;
			this.name = name;
			this.hideContents = hideContents;
			this.pinned = pinned;
		}
	}

	// Synthetic package for the ComponentName of folders so they can be
	// ordinary AppIcons in the drawer list without ever colliding with a
	// real package.
	public static final String FOLDER_PACKAGE =
			"de.markusfisch.android.pielauncher.folder";

	private final List<Folder> folders = new ArrayList<>();
	private final Map<Long, Set<LauncherItemKey>> items = new HashMap<>();
	// Where items were put by hand, by folder; missing for the rest.
	private final Map<Long, Map<LauncherItemKey, Integer>> positions =
			new HashMap<>();
	private final List<Apps.AppIcon> icons = new ArrayList<>();

	private Bitmap bitmap;
	// Not synchronized in invalidate() so storages can flag a change
	// without taking this lock while this class is taking theirs.
	private volatile boolean restored = false;

	public static boolean isFolder(Apps.AppIcon icon) {
		return icon != null && isFolder(icon.componentName);
	}

	public static boolean isFolder(LauncherItemKey key) {
		return key != null && isFolder(key.componentName);
	}

	public static boolean isFolder(ComponentName componentName) {
		return componentName != null &&
				FOLDER_PACKAGE.equals(componentName.getPackageName());
	}

	public static long idOf(Apps.AppIcon icon) {
		return parseId(icon.componentName);
	}

	public static long idOf(LauncherItemKey key) {
		return parseId(key.componentName);
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

	public synchronized boolean isPinned(long id) {
		Folder folder = getFolder(id);
		return folder != null && folder.pinned;
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

	// The icon of the hidden folder each app is in, for badging search
	// results. An app in more than one shows the first folder's.
	public synchronized Map<LauncherItemKey, Bitmap> getHiddenItemBadges() {
		Map<LauncherItemKey, Bitmap> badges = new HashMap<>();
		for (Folder folder : folders) {
			Set<LauncherItemKey> keys = items.get(folder.id);
			Apps.AppIcon icon = getIcon(folder.id);
			if (!folder.hideContents || keys == null || icon == null) {
				continue;
			}
			for (LauncherItemKey key : keys) {
				if (!badges.containsKey(key)) {
					badges.put(key, icon.bitmap);
				}
			}
		}
		return badges;
	}

	// The folder's apps in the order they were arranged in, with the ones
	// never moved by hand after them by name. Apps that are not indexed,
	// like hidden ones, are left out.
	public synchronized List<Apps.AppIcon> getOrderedApps(
			long id,
			Map<LauncherItemKey, Apps.AppIcon> apps) {
		Map<LauncherItemKey, Integer> order = positions.get(id);
		List<Apps.AppIcon> placed = new ArrayList<>();
		List<Apps.AppIcon> rest = new ArrayList<>();
		Map<Apps.AppIcon, Integer> at = new HashMap<>();
		for (LauncherItemKey key : getItems(id)) {
			Apps.AppIcon icon = apps.get(key);
			if (icon == null) {
				continue;
			}
			Integer position = order != null ? order.get(key) : null;
			if (position != null) {
				at.put(icon, position);
				placed.add(icon);
			} else {
				rest.add(icon);
			}
		}
		Collections.sort(placed, (left, right) ->
				at.get(left) - at.get(right));
		Collections.sort(rest, (left, right) ->
				left.label.compareToIgnoreCase(right.label));
		placed.addAll(rest);
		return placed;
	}

	public synchronized void setOrder(
			Context context,
			long id,
			List<LauncherItemKey> keys) {
		PieLauncherApp.getDatabase(context).setFolderOrder(context, id, keys);
		reload(context);
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
			boolean hideContents,
			boolean pinned) {
		PieLauncherApp.getDatabase(context).updateFolder(id, name,
				hideContents, pinned);
		reload(context);
	}

	public synchronized void delete(Context context, long id) {
		// Drop the tags and the custom icon too, or they would linger in
		// the database and in every settings export made afterwards.
		LauncherItemKey key = keyOf(id);
		PieLauncherApp.appTags.store(context, key, null);
		PieLauncherApp.appIcons.store(context, key, null);
		PieLauncherApp.workPausedIcons.store(context, key, null);
		if (PieLauncherApp.iconPack.hasMapping(key.componentName)) {
			PieLauncherApp.iconPack.removeMapping(key.componentName);
			PieLauncherApp.iconPack.storeMappings(context);
		}
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

	public synchronized void addAll(
			Context context,
			long id,
			Collection<LauncherItemKey> keys) {
		Database database = PieLauncherApp.getDatabase(context);
		for (LauncherItemKey key : keys) {
			database.addToFolder(context, id, key);
		}
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
		PieLauncherApp.workPausedIcons.restore(context);
		if (bitmap == null) {
			// One bitmap for all folders; the drawer draws hundreds of
			// icons per frame and does not need one more per folder.
			bitmap = Converter.getBitmapFromDrawable(
					context.getResources(), R.drawable.ic_folder);
		}
		PieLauncherApp.getDatabase(context).restoreFolders(context, folders,
				items, positions);
		icons.clear();
		for (Folder folder : folders) {
			LauncherItemKey key = keyOf(folder.id);
			icons.add(new Apps.AppIcon(
					key.componentName,
					folder.name,
					iconFor(key),
					null));
		}
		restored = true;
	}

	// What a folder shows: a picked image first, then the icon pack icon
	// mapped to it, then the built-in folder symbol. Must be called from
	// reload() only, which makes sure the fallback is there.
	private Bitmap iconFor(LauncherItemKey key) {
		Bitmap custom = Apps.getCustomIcon(key);
		if (custom != null) {
			return custom;
		}
		Drawable mapped = PieLauncherApp.iconPack.getMappedIcon(
				key.componentName);
		return mapped != null
				? Converter.getBitmapFromDrawable(mapped)
				: bitmap;
	}
}
