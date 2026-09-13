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
import de.markusfisch.android.pielauncher.preference.Preferences;

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
	private boolean restored = false;

	public static boolean isFolder(Apps.AppIcon icon) {
		return icon != null && icon.componentName != null &&
				FOLDER_PACKAGE.equals(icon.componentName.getPackageName());
	}

	public static long idOf(Apps.AppIcon icon) {
		try {
			return Long.parseLong(icon.componentName.getClassName());
		} catch (NumberFormatException e) {
			return -1L;
		}
	}

	public synchronized List<Folder> getFolders() {
		return new ArrayList<>(folders);
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
			if (strategy == Preferences.SEARCH_STRICTNESS_STARTS_WITH
					? name.startsWith(query)
					: name.contains(query)) {
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

	private synchronized void reload(Context context) {
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
			icons.add(new Apps.AppIcon(
					new ComponentName(FOLDER_PACKAGE,
							String.valueOf(folder.id)),
					folder.name,
					bitmap,
					null));
		}
		restored = true;
	}
}
