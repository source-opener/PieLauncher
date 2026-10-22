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
// big buttons with a picture and a word each. Plain views in a dialog,
// built when needed.
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
	private final Context context;
	private final Dialog dialog;
	private final LinearLayout body;
	private final LinearLayout icons;
	private final TextView firstIcon;
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

		icons = new LinearLayout(context);
		icons.setGravity(Gravity.CENTER);
		firstIcon = newIcon(icon, null);
		icons.addView(firstIcon);
		body.addView(icons);
		TextView header = newText(title, 18);
		header.setPadding(0, px(4), 0, px(12));
		body.addView(header);

		dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(body);
	}

	public void add(int iconId, int labelId, Runnable action) {
		entries.add(new Entry(iconId, labelId, action));
	}

	// Shows a second icon next to the first, for example the one used
	// while the work profile is paused, and names both.
	public void addHeaderIcon(Bitmap icon, int captionId, int firstCaptionId) {
		firstIcon.setText(firstCaptionId);
		TextView second = newIcon(icon, context.getString(captionId));
		second.setPadding(px(24), 0, 0, 0);
		icons.addView(second);
	}

	// A small title over the buttons added after it.
	public void heading(int labelId) {
		entries.add(new Entry(0, labelId, null));
	}

	public void show() {
		addGrid(entries);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(0));
			int width = context.getResources().getDisplayMetrics().widthPixels;
			window.setLayout(Math.min(width - px(32), px(420)),
					LinearLayout.LayoutParams.WRAP_CONTENT);
		}
		dialog.show();
	}

	private void addGrid(List<Entry> list) {
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
	}

	private TextView newIcon(Bitmap icon, CharSequence caption) {
		TextView tv = newText(caption, 12);
		if (icon != null) {
			Drawable d = new BitmapDrawable(context.getResources(), icon);
			d.setBounds(0, 0, px(56), px(56));
			tv.setCompoundDrawables(null, d, null, null);
			tv.setCompoundDrawablePadding(px(4));
		}
		return tv;
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
