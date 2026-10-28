package dev.shrd.launcher.ui;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.launch.MinecraftMeta;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The version pickers ask Mojang and Fabric what exists. Both answers are small but neither is
 * instant, so they are fetched once, off the UI thread, and handed back on the UI thread.
 */
public final class VersionCatalog {

	private static List<String> releases;
	private static String latestRelease = "";
	private static final Map<String, List<String>> loaders = new HashMap<>();

	private VersionCatalog() {
	}

	public static String latestRelease() {
		return latestRelease;
	}

	public static void releases(Consumer<List<String>> callback) {
		if (releases != null) {
			callback.accept(releases);
			return;
		}

		background("shrd-versions", () -> {
			List<String> fetched = new ArrayList<>();
			String latest = "";

			try {
				latest = MinecraftMeta.latestRelease();

				for (MinecraftMeta.VersionEntry entry : MinecraftMeta.releases()) {
					fetched.add(entry.id());
				}
			} catch (Exception e) {
				Log.warn("could not list Minecraft versions: %s", e.getMessage());
			}

			releases = fetched;
			latestRelease = latest;
			return fetched;
		}, callback);
	}

	public static void fabricLoaders(String minecraftVersion, Consumer<List<String>> callback) {
		List<String> cached = loaders.get(minecraftVersion);

		if (cached != null) {
			callback.accept(cached);
			return;
		}

		background("shrd-loaders", () -> {
			List<String> fetched = new ArrayList<>();

			try {
				fetched.addAll(MinecraftMeta.fabricLoaderVersions(minecraftVersion));
			} catch (Exception e) {
				Log.warn("could not list Fabric loaders for %s: %s", minecraftVersion, e.getMessage());
			}

			loaders.put(minecraftVersion, fetched);
			return fetched;
		}, callback);
	}

	private static void background(String name, java.util.concurrent.Callable<List<String>> work, Consumer<List<String>> callback) {
		Thread thread = new Thread(() -> {
			List<String> result;

			try {
				result = work.call();
			} catch (Exception e) {
				result = List.of();
			}

			List<String> finalResult = result;
			SwingUtilities.invokeLater(() -> callback.accept(finalResult));
		}, name);

		thread.setDaemon(true);
		thread.start();
	}
}
