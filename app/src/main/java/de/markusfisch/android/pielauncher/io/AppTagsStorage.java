package de.markusfisch.android.pielauncher.io;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

public class AppTagsStorage {
	private static final String[] NONE = {};
	private static final String SEPARATOR = " ";

	private final Map<LauncherItemKey, String> tags = new HashMap<>();

	private boolean restored = false;

	public synchronized String get(LauncherItemKey key) {
		String value = tags.get(key);
		return value != null ? value : "";
	}

	public synchronized String[] split(LauncherItemKey key) {
		String value = tags.get(key);
		return value == null
				? NONE
				: value.toLowerCase(Locale.getDefault()).split(SEPARATOR);
	}

	public synchronized void store(
			Context context,
			LauncherItemKey key,
			String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			tags.remove(key);
		} else {
			tags.put(key, normalized);
		}
		PieLauncherApp.getDatabase(context).storeAppTags(context, key,
				normalized);
	}

	public synchronized void restore(Context context) {
		if (restored) {
			return;
		}
		PieLauncherApp.getDatabase(context).restoreAppTags(context, tags);
		restored = true;
	}

	public synchronized void invalidate() {
		restored = false;
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (String tag : value.split("[\\s,;]+")) {
			if (!tag.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(SEPARATOR);
				}
				sb.append(tag);
			}
		}
		return sb.length() > 0 ? sb.toString() : null;
	}
}
