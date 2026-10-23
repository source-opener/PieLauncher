package de.markusfisch.android.pielauncher.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.AppLauncher;
import de.markusfisch.android.pielauncher.content.Apps;
import de.markusfisch.android.pielauncher.content.Folders;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

// Puts the work profile apps, or the apps of a category, into one folder
// that hides them from the drawer. The category is what an app says it is
// in its own manifest, so apps that don't say are left out.
@SuppressLint("UseRequiresApi")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CategoryFolderDialog {
	private static final int WORK = -2;
	private static final int NONE = -1;
	// The values of ApplicationInfo.CATEGORY_*, with the names given to
	// their folders.
	private static final int[][] CATEGORIES = {
			{0, R.string.category_folder_games},
			{1, R.string.category_folder_audio},
			{2, R.string.category_folder_video},
			{3, R.string.category_folder_images},
			{4, R.string.category_folder_social},
			{5, R.string.category_folder_news},
			{6, R.string.category_folder_maps},
			{7, R.string.category_folder_productivity},
			{8, R.string.category_folder_accessibility},
	};

	public static void show(Context context) {
		Map<Integer, Set<LauncherItemKey>> groups = getGroups(context);
		List<Integer> choices = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (groups.containsKey(WORK)) {
			choices.add(WORK);
			labels.add(labelOf(context, R.string.work_folder_name,
					groups.get(WORK)));
		}
		for (int[] category : CATEGORIES) {
			Set<LauncherItemKey> apps = groups.get(category[0]);
			if (apps != null) {
				choices.add(category[0]);
				labels.add(labelOf(context, category[1], apps));
			}
		}
		if (choices.isEmpty()) {
			toast(context, context.getString(R.string.no_category_apps));
			return;
		}
		Dialog.newDialog(context)
				.setTitle(R.string.category_folders)
				.setItems(labels.toArray(new String[0]), (dialog, which) -> {
					int choice = choices.get(which);
					moveApps(context, context.getString(choice == WORK
							? R.string.work_folder_name
							: nameOf(choice)), groups.get(choice));
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static String labelOf(
			Context context,
			int nameId,
			Set<LauncherItemKey> apps) {
		return context.getString(nameId) + " (" + apps.size() + ")";
	}

	private static int nameOf(int category) {
		for (int[] c : CATEGORIES) {
			if (c[0] == category) {
				return c[1];
			}
		}
		throw new IllegalArgumentException();
	}

	// Work profile apps and the categories of all other apps. Apps in
	// the private space stay out of this, so no folder gives them away.
	private static Map<Integer, Set<LauncherItemKey>> getGroups(
			Context context) {
		Map<Integer, Set<LauncherItemKey>> groups = new HashMap<>();
		UserHandle work = AppLauncher.findWorkProfileUser(context);
		UserHandle privateUser = AppLauncher.findPrivateProfileUser(context);
		for (Map.Entry<LauncherItemKey, Apps.AppIcon> entry :
				PieLauncherApp.apps.apps.entrySet()) {
			LauncherItemKey key = entry.getKey();
			if (Apps.isShortcut(entry.getValue()) ||
					(privateUser != null &&
							privateUser.equals(key.userHandle))) {
				continue;
			}
			add(groups, work != null && work.equals(key.userHandle)
					? WORK
					: categoryOf(context, key), key);
		}
		return groups;
	}

	private static void add(
			Map<Integer, Set<LauncherItemKey>> groups,
			int group,
			LauncherItemKey key) {
		if (group == NONE) {
			return;
		}
		Set<LauncherItemKey> keys = groups.get(group);
		if (keys == null) {
			keys = new HashSet<>();
			groups.put(group, keys);
		}
		keys.add(key);
	}

	private static int categoryOf(Context context, LauncherItemKey key) {
		String packageName = key.componentName.getPackageName();
		ApplicationInfo info;
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
					key.userHandle != null) {
				LauncherApps la = (LauncherApps) context.getSystemService(
						Context.LAUNCHER_APPS_SERVICE);
				info = la.getApplicationInfo(packageName, 0, key.userHandle);
			} else {
				info = context.getPackageManager().getApplicationInfo(
						packageName, 0);
			}
		} catch (PackageManager.NameNotFoundException e) {
			return NONE;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
				info.category != ApplicationInfo.CATEGORY_UNDEFINED) {
			return info.category;
		}
		// Older apps only had a flag for games.
		@SuppressWarnings("deprecation")
		int isGame = info.flags & ApplicationInfo.FLAG_IS_GAME;
		return isGame != 0 ? CATEGORIES[0][0] : NONE;
	}

	private static void moveApps(
			Context context,
			String name,
			Set<LauncherItemKey> apps) {
		PieLauncherApp.folders.restore(context);
		Folders.Folder folder = findFolder(name);
		if (folder == null) {
			long id = PieLauncherApp.folders.create(context, name);
			if (id > -1) {
				move(context, id, apps);
			}
			return;
		}
		List<String> labels = getLabelsToHide(folder, apps);
		if (labels.isEmpty()) {
			move(context, folder.id, apps);
			return;
		}
		Dialog.newDialog(context)
				.setTitle(context.getString(R.string.work_folder_exists,
						folder.name))
				.setMessage(context.getString(R.string.work_folder_hides,
						TextUtils.join("\n", labels)))
				.setPositiveButton(android.R.string.ok, (dialog, which) ->
						move(context, folder.id, apps))
				.setNeutralButton(R.string.other_folder_name,
						(dialog, which) -> askForName(context, name, apps))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static Folders.Folder findFolder(String name) {
		for (Folders.Folder folder : PieLauncherApp.folders.getFolders()) {
			if (folder.name.trim().equalsIgnoreCase(name.trim())) {
				return folder;
			}
		}
		return null;
	}

	// What leaves the drawer by using this folder: the apps moved and,
	// unless it hides them already, what the folder holds now.
	private static List<String> getLabelsToHide(
			Folders.Folder folder,
			Set<LauncherItemKey> apps) {
		Set<LauncherItemKey> keys = new HashSet<>(apps);
		if (!folder.hideContents) {
			keys.addAll(PieLauncherApp.folders.getItems(folder.id));
		}
		keys.removeAll(PieLauncherApp.folders.getHiddenItems());
		List<String> labels = new ArrayList<>();
		for (LauncherItemKey key : keys) {
			Apps.AppIcon icon = PieLauncherApp.apps.apps.get(key);
			if (icon != null) {
				labels.add(icon.label);
			}
		}
		Collections.sort(labels, String.CASE_INSENSITIVE_ORDER);
		return labels;
	}

	private static void move(
			Context context,
			long id,
			Set<LauncherItemKey> apps) {
		PieLauncherApp.folders.addAll(context, id, apps);
		Folders.Folder folder = PieLauncherApp.folders.getFolder(id);
		if (folder == null) {
			return;
		}
		if (!folder.hideContents) {
			PieLauncherApp.folders.update(context, id, folder.name, true,
					folder.pinned);
		}
		PieLauncherApp.apps.propagateUpdate();
		toast(context, context.getString(R.string.apps_moved,
				folder.name));
	}

	private static void askForName(
			Context context,
			String name,
			Set<LauncherItemKey> apps) {
		View view = LayoutInflater.from(context).inflate(
				R.layout.dialog_input, null);
		EditText input = view.findViewById(R.id.input);
		input.setHint(R.string.folder_name);
		input.setText(name);
		Dialog.newDialog(context)
				.setTitle(R.string.folder_name)
				.setView(view)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					String s = input.getText().toString().trim();
					if (!s.isEmpty()) {
						moveApps(context, s, apps);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void toast(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
	}
}
