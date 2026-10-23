package de.markusfisch.android.pielauncher.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
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

// Puts all work profile apps into one folder that hides them from the
// drawer.
@SuppressLint("UseRequiresApi")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class WorkFolderDialog {
	public static void show(Context context) {
		moveWorkApps(context, context.getString(R.string.work_folder_name));
	}

	private static void moveWorkApps(Context context, String name) {
		Set<LauncherItemKey> workApps = getWorkApps(context);
		if (workApps.isEmpty()) {
			toast(context, context.getString(R.string.no_work_apps));
			return;
		}
		PieLauncherApp.folders.restore(context);
		Folders.Folder folder = findFolder(name);
		if (folder == null) {
			long id = PieLauncherApp.folders.create(context, name);
			if (id > -1) {
				move(context, id, workApps);
			}
			return;
		}
		List<String> labels = getLabelsToHide(folder, workApps);
		if (labels.isEmpty()) {
			move(context, folder.id, workApps);
			return;
		}
		Dialog.newDialog(context)
				.setTitle(context.getString(R.string.work_folder_exists,
						folder.name))
				.setMessage(context.getString(R.string.work_folder_hides,
						TextUtils.join("\n", labels)))
				.setPositiveButton(android.R.string.ok, (dialog, which) ->
						move(context, folder.id, workApps))
				.setNeutralButton(R.string.other_folder_name,
						(dialog, which) -> askForName(context, name))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static Set<LauncherItemKey> getWorkApps(Context context) {
		Set<LauncherItemKey> keys = new HashSet<>();
		UserHandle work = AppLauncher.findWorkProfileUser(context);
		if (work == null) {
			return keys;
		}
		for (Map.Entry<LauncherItemKey, Apps.AppIcon> entry :
				PieLauncherApp.apps.apps.entrySet()) {
			if (work.equals(entry.getKey().userHandle) &&
					!Apps.isShortcut(entry.getValue())) {
				keys.add(entry.getKey());
			}
		}
		return keys;
	}

	private static Folders.Folder findFolder(String name) {
		for (Folders.Folder folder : PieLauncherApp.folders.getFolders()) {
			if (folder.name.trim().equalsIgnoreCase(name.trim())) {
				return folder;
			}
		}
		return null;
	}

	// What leaves the drawer by using this folder: the work apps and,
	// unless it hides them already, what the folder holds now.
	private static List<String> getLabelsToHide(
			Folders.Folder folder,
			Set<LauncherItemKey> workApps) {
		Set<LauncherItemKey> keys = new HashSet<>(workApps);
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
			Set<LauncherItemKey> workApps) {
		PieLauncherApp.folders.addAll(context, id, workApps);
		Folders.Folder folder = PieLauncherApp.folders.getFolder(id);
		if (folder == null) {
			return;
		}
		if (!folder.hideContents) {
			PieLauncherApp.folders.update(context, id, folder.name, true,
					folder.pinned);
		}
		PieLauncherApp.apps.propagateUpdate();
		toast(context, context.getString(R.string.work_apps_moved,
				folder.name));
	}

	private static void askForName(Context context, String name) {
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
						moveWorkApps(context, s);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void toast(Context context, String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
	}
}
