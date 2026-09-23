package de.markusfisch.android.pielauncher.graphics;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.markusfisch.android.pielauncher.io.IconMappingsStorage;

public class IconPack {
	public static class Pack {
		private static final String[] DRAWABLE_LISTS = {
				"drawable.xml", "icon_pack.xml", "appfilter.xml"};
		private static final Pattern DRAWABLE_ATTRIBUTE =
				Pattern.compile("drawable\\s*=\\s*\"([^\"]+)\"");

		public final String packageName;
		public final String name;
		public final Resources resources;

		public Pack(String packageName, String name, Resources resources) {
			this.packageName = packageName;
			this.name = name;
			this.resources = resources;
		}

		public Drawable getDrawable(String drawableName) {
			if (drawableName == null) {
				return null;
			}
			@SuppressLint("DiscouragedApi")
			int id = resources.getIdentifier(drawableName, "drawable",
					packageName);
			try {
				return id > 0 ? resources.getDrawable(id) : null;
			} catch (NotFoundException e) {
				return null;
			}
		}

		// Every icon in the pack, for picking one by hand. A pack lists
		// them in drawable.xml, in the order it wants them shown, while
		// appfilter.xml only names the ones it assigns to an app, which
		// is a fraction of a large pack. Read all of them and keep the
		// first order an icon appears in.
		public ArrayList<String> getDrawableNames() {
			LinkedHashSet<String> names = new LinkedHashSet<>();
			for (String fileName : DRAWABLE_LISTS) {
				loadDrawableNames(fileName, names);
			}
			return new ArrayList<>(names);
		}

		private void loadDrawableNames(String fileName, Set<String> names) {
			try {
				parseDrawableNames(fileName, names);
			} catch (FileNotFoundException e) {
				// Not every pack ships every one of these.
			} catch (XmlPullParserException | IOException e) {
				scanDrawableNames(fileName, names);
			}
		}

		private void parseDrawableNames(String fileName, Set<String> names)
				throws XmlPullParserException, IOException {
			InputStream is = null;
			try {
				is = resources.getAssets().open(fileName);
				XmlPullParser parser = Xml.newPullParser();
				// Let the parser take the encoding from the document
				// rather than assuming the platform default.
				parser.setInput(is, null);
				for (int eventType = parser.getEventType();
						eventType != XmlPullParser.END_DOCUMENT;
						eventType = parser.next()) {
					if (eventType == XmlPullParser.START_TAG &&
							"item".equals(parser.getName())) {
						addDrawableName(names, parser.getAttributeValue(
								null, "drawable"));
					}
				}
			} finally {
				close(is);
			}
		}

		// These files are written by hand and a single unescaped
		// ampersand ends the parse where it sits, taking every icon
		// after it with it. Read the names out of the raw text instead,
		// which no amount of malformed XML around them can stop.
		private void scanDrawableNames(String fileName, Set<String> names) {
			InputStream is = null;
			try {
				is = resources.getAssets().open(fileName);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(is));
				String line;
				while ((line = reader.readLine()) != null) {
					Matcher matcher = DRAWABLE_ATTRIBUTE.matcher(line);
					while (matcher.find()) {
						addDrawableName(names, matcher.group(1));
					}
				}
			} catch (IOException e) {
				// Nothing more to take from this file.
			} finally {
				close(is);
			}
		}

		private static void addDrawableName(Set<String> names, String name) {
			if (name != null && !name.isEmpty()) {
				names.add(name);
			}
		}

		private static void close(InputStream is) {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// Ignore.
				}
			}
		}

		public void loadComponentAndDrawableNames(
				LinkedHashMap<String, String> map) {
			InputStream is = null;
			try {
				is = resources.getAssets().open("appfilter.xml");
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(new InputStreamReader(is));
				for (int eventType = parser.getEventType();
						eventType != XmlPullParser.END_DOCUMENT;
						eventType = parser.next()) {
					if (eventType == XmlPullParser.START_TAG &&
							"item".equals(parser.getName())) {
						String component = parser.getAttributeValue(
								null, "component");
						if (component == null) {
							component = parser.getAttributeValue(
									null, "activity");
						}
						if (component == null || component.isEmpty()) {
							continue;
						}
						String drawable = parser.getAttributeValue(
								null, "drawable");
						if (drawable == null || drawable.isEmpty()) {
							continue;
						}
						ComponentName cn = parseComponent(component);
						if (cn != null) {
							map.put(cn.flattenToShortString(), drawable);
						} else {
							map.put(component, drawable);
						}
					}
				}
			} catch (XmlPullParserException | IOException e) {
				// Ignore.
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						// Ignore.
					}
				}
			}
		}
	}

	public static class PackAndDrawable implements Serializable {
		public final String packageName;
		public final String drawableName;

		public PackAndDrawable(String packageName, String drawableName) {
			this.packageName = packageName;
			this.drawableName = drawableName;
		}
	}

	public final LinkedHashMap<String, Pack> packs = new LinkedHashMap<>();
	public final LinkedHashMap<String, String> componentToDrawableNames =
			new LinkedHashMap<>();

	private final HashMap<ComponentName, PackAndDrawable> mappings =
			new HashMap<>();

	private PackageManager packageManager;
	private IconPack.Pack selectedPack;

	// The packs and the mappings are written while apps are indexed in the
	// background but read from the main thread too, for example when a
	// folder resolves its icon, so guard them.
	public synchronized boolean hasPacks() {
		return !packs.isEmpty();
	}

	public synchronized void restoreMappings(Context context) {
		IconMappingsStorage.restore(
				context, getSelectedIconPackageName(), mappings);
	}

	public synchronized void storeMappings(Context context) {
		IconMappingsStorage.store(
				context, getSelectedIconPackageName(), mappings);
	}

	public synchronized boolean hasMapping(ComponentName componentName) {
		return mappings.containsKey(componentName);
	}

	public synchronized void addMapping(
			String iconPackageName,
			ComponentName componentName,
			String drawableName) {
		mappings.put(componentName,
				new PackAndDrawable(iconPackageName, drawableName));
	}

	public synchronized void removeMapping(ComponentName componentName) {
		mappings.remove(componentName);
	}

	public synchronized void clearMappings() {
		mappings.clear();
	}

	// The icon a component was explicitly mapped to, without the fallbacks
	// getIcon() applies. Folders have no launch intent and no icon of their
	// own to fall back on, so this is all there is to look up for them.
	public synchronized Drawable getMappedIcon(ComponentName componentName) {
		PackAndDrawable pad = mappings.get(componentName);
		if (pad == null) {
			return null;
		}
		Pack pack = packs.get(pad.packageName);
		return pack != null ? pack.getDrawable(pad.drawableName) : null;
	}

	public synchronized String getSelectedIconPackageName() {
		return selectedPack != null ? selectedPack.packageName : null;
	}

	public synchronized HashMap<String, String> getIconPacks() {
		HashMap<String, String> map = new HashMap<>();
		for (Pack pack : packs.values()) {
			map.put(pack.packageName, pack.name);
		}
		return map;
	}

	public synchronized void updatePacks(PackageManager pm) {
		packs.clear();
		for (String theme : new String[]{
				"org.adw.launcher.THEMES",
				"com.gau.go.launcherex.theme"
		}) {
			for (ResolveInfo info : queryIntentActivities(
					pm, new Intent(theme))) {
				String packageName = info.activityInfo.packageName;
				try {
					packs.put(packageName, new Pack(
							packageName,
							pm.getApplicationLabel(getApplicationInfo(
									pm, packageName)).toString(),
							pm.getResourcesForApplication(packageName)));
				} catch (PackageManager.NameNotFoundException e) {
					// Ignore.
				}
			}
		}
	}

	public synchronized void selectPack(PackageManager pm, String packageName) {
		selectedPack = null;
		packageManager = null;
		componentToDrawableNames.clear();
		if (pm == null) {
			return;
		}
		// Always update because packs may have been added/removed.
		updatePacks(pm);
		if (packageName == null || packageName.isEmpty()) {
			return;
		}
		selectedPack = packs.get(packageName);
		if (selectedPack == null) {
			return;
		}
		// Always reload packages and drawables as the pack may have
		// been updated.
		selectedPack.loadComponentAndDrawableNames(componentToDrawableNames);
		packageManager = pm;
	}

	public synchronized Drawable getIcon(ComponentName componentName) {
		String drawableName = null;
		PackAndDrawable pad = mappings.get(componentName);
		if (pad != null) {
			if (selectedPack != null &&
					pad.packageName.equals(selectedPack.packageName)) {
				drawableName = pad.drawableName;
			} else {
				Pack pack = packs.get(pad.packageName);
				if (pack != null) {
					return pack.getDrawable(pad.drawableName);
				}
			}
		}
		if (selectedPack == null) {
			return null;
		}
		if (drawableName == null) {
			Intent intent = packageManager.getLaunchIntentForPackage(
					componentName.getPackageName());
			if (intent == null) {
				return null;
			}
			drawableName = componentToDrawableNames.get(
					componentName.flattenToShortString());
		}
		return selectedPack.getDrawable(drawableName);
	}

	private static ComponentName parseComponent(String s) {
		if (s == null) {
			return null;
		}
		if (s.startsWith("ComponentInfo{") && s.endsWith("}")) {
			s = s.substring(14, s.length() - 1);
		}
		return ComponentName.unflattenFromString(s);
	}

	private static List<ResolveInfo> queryIntentActivities(
			PackageManager pm,
			Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return pm.queryIntentActivities(intent,
					PackageManager.ResolveInfoFlags.of(
							PackageManager.GET_META_DATA));
		} else {
			return pm.queryIntentActivities(intent,
					PackageManager.GET_META_DATA);
		}
	}

	private static ApplicationInfo getApplicationInfo(
			PackageManager pm,
			String packageName)
			throws PackageManager.NameNotFoundException {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return pm.getApplicationInfo(packageName,
					PackageManager.ApplicationInfoFlags.of(
							PackageManager.GET_META_DATA));
		} else {
			return pm.getApplicationInfo(packageName,
					PackageManager.GET_META_DATA);
		}
	}
}
