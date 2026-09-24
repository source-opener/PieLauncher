package de.markusfisch.android.pielauncher.content;

import android.content.Context;
import android.os.UserHandle;

import java.text.Normalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.Apps.AppIcon;
import de.markusfisch.android.pielauncher.preference.Preferences;

public class AppSearch {
	public static final Comparator<AppIcon> appLabelComparator = (left, right) -> {
		// Fast enough to do it for every comparison.
		// Otherwise, if defaultLocale was a permanent field outside
		// this scope, we'd need to listen for configuration changes
		// because the locale may change.
		Locale defaultLocale = Locale.getDefault();
		// compareToIgnoreCase() does not take locale into account.
		int result = left.label.toLowerCase(defaultLocale).compareTo(
				right.label.toLowerCase(defaultLocale));
		return result == 0 && left.userHandle != null && right.userHandle != null
				? left.userHandle.hashCode() - right.userHandle.hashCode()
				: result;
	};

	public static String getSubject(
			int item,
			AppIcon appIcon,
			Locale defaultLocale) {
		if (item == Preferences.SEARCH_PARAMETER_PACKAGE_NAME) {
			return appIcon.componentName.getPackageName()
					.toLowerCase(defaultLocale);
		}
		return fold(appIcon.label.toLowerCase(defaultLocale));
	}

	// Strips accents so that "cafe" finds "Café". The ASCII check keeps
	// this off the hot path for most labels.
	public static String fold(String s) {
		for (int i = 0, len = s.length(); i < len; ++i) {
			if (s.charAt(i) > 0x7f) {
				return Normalizer.normalize(s, Normalizer.Form.NFD)
						.replaceAll("\\p{Mn}+", "");
			}
		}
		return s;
	}

	public static int hammingDistance(String a, String b, int l) {
		int count = 0;
		for (int i = 0; i < l; ++i) {
			if (a.charAt(i) != b.charAt(i)) {
				++count;
			}
		}
		return count;
	}

	public static class HammingHit {
		public final int distance;
		public final AppIcon appIcon;

		HammingHit(int distance, AppIcon appIcon) {
			this.distance = distance;
			this.appIcon = appIcon;
		}
	}

	public static List<AppIcon> filterAppsBy(
			Apps repo,
			Context context,
			String query) {
		Preferences prefs = PieLauncherApp.getPrefs(context);
		return filterAppsBy(repo, context, query, prefs, prefs.getAppSorting(),
				null, -1L);
	}

	// Like filterAppsBy() but with folders: pinned folders matching the
	// query are listed first, the others are sorted in with the apps,
	// unless folderId names an open folder, in which case the result is
	// restricted to that folder's items.
	public static List<AppIcon> filterDrawerBy(
			Apps repo,
			Context context,
			String query,
			long folderId) {
		Preferences prefs = PieLauncherApp.getPrefs(context);
		PieLauncherApp.folders.restore(context);
		return filterAppsBy(repo, context, query, prefs, prefs.getAppSorting(),
				PieLauncherApp.folders, folderId);
	}

	public static List<AppIcon> filterAppsByFrecency(
			Apps repo,
			Context context) {
		Preferences prefs = PieLauncherApp.getPrefs(context);
		return filterAppsBy(repo, context, null, prefs,
				Preferences.APP_SORT_FRECENCY, null, -1L);
	}

	private static List<AppIcon> filterAppsBy(
			Apps repo,
			Context context,
			String query,
			Preferences prefs,
			int appSorting,
			Folders folders,
			long folderId) {
		if (repo.isIndexing()) {
			return null;
		}

		UserHandle privateUser = AppLauncher.findPrivateProfileUser(context);
		boolean privateOnly = false;

		query = query == null ? "" : query.trim();
		if (privateUser != null && query.startsWith(".")) {
			query = query.substring(1);
			if (AppLauncher.isPrivateProfileLocked(context, privateUser)) {
				if (AppLauncher.unlockPrivateProfile(context, () -> {
					Apps.UpdateListener listener = repo.getUpdateListener();
					if (listener != null) {
						listener.onShowAllAppsOnResume();
					}
				})) {
					return null;
				}
			} else {
				privateOnly = true;
			}
		}

		Locale defaultLocale = Locale.getDefault();
		query = fold(query.toLowerCase(defaultLocale));

		int strategy = prefs.getSearchStrictness();
		Comparator<AppIcon> appComparator = getAppComparator(
				appSorting, System.currentTimeMillis());
		ArrayList<AppIcon> list = new ArrayList<>();
		ArrayList<AppSearch.HammingHit> hamming = new ArrayList<>();

		boolean inFolder = folders != null && folderId > -1;
		Set<LauncherItemKey> only = inFolder
				? folders.getItems(folderId)
				: null;
		Set<LauncherItemKey> hidden = folders != null && !inFolder
				? folders.getHiddenItems()
				: Collections.<LauncherItemKey>emptySet();

		if (query.isEmpty()) {
			for (Map.Entry<LauncherItemKey, AppIcon> entry : repo.apps.entrySet()) {
				AppIcon appIcon = entry.getValue();
				if (inProfile(appIcon.userHandle, privateUser, privateOnly) &&
						inScope(entry.getKey(), only, hidden)) {
					list.add(appIcon);
				}
			}
			switch (prefs.excludePie()) {
				case Preferences.EXCLUDE_PIE_ALL:
					list.removeAll(new HashSet<>(repo.menuSecondary));
					// Fall through.
				case Preferences.EXCLUDE_PIE_PRIMARY:
					list.removeAll(new HashSet<>(repo.menuPrimary));
					break;
			}
		} else {
			int item = prefs.getSearchParameter();
			for (Map.Entry<LauncherItemKey, AppIcon> entry : repo.apps.entrySet()) {
				AppIcon appIcon = entry.getValue();
				if (!inProfile(appIcon.userHandle, privateUser, privateOnly) ||
						!inScope(entry.getKey(), only, hidden)) {
					continue;
				}
				String subject = AppSearch.getSubject(item, appIcon, defaultLocale);
				boolean add = false;
				switch (strategy) {
					// HAMMING includes CONTAINS for historical reasons.
					case Preferences.SEARCH_STRICTNESS_HAMMING:
					case Preferences.SEARCH_STRICTNESS_CONTAINS:
						add = subject.contains(query);
						break;
					case Preferences.SEARCH_STRICTNESS_STARTS_WITH:
						add = subject.startsWith(query);
						break;
				}
				if (!add) {
					add = hasTag(entry.getKey(), query, strategy);
				}
				if (add) {
					list.add(appIcon);
				} else {
					int min = Math.min(subject.length(), query.length());
					int distance = AppSearch.hammingDistance(
							subject, query, min);
					if (distance <= min >> 1) {
						hamming.add(new AppSearch.HammingHit(
								distance, appIcon));
					}
				}
			}
		}

		boolean noAppMatches = list.isEmpty();
		List<AppIcon> pinnedFolders = new ArrayList<>();
		if (folders != null && !inFolder) {
			for (AppIcon icon : folders.match(query, strategy,
					defaultLocale)) {
				if (folders.isPinned(Folders.idOf(icon))) {
					pinnedFolders.add(icon);
				} else {
					list.add(icon);
				}
			}
		}

		Collections.sort(list, appComparator);
		if (!hamming.isEmpty() && (noAppMatches ||
				strategy == Preferences.SEARCH_STRICTNESS_HAMMING)) {
			// Only append hamming matches as they're less likely
			// as good as exact matches.
			Collections.sort(hamming, (left, right) -> {
				int result = left.distance - right.distance;
				return result != 0
						? result
						: appComparator.compare(left.appIcon, right.appIcon);
			});
			for (AppSearch.HammingHit hit : hamming) {
				list.add(hit.appIcon);
			}
		}
		Collections.sort(pinnedFolders, appLabelComparator);
		list.addAll(0, pinnedFolders);
		return list;
	}

	private static boolean inScope(
			LauncherItemKey key,
			Set<LauncherItemKey> only,
			Set<LauncherItemKey> hidden) {
		return only != null ? only.contains(key) : !hidden.contains(key);
	}

	static Comparator<AppIcon> getAppComparator(int sorting, long now) {
		if (sorting != Preferences.APP_SORT_FRECENCY) {
			return appLabelComparator;
		}
		return (left, right) -> {
			int result = Frecency.compare(
					left.frecencyScore,
					left.frecencyUpdatedAt,
					right.frecencyScore,
					right.frecencyUpdatedAt,
					now);
			return result != 0
					? result
					: appLabelComparator.compare(left, right);
		};
	}

	static boolean matches(String subject, String query, int strategy) {
		return strategy == Preferences.SEARCH_STRICTNESS_STARTS_WITH
				? subject.startsWith(query)
				: subject.contains(query);
	}

	static boolean hasTag(
			LauncherItemKey key,
			String query,
			int strategy) {
		for (String tag : PieLauncherApp.appTags.split(key)) {
			if (matches(fold(tag), query, strategy)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inProfile(
			UserHandle appProfile,
			UserHandle privateUser,
			boolean privateOnly) {
		return privateUser == null ||
				privateUser.equals(appProfile) == privateOnly;
	}
}
