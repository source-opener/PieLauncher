package de.markusfisch.android.pielauncher.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.Apps;
import de.markusfisch.android.pielauncher.graphics.ToolbarBackground;
import de.markusfisch.android.pielauncher.preference.Preferences;
import de.markusfisch.android.pielauncher.view.SoftKeyboard;
import de.markusfisch.android.pielauncher.view.SystemBars;
import de.markusfisch.android.pielauncher.widget.AppPieView;
import de.markusfisch.android.pielauncher.widget.FolderPanelView;
import de.markusfisch.android.pielauncher.widget.SearchInput;

public class HomeActivity extends Activity {
	private Preferences prefs;
	private SoftKeyboard kb;
	private GestureDetector gestureDetector;
	private ToolbarBackground toolbarBackground;
	private AppPieView pieView;
	private FolderPanelView folderPanel;
	private View arrangeBar;
	private SearchInput searchInput;
	private ImageView prefsButton;
	private boolean updateAfterTextChange = true;
	private boolean showAllAppsOnResume = false;
	private int immersiveMode = Preferences.IMMERSIVE_MODE_DISABLED;
	private long pausedAt = 0L;
	private OnBackInvokedCallback onBackInvokedCallback = null;

	@SuppressLint("GestureBackNavigation")
	@Override
	public void onBackPressed() {
		handleBack();
	}

	// With the persistent keyboard, the horizontal drawer keeps the
	// keyboard up for as long as the drawer shows.
	private boolean keepsKeyboard() {
		return pieView.inListMode() && prefs.horizontalDrawer() &&
				prefs.persistentKeyboard() && isSearchVisible();
	}

	private void handleBack() {
		if (folderPanel.hide()) {
			return;
		}
		if (pieView.isArranging()) {
			pieView.stopArranging();
			return;
		}
		if (pieView.inEditMode()) {
			pieView.endEditMode();
			showAllApps();
		} else if (pieView.closeFolder()) {
			resetSearch();
		} else {
			hideAllApps();
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (ev == null) {
			return false;
		}
		if (keepsKeyboard() &&
				ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
			// The horizontal drawer is laid out above the keyboard, so
			// bring it back if it was put away somehow.
			kb.showFor(searchInput);
		}
		// The folder panel takes all touches while it shows, including
		// drags that would otherwise pull the drawer down.
		if (pieView.inListMode() && !folderPanel.isShowing() &&
				gestureDetector.onTouchEvent(ev)) {
			return true;
		}
		try {
			return super.dispatchTouchEvent(ev);
		} catch (IllegalStateException e) {
			// Never saw this happen on any of my devices but had two reports
			// from Google so it's probably better to catch and ignore this
			// exception than letting the launcher crash.
			return false;
		}
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		prefs = PieLauncherApp.getPrefs(this);
		kb = new SoftKeyboard(this);
		gestureDetector = new GestureDetector(this, new FlingListener(
				ViewConfiguration.get(this).getScaledMinimumFlingVelocity()));

		setContentView(R.layout.activity_home);
		registerBackCallback();
		if (!PreferencesActivity.isReady(this)) {
			PreferencesActivity.startWelcome(this);
		}

		toolbarBackground = new ToolbarBackground(getResources());
		pieView = findViewById(R.id.pie);
		folderPanel = findViewById(R.id.folder_panel);
		initArrangeBar();
		searchInput = findViewById(R.id.search);
		prefsButton = findViewById(R.id.preferences);

		initPieView();
		initPrefsButton();
		initSearchInput();

		SystemBars.listenForWindowInsets(pieView,
				(left, top, right, bottom) -> {
					pieView.setPadding(
							left,
							// Never set a top padding because the list
							// should appear under the status bar.
							0,
							right,
							bottom);
					// The insets change when the keyboard comes or goes.
					pieView.post(pieView::updateKeyboardOverlap);
				});

		immersiveMode = prefs.getImmersiveMode();
		SystemBars.setTransparentSystemBars(getWindow(), immersiveMode);

		// We may have been started with a request to pin a shortcut, e.g. when
		// a browser adds a PWA to the home screen. No-op for regular launches.
		PieLauncherApp.apps.acceptPinRequest(this, getIntent());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.home, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.preferences) {
			showPreferences();
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		// A shortcut-pin request (e.g. adding a PWA from a browser) arrives
		// here while the launcher is already running. Handle it and return so
		// the home-gesture logic below doesn't act on this unrelated intent.
		if (Apps.isPinItemRequest(intent)) {
			PieLauncherApp.apps.acceptPinRequest(this, intent);
			return;
		}
		// Because this activity has the launch mode "singleTask", it'll get
		// an onNewIntent() when the activity is re-launched.
		if (intent != null &&
				(intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) !=
						Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) {
			if (isGestureNavigationEnabled()) {
				if (Build.VERSION.SDK_INT ==
						Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
					// Deal with the gesture navigation/recent apps bug in
					// Android 14:
					// * make the home gesture twice
					// * enter list of recent apps
					// * now an empty, broken screen is in recent apps
					finish();
				} else {
					resetHomeState();
				}
				return;
			}
			// If this activity is re-launched but _not_ brought to front,
			// the home button was pressed while this activity was on screen.
			if (pieView.inEditMode()) {
				pieView.endEditMode();
			} else if (prefs.showDrawerOnHome() &&
					!isSearchVisible() &&
					// Only show all apps if the activity was recently paused
					// (by pressing the home button) and _not_ if onPause()
					// was triggered by pressing the overview button and
					// *then* the home button.
					System.currentTimeMillis() - pausedAt < 100) {
				// onNewIntent() is always followed by onResume()
				showAllAppsOnResume = true;
			}
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		// On some devices, after unlock, the activity resumes normally but
		// WindowManager never issues a draw command to its Surface. Forcing
		// an invalidate here triggers a redraw when the window regains focus.
		if (hasFocus) {
			getWindow().getDecorView().invalidate();
		}
	}

	@Override
	public void onRestart() {
		super.onRestart();
		if (prefs.forceRelaunch()) {
			finish();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		setRequestedOrientation(prefs.getOrientation());
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateSystemBars();
		if (showAllAppsOnResume) {
			showAllApps();
			showAllAppsOnResume = false;
		} else {
			hideAllApps();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		pausedAt = System.currentTimeMillis();
	}

	@Override
	protected void onDestroy() {
		unregisterBackCallback();
		PieLauncherApp.apps.setUpdateListener(null);
		super.onDestroy();
	}

	private void initArrangeBar() {
		arrangeBar = findViewById(R.id.arrange_bar);
		arrangeBar.findViewById(R.id.arrange_undo).setOnClickListener((v) ->
				pieView.undoArrangement());
		arrangeBar.findViewById(R.id.arrange_reset).setOnClickListener((v) ->
				pieView.resetArrangement());
		arrangeBar.findViewById(R.id.arrange_done).setOnClickListener((v) ->
				pieView.stopArranging());
	}

	private void initPieView() {
		pieView.setWindow(getWindow());
		pieView.setListListener(new AppPieView.ListListener() {
			@Override
			public void onOpenList(boolean resume) {
				showAllAppsOnResume = resume;
				showAllApps();
			}

			@Override
			public void onHideList() {
				hideAllApps();
			}

			@Override
			public void onOpenFolder(Apps.AppIcon folder) {
				folderPanel.show(folder, new FolderPanelView.Listener() {
					@Override
					public void onLaunch(Apps.AppIcon icon) {
						pieView.launchFromFolder(icon);
					}

					@Override
					public void onOptions(Apps.AppIcon icon) {
						pieView.showOptionsFor(icon);
					}
				});
			}

			@Override
			public void onArrange(boolean arranging) {
				// The bar takes the place of the search field, which
				// would only filter what is being arranged.
				if (arranging) {
					hideKeyboadAndPrefsButton();
				}
				searchInput.setVisibility(arranging
						? View.INVISIBLE
						: View.VISIBLE);
				arrangeBar.setVisibility(arranging
						? View.VISIBLE
						: View.GONE);
				setAlpha(arrangeBar.findViewById(R.id.arrange_undo),
						pieView.canUndoArrangement() ? 1f : .4f);
			}

			@Override
			public void onScrollList(int y, boolean isScrolling) {
				if (isScrolling && y != 0) {
					hideKeyboadAndPrefsButton();
				}
				searchInput.setBackgroundColor(
						toolbarBackground.getColorForY(y));
			}

			@Override
			public void onDragDown(float alpha) {
				if (prefs.horizontalDrawer()) {
					hidePrefsButton();
				} else {
					hideKeyboadAndPrefsButton();
				}
				searchInput.setBackgroundColor(0);
				setAlpha(searchInput, alpha);
			}

			@Override
			public void onResetSearch() {
				resetSearch();
			}
		});
		PieLauncherApp.apps.setUpdateListener(new Apps.UpdateListener() {
			@Override
			public void onUpdate() {
				updateAppList(false);
			}

			@Override
			public void onShowAllAppsOnResume() {
				showAllAppsOnResume = true;
			}
		});
	}

	private void initPrefsButton() {
		prefsButton.setOnClickListener(v -> showPreferences());
		prefsButton.setOnLongClickListener(v -> {
			showEditor();
			return true;
		});
	}

	private void initSearchInput() {
		searchInput.setBackListener(new SearchInput.BackListener() {
			@Override
			public boolean keepsKeyboard() {
				return HomeActivity.this.keepsKeyboard();
			}

			@Override
			public void onBack() {
				handleBack();
			}
		});
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
				if (!updateAfterTextChange) {
					return;
				}
				// Typing searches the whole drawer again.
				folderPanel.hide();
				if (e.length() > 0) {
					hidePrefsButton();
				}
				String s = e.toString();
				if (s.equals("..")) {
					e.clear();
					showPreferences();
					return;
				} else if (s.equals(",,")) {
					e.clear();
					showEditor();
					return;
				}
				// Replace ". " before updateAppList().
				if (endsWithDoubleSpace(e)) {
					pieView.launchSelectedAppFromList();
				}
				updateAppList(true);
				// Check icon count after the list was updated.
				if (prefs.autoLaunchMatching() &&
						pieView.getIconCount() == 1) {
					pieView.launchSelectedAppFromList();
				}
			}
		});
		searchInput.setOnEditorActionListener((v, actionId, event) -> {
			switch (actionId) {
				case EditorInfo.IME_ACTION_GO:
				case EditorInfo.IME_ACTION_SEND:
				case EditorInfo.IME_ACTION_DONE:
				case EditorInfo.IME_ACTION_NEXT:
				case EditorInfo.IME_ACTION_SEARCH:
				case EditorInfo.IME_NULL:
					if (!searchInput.getText().toString().isEmpty()) {
						if (pieView.openSelectedFolderFromList()) {
							return true;
						}
						pieView.launchSelectedAppFromList();
					}
					hideAllApps();
					return true;
				default:
					return false;
			}
		});
	}

	private boolean endsWithDoubleSpace(Editable e) {
		boolean doubleSpaceLaunch = prefs.doubleSpaceLaunch();
		String s = e.toString();
		if ((doubleSpaceLaunch && s.endsWith("  ")) ||
				// Some keyboards auto-replace two spaces with ". ",
				// which means a new, different search result.
				s.endsWith(". ")) {
			updateAfterTextChange = false;
			e.clear();
			e.append(s.substring(0, s.length() - 2));
			if (!doubleSpaceLaunch) {
				// Restore the two spaces for moving the selection.
				e.append("  ");
			}
			updateAfterTextChange = true;
			return doubleSpaceLaunch;
		}
		return false;
	}

	private void updateSystemBars() {
		int newImmersiveMode = prefs.getImmersiveMode();
		if (immersiveMode != newImmersiveMode) {
			immersiveMode = newImmersiveMode;
			SystemBars.setSystemUIVisibility(getWindow(), immersiveMode);
		}
	}

	private void showPreferences() {
		// Hide searchInput to avoid inactive InputConnection.
		hideAllApps();
		PreferencesActivity.start(this);
		showAllAppsOnResume = true;
	}

	private void showEditor() {
		hideAllApps();
		pieView.showEditor();
	}

	private void hidePrefsButton() {
		if (prefsButton.getVisibility() == View.VISIBLE) {
			prefsButton.setVisibility(View.GONE);
		}
	}

	private void showAllApps() {
		if (isSearchVisible()) {
			return;
		}

		searchInput.setVisibility(View.VISIBLE);
		prefsButton.setVisibility(View.VISIBLE);
		setAlpha(searchInput, 1f);
		if (prefs.displayKeyboard() || prefs.horizontalDrawer()) {
			kb.showFor(searchInput);
		}

		clearSearchInput();
		updateAppList(true);
		pieView.showList();
	}

	private void clearSearchInput() {
		Editable editable = searchInput.getText();
		updateAfterTextChange = false;
		editable.clear();
		updateAfterTextChange = true;
	}

	private void resetSearch() {
		clearSearchInput();
		updateAppList(true);
	}

	private void setAlpha(View view, float alpha) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			view.setAlpha(alpha);
		}
	}

	private void hideAllApps() {
		folderPanel.hide();
		pieView.stopArranging();
		if (isSearchVisible()) {
			searchInput.setVisibility(View.GONE);
			hideKeyboadAndPrefsButton();
		}
		// Ensure the pie menu is initially hidden because on some devices
		// there's not always a matching ACTION_UP/_CANCEL event for every
		// ACTION_DOWN event.
		pieView.hideList();
	}

	private void resetHomeState() {
		showAllAppsOnResume = false;
		if (pieView.inEditMode()) {
			pieView.endEditMode();
		}
		hideAllApps();
	}

	private void hideKeyboadAndPrefsButton() {
		kb.hideFrom(searchInput);
		hidePrefsButton();
	}

	private boolean isSearchVisible() {
		return searchInput.getVisibility() == View.VISIBLE;
	}

	private boolean isGestureNavigationEnabled() {
		return Settings.Secure.getInt(getContentResolver(),
				"navigation_mode", 0) == 2;
	}

	private void updateAppList(boolean resetScroll) {
		pieView.filterAppList(searchInput.getText().toString(), resetScroll);
	}

	private void registerBackCallback() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
				onBackInvokedCallback != null) {
			return;
		}

		onBackInvokedCallback = this::handleBack;
		getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
				OnBackInvokedDispatcher.PRIORITY_DEFAULT,
				onBackInvokedCallback);
	}

	private void unregisterBackCallback() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
				onBackInvokedCallback == null) {
			return;
		}

		getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
				onBackInvokedCallback);
		onBackInvokedCallback = null;
	}

	private class FlingListener extends GestureDetector.SimpleOnGestureListener {
		private final int minimumVelocity;

		private FlingListener(int minimumVelocity) {
			this.minimumVelocity = minimumVelocity;
		}

		@Override
		public boolean onScroll(
				MotionEvent e1,
				MotionEvent e2,
				float distanceX,
				float distanceY) {
			if (pieView.appListNotScrolled() &&
					Math.abs(distanceY) > Math.abs(distanceX)) {
				pieView.dragDownListBy(distanceY);
			}
			return false;
		}

		@Override
		public boolean onFling(
				MotionEvent e1,
				MotionEvent e2,
				float velocityX,
				float velocityY) {
			// Compare speeds, not velocities: a fling to the left has a
			// negative velocityX that any drift downwards would beat.
			if (pieView.appListNotScrolled() &&
					Math.abs(velocityY) > Math.abs(velocityX) &&
					velocityY >= minimumVelocity &&
					e1 != null && e2 != null &&
					e2.getY() - e1.getY() > 0) {
				pieView.resetScroll();
				hideAllApps();
				return true;
			}
			return false;
		}
	}
}
