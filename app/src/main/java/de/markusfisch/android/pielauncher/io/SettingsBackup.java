package de.markusfisch.android.pielauncher.io;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.markusfisch.android.pielauncher.app.PieLauncherApp;

public class SettingsBackup {
	public static final String MIME_TYPE = "application/json";
	public static final String FILE_NAME = "pielauncher-settings.json";

	private static final int FORMAT = 1;
	private static final String FORMAT_KEY = "format";
	private static final String PREFERENCES = "preferences";
	private static final String TABLES = "tables";
	private static final String TYPE = "type";
	private static final String VALUE = "value";
	private static final String BOOLEAN = "boolean";
	private static final String INT = "int";
	private static final String LONG = "long";
	private static final String FLOAT = "float";
	private static final String STRING = "string";
	// Apps.addDrawerIcon() keys the drawer icon like this.
	private static final String DRAWER_SUFFIX = ".drawer/";

	public static void export(Context context, OutputStream out)
			throws IOException, JSONException {
		JSONObject backup = new JSONObject();
		backup.put(FORMAT_KEY, FORMAT);
		backup.put(PREFERENCES, preferencesToJson(
				PieLauncherApp.getPrefs(context).exportValues()));
		backup.put(TABLES,
				PieLauncherApp.getDatabase(context).exportTables());
		out.write(backup.toString(1).getBytes("UTF-8"));
		out.flush();
	}

	public static void restore(Context context, InputStream in)
			throws IOException, JSONException {
		JSONObject backup = new JSONObject(readAll(in));
		if (backup.optInt(FORMAT_KEY, 0) < 1) {
			throw new IOException("not a settings backup");
		}

		JSONObject tables = backup.optJSONObject(TABLES);
		if (tables != null) {
			adoptDrawerKeys(context, tables);
			PieLauncherApp.getDatabase(context).importTables(tables);
		}

		JSONObject preferences = backup.optJSONObject(PREFERENCES);
		if (preferences != null) {
			PieLauncherApp.getPrefs(context).importValues(
					jsonToPreferences(preferences));
		}
	}

	// The drawer icon is keyed by the package of the build that stored
	// it, so one taken over from the other build would not be found and
	// the icon would lose its place in the pie menu.
	private static void adoptDrawerKeys(Context context, JSONObject tables)
			throws JSONException {
		String own = context.getPackageName() + DRAWER_SUFFIX;
		String other = SettingsSync.otherPackageName(
				context.getPackageName()) + DRAWER_SUFFIX;
		for (Iterator<String> it = tables.keys(); it.hasNext(); ) {
			JSONArray rows = tables.optJSONArray(it.next());
			for (int i = 0, len = rows == null ? 0 : rows.length();
					i < len; ++i) {
				JSONObject row = rows.optJSONObject(i);
				if (row == null) {
					continue;
				}
				List<String> columns = new ArrayList<>();
				for (Iterator<String> c = row.keys(); c.hasNext(); ) {
					columns.add(c.next());
				}
				for (String column : columns) {
					Object value = row.opt(column);
					if (value instanceof String &&
							((String) value).startsWith(other)) {
						row.put(column, own + ((String) value).substring(
								other.length()));
					}
				}
			}
		}
	}

	private static String readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) > 0) {
			out.write(buffer, 0, read);
		}
		return out.toString("UTF-8");
	}

	private static JSONObject preferencesToJson(Map<String, ?> values)
			throws JSONException {
		JSONObject json = new JSONObject();
		for (Map.Entry<String, ?> entry : values.entrySet()) {
			Object value = entry.getValue();
			String type;
			if (value instanceof Boolean) {
				type = BOOLEAN;
			} else if (value instanceof Integer) {
				type = INT;
			} else if (value instanceof Long) {
				type = LONG;
			} else if (value instanceof Float) {
				type = FLOAT;
			} else if (value instanceof String) {
				type = STRING;
			} else {
				continue;
			}
			JSONObject item = new JSONObject();
			item.put(TYPE, type);
			item.put(VALUE, value instanceof Float
					? ((Float) value).doubleValue()
					: value);
			json.put(entry.getKey(), item);
		}
		return json;
	}

	private static Map<String, Object> jsonToPreferences(JSONObject json)
			throws JSONException {
		Map<String, Object> values = new HashMap<>();
		for (Iterator<String> it = json.keys(); it.hasNext(); ) {
			String key = it.next();
			JSONObject item = json.optJSONObject(key);
			if (item == null) {
				continue;
			}
			String type = item.optString(TYPE);
			if (BOOLEAN.equals(type)) {
				values.put(key, item.getBoolean(VALUE));
			} else if (INT.equals(type)) {
				values.put(key, item.getInt(VALUE));
			} else if (LONG.equals(type)) {
				values.put(key, item.getLong(VALUE));
			} else if (FLOAT.equals(type)) {
				values.put(key, (float) item.getDouble(VALUE));
			} else if (STRING.equals(type)) {
				values.put(key, item.getString(VALUE));
			}
		}
		return values;
	}
}
