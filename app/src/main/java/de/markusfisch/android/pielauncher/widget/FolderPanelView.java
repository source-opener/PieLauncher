package de.markusfisch.android.pielauncher.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.content.Apps;
import de.markusfisch.android.pielauncher.content.Folders;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;
import de.markusfisch.android.pielauncher.graphics.Converter;

// Shows the apps of a folder in a panel right above the keyboard, filled
// from the bottom row up. Drawn on top of the drawer in the same window,
// so the keyboard stays where it is. In edit mode apps can be dragged to
// another place; every move can be undone, and the order can be reset to
// alphabetical.
public class FolderPanelView extends View {
	public interface Listener {
		void onLaunch(Apps.AppIcon icon);

		void onOptions(Apps.AppIcon icon);
	}

	private static final int COLUMNS = 4;
	// Header buttons from the right: Edit alone, or Done, Reset and Undo
	// while editing.
	private static final int DONE = 0;
	private static final int RESET = 1;
	private static final int UNDO = 2;

	private final Paint paintPanel = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint paintScrim = new Paint();
	private final Paint paintIcon = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final TextPaint paintText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
	private final TextPaint paintTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
	private final RectF panelRect = new RectF();
	private final Rect drawRect = new Rect();
	private final Rect visibleFrame = new Rect();
	private final int[] screenLocation = new int[2];
	private final Rect[] buttonRects = {new Rect(), new Rect(), new Rect()};
	private final List<List<Apps.AppIcon>> undo = new ArrayList<>();
	private final Runnable longPress = this::onLongPress;
	private final float dp;
	private final int iconSize;
	private final int padding;
	private final int touchSlop;
	private final int longPressTimeout;
	private final Bitmap iconEdit;
	private final Bitmap iconUndo;
	private final Bitmap iconReset;
	private final Bitmap iconDone;

	private List<Apps.AppIcon> icons = new ArrayList<>();
	private List<Apps.AppIcon> dragStart;
	private Listener listener;
	private ColorFilter tint;
	private long folderId = -1L;
	private String title;
	private boolean editing;
	private boolean moved;
	private boolean longPressed;
	private int covered;
	private int cellWidth;
	private int cellHeight;
	private int gridLeft;
	private int gridBottom;
	private int gridTop;
	private int scrollY;
	private int maxScrollY;
	private int downX;
	private int downY;
	private int downScrollY;
	private int touchX;
	private int touchY;
	private int pressed = -1;

	public FolderPanelView(Context context, AttributeSet attrs) {
		super(context, attrs);
		dp = context.getResources().getDisplayMetrics().density;
		float sp = context.getResources().getDisplayMetrics().scaledDensity;
		iconSize = Math.round(48f * dp);
		padding = Math.round(16f * dp);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		longPressTimeout = ViewConfiguration.getLongPressTimeout();
		paintScrim.setColor(0x66000000);
		paintText.setTextSize(12f * sp);
		paintText.setTextAlign(Paint.Align.CENTER);
		paintTitle.setTextSize(16f * sp);
		paintTitle.setFakeBoldText(true);
		iconEdit = Converter.getBitmapFromDrawable(context.getResources(),
				R.drawable.ic_edit);
		iconUndo = Converter.getBitmapFromDrawable(context.getResources(),
				R.drawable.ic_undo);
		iconReset = Converter.getBitmapFromDrawable(context.getResources(),
				R.drawable.ic_restore);
		iconDone = Converter.getBitmapFromDrawable(context.getResources(),
				R.drawable.ic_done);
		setVisibility(View.GONE);
	}

	public boolean isShowing() {
		return getVisibility() == View.VISIBLE;
	}

	@SuppressWarnings("deprecation")
	public void show(Apps.AppIcon folder, Listener listener) {
		this.listener = listener;
		folderId = Folders.idOf(folder);
		title = folder.label;
		editing = false;
		undo.clear();
		scrollY = 0;
		loadIcons();
		boolean light = PieLauncherApp.getPrefs(getContext())
				.useLightDialogs();
		paintPanel.setColor(getResources().getColor(light
				? R.color.bg_panel_light
				: R.color.bg_panel));
		int text = light ? 0xff202020 : 0xffffffff;
		paintText.setColor(text);
		paintTitle.setColor(text);
		tint = light
				? new PorterDuffColorFilter(text, PorterDuff.Mode.SRC_IN)
				: null;
		setVisibility(View.VISIBLE);
		measureCovered();
		invalidate();
	}

	public boolean hide() {
		if (!isShowing()) {
			return false;
		}
		removeCallbacks(longPress);
		setVisibility(View.GONE);
		icons = new ArrayList<>();
		return true;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		measureCovered();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		layoutPanel();
		canvas.drawRect(0, 0, getWidth(), getHeight(), paintScrim);
		float radius = 24f * dp;
		canvas.drawRoundRect(panelRect, radius, radius, paintPanel);
		drawHeader(canvas);
		canvas.save();
		canvas.clipRect(panelRect.left, gridTop, panelRect.right,
				gridBottom);
		boolean dragging = editing && moved && pressed > -1;
		for (int i = 0, size = icons.size(); i < size; ++i) {
			if (dragging && i == pressed) {
				// Drawn last, under the finger.
				continue;
			}
			drawIcon(canvas, icons.get(i), cellLeft(i), cellTop(i));
		}
		canvas.restore();
		if (dragging) {
			drawIcon(canvas, icons.get(pressed), touchX - cellWidth / 2,
					touchY - iconSize / 2);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		touchX = Math.round(event.getX());
		touchY = Math.round(event.getY());
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = touchX;
				downY = touchY;
				downScrollY = scrollY;
				moved = false;
				longPressed = false;
				pressed = indexAt(touchX, touchY);
				if (pressed > -1) {
					if (editing) {
						dragStart = new ArrayList<>(icons);
					} else {
						postDelayed(longPress, longPressTimeout);
					}
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (!moved && Math.abs(touchX - downX) +
						Math.abs(touchY - downY) > touchSlop) {
					moved = true;
					removeCallbacks(longPress);
				}
				if (moved) {
					if (editing && pressed > -1) {
						moveDragged();
					} else if (maxScrollY > 0) {
						scrollY = Math.max(0, Math.min(maxScrollY,
								downScrollY + touchY - downY));
					}
				}
				break;
			case MotionEvent.ACTION_UP:
				removeCallbacks(longPress);
				if (editing && pressed > -1 && moved) {
					drop();
				} else if (!moved && !longPressed) {
					tap();
				}
				pressed = -1;
				break;
			case MotionEvent.ACTION_CANCEL:
				removeCallbacks(longPress);
				if (editing && pressed > -1 && moved) {
					drop();
				}
				pressed = -1;
				break;
			default:
				break;
		}
		invalidate();
		// Nothing below gets a touch while the panel is showing.
		return true;
	}

	private void tap() {
		if (!panelRect.contains(touchX, touchY)) {
			hide();
			return;
		}
		int button = buttonAt(touchX, touchY);
		if (button > -1) {
			onButton(button);
			return;
		}
		int index = indexAt(touchX, touchY);
		if (index > -1 && !editing && listener != null) {
			Apps.AppIcon icon = icons.get(index);
			hide();
			listener.onLaunch(icon);
		}
	}

	private void onLongPress() {
		if (pressed < 0 || pressed >= icons.size() || listener == null) {
			return;
		}
		longPressed = true;
		Apps.AppIcon icon = icons.get(pressed);
		hide();
		listener.onOptions(icon);
	}

	private void onButton(int button) {
		if (!editing) {
			editing = true;
		} else if (button == UNDO) {
			if (!undo.isEmpty()) {
				icons = undo.remove(undo.size() - 1);
				saveOrder();
			}
		} else if (button == RESET) {
			undo.add(new ArrayList<>(icons));
			PieLauncherApp.folders.setOrder(getContext(), folderId, null);
			loadIcons();
		} else {
			editing = false;
		}
	}

	// The dragged app takes the place under the finger and the others
	// close up behind it, so the grid shows where it will land.
	private void moveDragged() {
		int target = nearestIndex(touchX, touchY);
		if (target > -1 && target != pressed) {
			icons.add(target, icons.remove(pressed));
			pressed = target;
		}
	}

	private void drop() {
		if (dragStart != null && !dragStart.equals(icons)) {
			undo.add(dragStart);
			saveOrder();
		}
		dragStart = null;
	}

	private void saveOrder() {
		List<LauncherItemKey> keys = new ArrayList<>(icons.size());
		for (Apps.AppIcon icon : icons) {
			keys.add(new LauncherItemKey(icon.componentName,
					icon.userHandle));
		}
		PieLauncherApp.folders.setOrder(getContext(), folderId, keys);
	}

	private void loadIcons() {
		icons = PieLauncherApp.folders.getOrderedApps(folderId,
				PieLauncherApp.apps.apps);
	}

	// How far the keyboard or the navigation bar, whichever is higher,
	// reaches into this view. Measured when shown and on size changes
	// only, because older Android asks the window manager for the frame.
	private void measureCovered() {
		if (getWindowToken() == null) {
			return;
		}
		getWindowVisibleDisplayFrame(visibleFrame);
		getLocationOnScreen(screenLocation);
		covered = Math.max(0,
				screenLocation[1] + getHeight() - visibleFrame.bottom);
	}

	private void layoutPanel() {
		int width = getWidth();
		int panelWidth = Math.min(width - padding * 2,
				Math.round(480f * dp));
		int left = (width - panelWidth) / 2;
		cellWidth = (panelWidth - padding * 2) / COLUMNS;
		cellHeight = iconSize + Math.round(paintText.getTextSize() * 2.2f);
		gridLeft = left + padding;
		int bottom = getHeight() - covered - padding;
		int buttonHeight = iconSize + Math.round(paintText.getTextSize());
		int header = buttonHeight + padding;
		int rows = Math.max(1, (icons.size() + COLUMNS - 1) / COLUMNS);
		// Leave room for the status bar and the search bar.
		int maxRows = Math.max(1, (bottom - padding * 2 - header -
				Math.round(96f * dp)) / cellHeight);
		int visibleRows = Math.min(rows, maxRows);
		maxScrollY = (rows - visibleRows) * cellHeight;
		gridBottom = bottom - padding;
		gridTop = gridBottom - visibleRows * cellHeight;
		panelRect.set(left, gridTop - header, left + panelWidth, bottom);
		int top = Math.round(panelRect.top) + padding / 2;
		int right = Math.round(panelRect.right) - padding / 2;
		int buttonWidth = Math.round(64f * dp);
		for (Rect rect : buttonRects) {
			rect.set(right - buttonWidth, top, right, top + buttonHeight);
			right -= buttonWidth;
		}
	}

	private void drawHeader(Canvas canvas) {
		int buttons = editing ? 3 : 1;
		float titleLeft = panelRect.left + padding;
		float titleRight = buttonRects[buttons - 1].left - padding / 2f;
		CharSequence text = TextUtils.ellipsize(editing
						? getContext().getString(R.string.folder_drag_hint)
						: title,
				paintTitle, titleRight - titleLeft, TextUtils.TruncateAt.END);
		canvas.drawText(text, 0, text.length(), titleLeft,
				buttonRects[0].centerY() - (paintTitle.descent() +
						paintTitle.ascent()) / 2f, paintTitle);
		if (editing) {
			drawButton(canvas, iconDone, R.string.folder_done,
					buttonRects[DONE], true);
			drawButton(canvas, iconReset, R.string.folder_reset,
					buttonRects[RESET], true);
			drawButton(canvas, iconUndo, R.string.folder_undo,
					buttonRects[UNDO], !undo.isEmpty());
		} else {
			drawButton(canvas, iconEdit, R.string.folder_edit,
					buttonRects[DONE], true);
		}
	}

	private void drawButton(
			Canvas canvas,
			Bitmap bitmap,
			int labelId,
			Rect rect,
			boolean enabled) {
		int half = Math.round(12f * dp);
		int cx = rect.centerX();
		int iconTop = rect.top + Math.round(6f * dp);
		drawRect.set(cx - half, iconTop, cx + half, iconTop + half * 2);
		int alpha = enabled ? 255 : 80;
		paintIcon.setAlpha(alpha);
		paintIcon.setColorFilter(tint);
		canvas.drawBitmap(bitmap, null, drawRect, paintIcon);
		paintIcon.setColorFilter(null);
		paintIcon.setAlpha(255);
		paintText.setAlpha(alpha);
		canvas.drawText(getContext().getString(labelId), cx,
				drawRect.bottom + paintText.getTextSize() * 1.3f, paintText);
		paintText.setAlpha(255);
	}

	private void drawIcon(Canvas canvas, Apps.AppIcon icon, int x, int y) {
		int ix = x + (cellWidth - iconSize) / 2;
		drawRect.set(ix, y, ix + iconSize, y + iconSize);
		canvas.drawBitmap(icon.bitmap, null, drawRect, paintIcon);
		CharSequence label = icon.fitLabel(paintText, cellWidth - 4f * dp);
		canvas.drawText(label, 0, label.length(), x + cellWidth / 2f,
				y + iconSize + paintText.getTextSize() * 1.3f, paintText);
	}

	// Rows count from the bottom, so the first app sits bottom left.
	private int cellLeft(int index) {
		return gridLeft + index % COLUMNS * cellWidth;
	}

	private int cellTop(int index) {
		return gridBottom - (index / COLUMNS + 1) * cellHeight + scrollY;
	}

	private int indexAt(int x, int y) {
		if (y < gridTop || y >= gridBottom) {
			return -1;
		}
		for (int i = 0, size = icons.size(); i < size; ++i) {
			int left = cellLeft(i);
			int top = cellTop(i);
			if (x >= left && x < left + cellWidth &&
					y >= top && y < top + cellHeight) {
				return i;
			}
		}
		return -1;
	}

	// The place a dragged app would go: the cell under the finger, or the
	// last one when the finger is past the end.
	private int nearestIndex(int x, int y) {
		if (icons.isEmpty() || cellWidth < 1 || cellHeight < 1) {
			return -1;
		}
		int column = Math.max(0, Math.min(COLUMNS - 1,
				(x - gridLeft) / cellWidth));
		int row = Math.max(0, (gridBottom + scrollY - y) / cellHeight);
		return Math.min(icons.size() - 1, row * COLUMNS + column);
	}

	private int buttonAt(int x, int y) {
		for (int i = 0, buttons = editing ? 3 : 1; i < buttons; ++i) {
			if (buttonRects[i].contains(x, y)) {
				return i;
			}
		}
		return -1;
	}
}
