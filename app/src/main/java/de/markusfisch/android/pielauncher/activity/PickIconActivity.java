package de.markusfisch.android.pielauncher.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.adapter.PickIconAdapter;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.AppSearch;
import de.markusfisch.android.pielauncher.content.Folders;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;
import de.markusfisch.android.pielauncher.graphics.Converter;
import de.markusfisch.android.pielauncher.graphics.BackgroundBlur;
import de.markusfisch.android.pielauncher.graphics.IconPack;
import de.markusfisch.android.pielauncher.graphics.ToolbarBackground;
import de.markusfisch.android.pielauncher.view.SoftKeyboard;
import de.markusfisch.android.pielauncher.view.SystemBars;
import de.markusfisch.android.pielauncher.widget.Dialog;
import de.markusfisch.android.pielauncher.widget.OptionsDialog;

public class PickIconActivity extends Activity {
	public interface OnHideListener {
		void onHide();
	}

	private static final String COMPONENT_NAME = "package_name";
	private static final String WORK_PAUSED_KEY = "work_paused_key";

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor =
			Executors.newSingleThreadExecutor();

	private SoftKeyboard kb;
	private ToolbarBackground toolbarBackground;
	private View progressView;
	private String iconPackPackageName;
	private GridView gridView;
	private EditText searchInput;
	private ArrayList<String> drawableNames;
	private ArrayList<String> searchNames;
	private PickIconAdapter iconAdapter;
	// Set when picking the icon shown while the work profile is paused,
	// which is stored as an image rather than as a mapping.
	private LauncherItemKey workPausedKey;

	public static void start(Context context, ComponentName componentName) {
		Intent intent = new Intent(context, PickIconActivity.class);
		intent.putExtra(COMPONENT_NAME, componentName);
		context.startActivity(intent);
	}

	public static void startForWorkPaused(
			Context context,
			LauncherItemKey key) {
		Intent intent = new Intent(context, PickIconActivity.class);
		intent.putExtra(COMPONENT_NAME, key.componentName);
		intent.putExtra(WORK_PAUSED_KEY, LauncherItemKey.flattenToString(
				context, key.componentName, key.userHandle));
		context.startActivity(intent);
	}

	public static void askToHide(Context context, ComponentName componentName) {
		askToHide(context, componentName, null);
	}

	public static void askToHide(
			Context context,
			ComponentName componentName,
			OnHideListener hideListener) {
		Dialog.newDialog(context)
				.setTitle(R.string.hide_app)
				.setMessage(R.string.want_to_hide_app)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					PieLauncherApp.apps.hiddenAppsStorage.addAndStore(context,
							componentName);
					PieLauncherApp.apps.updateIconsAsync(context);
					if (hideListener != null) {
						hideListener.onHide();
					}
				})
				.setNegativeButton(android.R.string.cancel, (d, w) -> {
				})
				.show();
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		Intent intent = getIntent();
		ComponentName componentName;
		if (intent == null || (componentName =
				getComponentNameFromIntent(intent)) == null) {
			finish();
			return;
		}

		String itemKey = intent.getStringExtra(WORK_PAUSED_KEY);
		if (itemKey != null) {
			workPausedKey = LauncherItemKey.unflattenFromString(this,
					itemKey);
		}

		setContentView(R.layout.activity_pick_icon);

		BackgroundBlur.setBlurRadius(getWindow(),
				PieLauncherApp.getPrefs(this).getBackgroundBlurRadius());
		kb = new SoftKeyboard(this);
		toolbarBackground = new ToolbarBackground(getResources());
		View toolbar = findViewById(R.id.toolbar);
		progressView = findViewById(R.id.progress);

		iconPackPackageName = PieLauncherApp.iconPack
				.getSelectedIconPackageName();
		if (iconPackPackageName == null) {
			IconPack.Pack pack =
					PieLauncherApp.iconPack.packs.values().iterator().next();
			if (pack != null) {
				iconPackPackageName = pack.packageName;
			}
		}
		if (iconPackPackageName == null) {
			finish();
			return;
		}

		initGridView(componentName);
		initSearch();
		initHide(componentName);
		initReset(componentName);
		initSwitchPack();

		Window window = getWindow();
		gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
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
					if (y != 0) {
						kb.hideFrom(searchInput);
					}
				}
				toolbar.setBackgroundColor(toolbarBackground.getColorForY(y));
			}
		});
		SystemBars.addPaddingFromWindowInsets(toolbar, gridView);
		SystemBars.setTransparentSystemBars(window);
		SystemBars.setNavigationBarColor(window,
				toolbarBackground.backgroundColor);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		executor.shutdownNow();
	}

	private void initGridView(ComponentName componentName) {
		gridView = findViewById(R.id.icons);
		gridView.setOnItemClickListener((parent, view, position, id) -> {
			if (workPausedKey != null) {
				storeWorkPausedIcon(iconAdapter.getItem(position));
				finish();
				return;
			}
			PieLauncherApp.iconPack.addMapping(
					iconPackPackageName,
					componentName,
					iconAdapter.getItem(position));
			PieLauncherApp.iconPack.storeMappings(this);
			PieLauncherApp.apps.updateIconsAsync(this);
			finish();
		});
		loadPack(iconPackPackageName);
	}

	private void storeWorkPausedIcon(String drawableName) {
		IconPack.Pack pack = PieLauncherApp.iconPack.packs.get(
				iconPackPackageName);
		Drawable drawable = pack != null
				? pack.getDrawable(drawableName)
				: null;
		if (drawable == null) {
			return;
		}
		PieLauncherApp.workPausedIcons.store(this, workPausedKey,
				Converter.getBitmapFromDrawable(drawable));
		PieLauncherApp.apps.indexAppsAsync(this);
	}

	private void initSearch() {
		searchInput = findViewById(R.id.search);
		searchInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start,
					int before, int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start,
					int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable e) {
				if (iconAdapter == null) {
					return;
				}
				// Adding matches one by one lays the grid out again for
				// every one of them, which a large pack cannot afford.
				iconAdapter.setNotifyOnChange(false);
				iconAdapter.clear();
				String query = foldForSearch(e.toString());
				for (int i = 0, size = searchNames.size(); i < size; ++i) {
					if (searchNames.get(i).contains(query)) {
						iconAdapter.add(drawableNames.get(i));
					}
				}
				iconAdapter.notifyDataSetChanged();
			}
		});
		searchInput.post(searchInput::requestFocus);
	}

	private void initReset(ComponentName componentName) {
		View resetButton = findViewById(R.id.reset);
		if (workPausedKey == null &&
				PieLauncherApp.iconPack.hasMapping(componentName)) {
			resetButton.setOnClickListener((v) -> {
				askToRestore(componentName);
			});
		} else {
			resetButton.setVisibility(View.INVISIBLE);
		}
	}

	private void askToRestore(ComponentName componentName) {
		Dialog.newDialog(this)
				.setTitle(R.string.change_icon)
				.setMessage(R.string.want_to_restore_icon)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					PieLauncherApp.iconPack.removeMapping(componentName);
					PieLauncherApp.iconPack.storeMappings(this);
					PieLauncherApp.apps.updateIconsAsync(this);
					finish();
				})
				.setNeutralButton(R.string.all, (d, w) -> {
					PieLauncherApp.iconPack.clearMappings();
					PieLauncherApp.iconPack.storeMappings(this);
					PieLauncherApp.apps.updateIconsAsync(this);
					finish();
				})
				.setNegativeButton(android.R.string.cancel, (d, w) -> {
				})
				.show();
	}

	private void initHide(ComponentName componentName) {
		View hideButton = findViewById(R.id.hide_app);
		// Neither the drawer icon nor a folder is an app to hide.
		if (workPausedKey != null ||
				Folders.isFolder(componentName) ||
				PieLauncherApp.apps.isDrawerPackageName(
						componentName.getPackageName())) {
			hideButton.setVisibility(View.INVISIBLE);
		} else {
			hideButton.setOnClickListener((v) -> askToHide(
					this, componentName, this::finish));
		}
	}

	private void initSwitchPack() {
		View switchButton = findViewById(R.id.switch_pack);
		HashMap<String, String> map = PieLauncherApp.iconPack.getIconPacks();
		if (!map.isEmpty()) {
			List<String> packageNames = new ArrayList<>(map.keySet());
			List<String> names = new ArrayList<>(map.values());
			switchButton.setOnClickListener((v) -> {
				OptionsDialog.show(this, R.string.icon_pack,
						names.toArray(new CharSequence[0]),
						(view, which) -> {
							loadPack(packageNames.get(which));
						});
			});
		} else {
			switchButton.setVisibility(View.GONE);
		}
	}

	private void loadPack(String packageName) {
		if (iconPackPackageName == null) {
			return;
		}
		progressView.setVisibility(View.VISIBLE);
		executor.execute(() -> {
			IconPack.Pack pack = PieLauncherApp.iconPack.packs.get(
					packageName);
			if (pack != null) {
				iconPackPackageName = packageName;
				drawableNames = pack.getDrawableNames();
				searchNames = foldForSearch(drawableNames);
			}
			handler.post(() -> {
				progressView.setVisibility(View.GONE);
				if (pack == null) {
					return;
				}
				iconAdapter = new PickIconAdapter(this,
						iconPackPackageName,
						new ArrayList<>(drawableNames));
				gridView.setAdapter(iconAdapter);
				searchInput.getText().clear();
			});
		});
	}

	// A pack names an icon whats_app and shows it as "Whats App", so
	// what someone reads off the pack and types in cannot be matched
	// against the name as it is. Compare both without case, accents or
	// anything between the words. Folding every name once with the pack
	// keeps that off the keystroke.
	private static ArrayList<String> foldForSearch(List<String> names) {
		ArrayList<String> folded = new ArrayList<>(names.size());
		for (int i = 0, size = names.size(); i < size; ++i) {
			folded.add(foldForSearch(names.get(i)));
		}
		return folded;
	}

	private static String foldForSearch(String name) {
		String folded = AppSearch.fold(name);
		StringBuilder sb = new StringBuilder(folded.length());
		for (int i = 0, len = folded.length(); i < len; ++i) {
			char c = folded.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	private static ComponentName getComponentNameFromIntent(Intent intent) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			//noinspection deprecation
			return intent.getParcelableExtra(COMPONENT_NAME);
		} else {
			return intent.getParcelableExtra(COMPONENT_NAME,
					ComponentName.class);
		}
	}
}
