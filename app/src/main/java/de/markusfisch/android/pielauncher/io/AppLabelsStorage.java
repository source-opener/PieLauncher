package de.markusfisch.android.pielauncher.io;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

public class AppLabelsStorage {
	private final Map<LauncherItemKey, String> labels = new HashMap<>();

	private boolean restored = false;

	public synchronized String get(LauncherItemKey key) {
		return labels.get(key);
	}

	public synchronized void store(
			Context context,
			LauncherItemKey key,
			String label) {
		if (label == null) {
			labels.remove(key);
		} else {
			labels.put(key, label);
		}
		PieLauncherApp.getDatabase(context).storeAppLabel(context, key, label);
	}

	public synchronized Set<LauncherItemKey> keys() {
		return new HashSet<>(labels.keySet());
	}

	public synchronized void restore(Context context) {
		if (restored) {
			return;
		}
		PieLauncherApp.getDatabase(context).restoreAppLabels(context, labels);
		restored = true;
	}
}
