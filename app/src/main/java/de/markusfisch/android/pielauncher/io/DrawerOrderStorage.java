package de.markusfisch.android.pielauncher.io;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

// Where apps were put in the drawer by hand, for sorting "By hand".
public class DrawerOrderStorage {
	private final Map<LauncherItemKey, Integer> positions = new HashMap<>();

	private boolean restored = false;

	// -1 for an app that was never put anywhere.
	public synchronized int positionOf(LauncherItemKey key) {
		Integer position = positions.get(key);
		return position != null ? position : -1;
	}

	// Replaces the whole order, or forgets it for null.
	public synchronized void store(
			Context context,
			List<LauncherItemKey> keys) {
		positions.clear();
		for (int i = 0, size = keys == null ? 0 : keys.size();
				i < size; ++i) {
			positions.put(keys.get(i), i);
		}
		PieLauncherApp.getDatabase(context).storeDrawerOrder(context, keys);
	}

	public synchronized void restore(Context context) {
		if (restored) {
			return;
		}
		PieLauncherApp.getDatabase(context).restoreDrawerOrder(context,
				positions);
		restored = true;
	}
}
