package de.markusfisch.android.pielauncher.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;
import de.markusfisch.android.pielauncher.graphics.Converter;

// What a long press on an app or a folder opens: its icon and name over
// big buttons with a picture and a word each, and the rarer ones behind
// More. Plain views in a dialog, built when needed.
public class ItemPanel {
	private static final int COLUMNS = 4;

	private static final class Entry {
		private final int iconId;
		private final int labelId;
		private final Runnable action;

		private Entry(int iconId, int labelId, Runnable action) {
			this.iconId = iconId;
			this.labelId = labelId;
			this.action = action;
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private final List<Entry> more = new ArrayList<>();
	private final Context context;
	private final Dialog dialog;
	private final LinearLayout body;
	private final boolean light;
	private final int textColor;
	private final float dp;

	public ItemPanel(Context context, Bitmap icon, CharSequence title) {
		this.context = context;
		dp = context.getResources().getDisplayMetrics().density;
		light = PieLauncherApp.getPrefs(context).useLightDialogs();
		textColor = light ? 0xff202020 : 0xffffffff;

		body = new LinearLayout(context);
		body.setOrientation(LinearLayout.VERTICAL);
		body.setBackgroundResource(light
				? R.drawable.panel_light
				: R.drawable.panel);
		int padding = px(16);
		body.setPadding(padding, padding, padding, padding);

		TextView header = newText(title, 18);
		header.setPadding(0, 0, 0, px(12));
		if (icon != null) {
			Drawable d = new BitmapDrawable(context.getResources(), icon);
			d.setBounds(0, 0, px(56), px(56));
			header.setCompoundDrawables(null, d, null, null);
			header.setCompoundDrawablePadding(px(8));
		}
		body.addView(header);

		dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(body);
	}

	public void add(int iconId, int labelId, Runnable action) {
		entries.add(new Entry(iconId, labelId, action));
	}

	public void addMore(int iconId, int labelId, Runnable action) {
		more.add(new Entry(iconId, labelId, action));
	}

	// A small title over the buttons added after it.
	public void heading(int labelId) {
		entries.add(new Entry(0, labelId, null));
	}

	public void show() {
		addGrid(entries);
		if (!more.isEmpty()) {
			List<Entry> toggle = new ArrayList<>();
			toggle.add(new Entry(R.drawable.ic_more, R.string.action_more,
					null));
			LinearLayout moreRow = addGrid(toggle);
			moreRow.getChildAt(0).setOnClickListener((v) -> {
				body.removeView(moreRow);
				addGrid(more);
			});
		}
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(0));
			int width = context.getResources().getDisplayMetrics().widthPixels;
			window.setLayout(Math.min(width - px(32), px(420)),
					LinearLayout.LayoutParams.WRAP_CONTENT);
		}
		dialog.show();
	}

	// Returns the last row, which is what the More toggle needs.
	private LinearLayout addGrid(List<Entry> list) {
		LinearLayout row = null;
		for (Entry entry : list) {
			if (entry.iconId == 0) {
				TextView heading = newText(context.getString(entry.labelId),
						14);
				heading.setGravity(Gravity.START);
				heading.setPadding(px(8), px(12), px(8), px(4));
				body.addView(heading);
				row = null;
				continue;
			}
			if (row == null || row.getChildCount() == COLUMNS) {
				row = new LinearLayout(context);
				row.setOrientation(LinearLayout.HORIZONTAL);
				row.setWeightSum(COLUMNS);
				body.addView(row);
			}
			row.addView(newButton(entry), new LinearLayout.LayoutParams(
					0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		}
		return row;
	}

	private TextView newButton(Entry entry) {
		TextView button = newText(context.getString(entry.labelId), 12);
		button.setBackgroundResource(R.drawable.panel_button);
		button.setPadding(px(4), px(12), px(4), px(12));
		button.setMaxLines(2);
		Drawable icon = Converter.getDrawable(context.getResources(),
				entry.iconId).mutate();
		icon.setBounds(0, 0, px(28), px(28));
		if (light) {
			icon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
		}
		button.setCompoundDrawables(null, icon, null, null);
		button.setCompoundDrawablePadding(px(6));
		if (entry.action != null) {
			button.setOnClickListener((v) -> {
				dialog.dismiss();
				entry.action.run();
			});
		}
		return button;
	}

	private TextView newText(CharSequence text, int sp) {
		TextView tv = new TextView(context);
		tv.setText(text);
		tv.setTextColor(textColor);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		tv.setGravity(Gravity.CENTER);
		return tv;
	}

	private int px(int dps) {
		return Math.round(dps * dp);
	}
}
