package de.markusfisch.android.pielauncher.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Window;
import android.view.WindowManager;

public class OptionsDialog {
	public static void show(Context context,
			int titleId,
			CharSequence[] items,
			DialogInterface.OnClickListener onClickListener) {
		AlertDialog dialog = Dialog.newDialog(context)
				.setTitle(titleId)
				.setItems(items, onClickListener)
				.create();
		// A list to pick from needs no keyboard, so leave it as it is.
		Window window = dialog.getWindow();
		if (window != null) {
			window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
		}
		dialog.show();
	}
}
