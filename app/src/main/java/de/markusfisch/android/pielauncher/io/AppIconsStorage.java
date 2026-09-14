package de.markusfisch.android.pielauncher.io;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

public class AppIconsStorage {
	private final Map<LauncherItemKey, Bitmap> icons = new HashMap<>();

	private boolean restored = false;

	public synchronized Bitmap get(LauncherItemKey key) {
		return icons.get(key);
	}

	public synchronized boolean has(LauncherItemKey key) {
		return icons.containsKey(key);
	}

	public synchronized void store(
			Context context,
			LauncherItemKey key,
			Bitmap bitmap) {
		if (bitmap == null) {
			icons.remove(key);
		} else {
			icons.put(key, bitmap);
		}
		PieLauncherApp.getDatabase(context).storeAppIcon(context, key, bitmap);
		// Folders bake their icon in when they are read, so make them
		// pick this up whether or not the key belongs to one.
		PieLauncherApp.folders.invalidate();
	}

	public synchronized Set<LauncherItemKey> keys() {
		return new HashSet<>(icons.keySet());
	}

	public synchronized void restore(Context context) {
		if (restored) {
			return;
		}
		PieLauncherApp.getDatabase(context).restoreAppIcons(context, icons);
		restored = true;
	}
}
