package de.markusfisch.android.pielauncher.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

// The drawer's search field. It may take Back before the keyboard does,
// so the keyboard can stay up while Back closes what the drawer shows.
public class SearchInput extends EditText {
	public interface BackListener {
		// Whether Back goes to the drawer instead of the keyboard.
		boolean keepsKeyboard();

		void onBack();
	}

	private BackListener backListener;

	public SearchInput(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setBackListener(BackListener listener) {
		backListener = listener;
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && backListener != null &&
				backListener.keepsKeyboard()) {
			// Claim the press and act on the release, like Back does.
			if (event.getAction() == KeyEvent.ACTION_UP) {
				backListener.onBack();
			}
			return true;
		}
		return super.onKeyPreIme(keyCode, event);
	}
}
