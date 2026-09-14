package de.markusfisch.android.pielauncher.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.adapter.CustomisedAppsAdapter;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.Folders;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;
import de.markusfisch.android.pielauncher.graphics.BackgroundBlur;
import de.markusfisch.android.pielauncher.graphics.Converter;
import de.markusfisch.android.pielauncher.graphics.ToolbarBackground;
import de.markusfisch.android.pielauncher.view.SystemBars;
import de.markusfisch.android.pielauncher.widget.OptionsDialog;

public class CustomisedAppsActivity extends Activity {
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor =
			Executors.newSingleThreadExecutor();

	private ToolbarBackground toolbarBackground;
	private View progressView;
	private ListView listView;
	private CustomisedAppsAdapter adapter;

	public static void start(Context context) {
		context.startActivity(new Intent(context,
				CustomisedAppsActivity.class));
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_customised_apps);

		BackgroundBlur.setBlurRadius(getWindow(),
				PieLauncherApp.getPrefs(this).getBackgroundBlurRadius());
		toolbarBackground = new ToolbarBackground(getResources());
		View toolbar = findViewById(R.id.toolbar);
		toolbar.setOnClickListener(v -> finish());
		progressView = findViewById(R.id.progress);
		progressView.setVisibility(View.GONE);

		listView = findViewById(R.id.apps);
		listView.setEmptyView(findViewById(R.id.no_customised_apps));
		listView.setOnItemClickListener((parent, view, position, id) -> {
			CustomisedAppsAdapter.CustomisedApp app = adapter.getItem(position);
			if (app != null) {
				showResetOptions(app);
			}
		});

		Window window = getWindow();
		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				if (visibleItemCount < 1) {
					return;
				}
				int y = 0xffff;
				if (firstVisibleItem == 0) {
					View child = view.getChildAt(firstVisibleItem);
					y = child.getTop() - view.getPaddingTop();
				}
				toolbar.setBackgroundColor(toolbarBackground.getColorForY(y));
			}
		});
		SystemBars.addPaddingFromWindowInsets(toolbar, listView);
		SystemBars.setTransparentSystemBars(window);
		SystemBars.setNavigationBarColor(window,
				toolbarBackground.backgroundColor);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadCustomisedApps();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		executor.shutdownNow();
	}

	private void loadCustomisedApps() {
		PieLauncherApp.appLabels.restore(this);
		PieLauncherApp.appTags.restore(this);
		PieLauncherApp.appIcons.restore(this);
		PieLauncherApp.folders.restore(this);

		Set<LauncherItemKey> keys = new HashSet<>();
		keys.addAll(PieLauncherApp.appLabels.keys());
		keys.addAll(PieLauncherApp.appTags.keys());
		keys.addAll(PieLauncherApp.appIcons.keys());

		progressView.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			ArrayList<CustomisedAppsAdapter.CustomisedApp> apps =
					new ArrayList<>();
			for (LauncherItemKey key : keys) {
				if (Folders.isFolder(key)) {
					CustomisedAppsAdapter.CustomisedApp folder =
							customisedFolder(key);
					if (folder != null) {
						apps.add(folder);
					}
					continue;
				}
				// Read the system name and icon rather than the indexed
				// ones, which still hold the customisation being reset
				// until the background index catches up.
				Pair<String, Drawable> system = getSystemNameAndIcon(this, key);
				String custom = PieLauncherApp.appLabels.get(key);
				Bitmap bitmap = PieLauncherApp.appIcons.get(key);
				String name = custom != null
						? custom
						: system != null
								? system.first
								: key.componentName.getPackageName();
				Drawable icon = bitmap != null
						? new BitmapDrawable(getResources(), bitmap)
						: system != null ? system.second : null;
				apps.add(new CustomisedAppsAdapter.CustomisedApp(
						key, name, PieLauncherApp.appTags.get(key), icon));
			}
			Collections.sort(apps, (left, right) ->
					left.name.compareToIgnoreCase(right.name));
			handler.post(() -> {
				progressView.setVisibility(View.GONE);
				adapter = new CustomisedAppsAdapter(this, apps);
				listView.setAdapter(adapter);
			});
		});
	}

	// A folder keeps its name in its own table and has no system name or
	// icon to fall back on, so resolve both here. Returns null for a
	// customisation left behind by a folder that no longer exists.
	private CustomisedAppsAdapter.CustomisedApp customisedFolder(
			LauncherItemKey key) {
		Folders.Folder folder = PieLauncherApp.folders.getFolder(
				Folders.idOf(key));
		if (folder == null) {
			return null;
		}
		Bitmap bitmap = PieLauncherApp.appIcons.get(key);
		return new CustomisedAppsAdapter.CustomisedApp(
				key,
				folder.name,
				PieLauncherApp.appTags.get(key),
				bitmap != null
						? new BitmapDrawable(getResources(), bitmap)
						: Converter.getDrawable(getResources(),
								R.drawable.ic_folder));
	}

	private static Pair<String, Drawable> getSystemNameAndIcon(
			Context context,
			LauncherItemKey key) {
		ComponentName componentName = key.componentName;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return getSystemNameAndIconFromLauncherApps(context, key);
		}
		PackageManager pm = context.getPackageManager();
		Intent intent = new Intent(Intent.ACTION_MAIN, null);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setPackage(componentName.getPackageName());
		for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
			return new Pair<>(info.loadLabel(pm).toString(), info.loadIcon(pm));
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static Pair<String, Drawable> getSystemNameAndIconFromLauncherApps(
			Context context,
			LauncherItemKey key) {
		ComponentName componentName = key.componentName;
		LauncherApps la = (LauncherApps) context.getSystemService(
				Context.LAUNCHER_APPS_SERVICE);
		UserHandle user = key.userHandle != null
				? key.userHandle
				: Process.myUserHandle();
		try {
			for (LauncherActivityInfo info : la.getActivityList(
					componentName.getPackageName(), user)) {
				if (componentName.equals(info.getComponentName())) {
					return new Pair<>(info.getLabel().toString(),
							info.getIcon(0));
				}
			}
		} catch (SecurityException | IllegalStateException e) {
			// The package or profile is gone.
		}
		return null;
	}

	private void showResetOptions(CustomisedAppsAdapter.CustomisedApp app) {
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		if (PieLauncherApp.appLabels.get(app.key) != null) {
			labels.add(getString(R.string.reset_name));
			actions.add(() -> PieLauncherApp.appLabels.store(this, app.key,
					null));
		}
		if (!PieLauncherApp.appTags.get(app.key).isEmpty()) {
			labels.add(getString(R.string.reset_tags));
			actions.add(() -> PieLauncherApp.appTags.store(this, app.key,
					null));
		}
		if (PieLauncherApp.appIcons.has(app.key)) {
			labels.add(getString(R.string.reset_icon));
			actions.add(() -> PieLauncherApp.appIcons.store(this, app.key,
					null));
		}
		labels.add(getString(R.string.reset_all));
		actions.add(() -> {
			PieLauncherApp.appLabels.store(this, app.key, null);
			PieLauncherApp.appTags.store(this, app.key, null);
			PieLauncherApp.appIcons.store(this, app.key, null);
		});
		OptionsDialog.show(this, R.string.customised_apps,
				labels.toArray(new CharSequence[0]),
				(view, which) -> {
					actions.get(which).run();
					PieLauncherApp.apps.indexAppsAsync(this);
					loadCustomisedApps();
				});
	}
}
