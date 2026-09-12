package de.markusfisch.android.pielauncher.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.adapter.CustomisedAppsAdapter;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.Apps;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;
import de.markusfisch.android.pielauncher.graphics.BackgroundBlur;
import de.markusfisch.android.pielauncher.graphics.ToolbarBackground;
import de.markusfisch.android.pielauncher.view.SystemBars;
import de.markusfisch.android.pielauncher.widget.OptionsDialog;

public class CustomisedAppsActivity extends Activity {
	private ToolbarBackground toolbarBackground;
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
		findViewById(R.id.progress).setVisibility(View.GONE);

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

	private void loadCustomisedApps() {
		PieLauncherApp.appLabels.restore(this);
		PieLauncherApp.appTags.restore(this);
		PieLauncherApp.appIcons.restore(this);

		Set<LauncherItemKey> keys = new HashSet<>();
		keys.addAll(PieLauncherApp.appLabels.keys());
		keys.addAll(PieLauncherApp.appTags.keys());
		keys.addAll(PieLauncherApp.appIcons.keys());

		ArrayList<CustomisedAppsAdapter.CustomisedApp> apps = new ArrayList<>();
		for (LauncherItemKey key : keys) {
			Apps.AppIcon icon = PieLauncherApp.apps.apps.get(key);
			apps.add(new CustomisedAppsAdapter.CustomisedApp(
					key,
					icon != null
							? icon.label
							: key.componentName.getPackageName(),
					icon != null
							? new BitmapDrawable(getResources(), icon.bitmap)
							: null));
		}
		Collections.sort(apps, (left, right) ->
				left.name.compareToIgnoreCase(right.name));

		adapter = new CustomisedAppsAdapter(this, apps);
		listView.setAdapter(adapter);
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
