package de.markusfisch.android.pielauncher.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import de.markusfisch.android.pielauncher.R;
import de.markusfisch.android.pielauncher.content.LauncherItemKey;

public class CustomisedAppsAdapter
		extends ArrayAdapter<CustomisedAppsAdapter.CustomisedApp> {
	public static class CustomisedApp {
		public final LauncherItemKey key;
		public final String name;
		public final Drawable icon;

		public CustomisedApp(
				LauncherItemKey key,
				String name,
				Drawable icon) {
			this.key = key;
			this.name = name;
			this.icon = icon;
		}
	}

	public CustomisedAppsAdapter(
			Context context,
			ArrayList<CustomisedApp> apps) {
		super(context, 0, apps);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = LayoutInflater
					.from(parent.getContext())
					.inflate(R.layout.item_hidden_app, parent, false);
		}
		ViewHolder holder = getViewHolder(convertView);
		CustomisedApp app = getItem(position);
		if (app != null) {
			holder.nameView.setText(app.name);
			holder.iconView.setImageDrawable(app.icon);
		}
		return convertView;
	}

	ViewHolder getViewHolder(View view) {
		ViewHolder holder;
		if ((holder = (ViewHolder) view.getTag()) == null) {
			holder = new ViewHolder();
			holder.iconView = view.findViewById(R.id.icon);
			holder.nameView = view.findViewById(R.id.name);
			view.setTag(holder);
		}
		return holder;
	}

	private static final class ViewHolder {
		private ImageView iconView;
		private TextView nameView;
	}
}
