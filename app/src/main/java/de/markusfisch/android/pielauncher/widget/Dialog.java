package de.markusfisch.android.pielauncher.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.app.PieLauncherApp;

public class Dialog {
	public static AlertDialog.Builder newDialog(Context context) {
		boolean light = PieLauncherApp.getPrefs(context).useLightDialogs();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return new AlertDialog.Builder(context, light
					? R.style.PanelDialogLight
					: R.style.PanelDialog);
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ||
				!light) {
			return new AlertDialog.Builder(context);
		}
		return new AlertDialog.Builder(context, R.style.LightDialog);
	}
}
