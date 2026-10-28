package dev.shrd.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the launcher keeps everything. One root holds the shared caches (client versions,
 * libraries, assets) and one folder per instance holds that instance's world, config and mods —
 * the same split the official launcher uses, so an instance directory is portable.
 */
public final class ShrdPaths {

	private static Path home = Path.of(System.getProperty("user.home"), ".shrd-launcher");

	private ShrdPaths() {
	}

	/** Overridden by {@code --home <dir>} on the command line. */
	public static void setHome(String path) {
		home = Path.of(path).toAbsolutePath().normalize();
	}

	public static Path home() {
		return home;
	}

	public static Path versions() {
		return home.resolve("versions");
	}

	public static Path libraries() {
		return home.resolve("libraries");
	}

	public static Path assets() {
		return home.resolve("assets");
	}

	public static Path assetIndexes() {
		return assets().resolve("indexes");
	}

	public static Path assetObjects() {
		return assets().resolve("objects");
	}

	public static Path instances() {
		return home.resolve("instances");
	}

	public static Path instance(String name) {
		return instances().resolve(name);
	}

	public static Path logs() {
		return home.resolve("logs");
	}

	public static Path cache() {
		return home.resolve("cache");
	}

	/** Downloaded launcher updates, one folder per version, waiting to be swapped in. */
	public static Path updates() {
		return home.resolve("updates");
	}

	/** Player skins, cached by name so the hero does not fetch one on every repaint. */
	public static Path skins() {
		return home.resolve("skins");
	}

	/** Background images the user picked, copied here so moving the original cannot lose them. */
	public static Path backgrounds() {
		return home.resolve("backgrounds");
	}

	/** Servers the launcher runs on this machine, one folder each, worlds included. */
	public static Path servers() {
		return home.resolve("servers");
	}

	public static File configFile() {
		return home.resolve("shrd-launcher.json").toFile();
	}

	public static void ensureDirs() {
		for (Path path : new Path[] { home, versions(), libraries(), assets(), assetIndexes(), assetObjects(), instances(), logs(), cache(), updates(), skins(), backgrounds(), servers() }) {
			try {
				Files.createDirectories(path);
			} catch (Exception e) {
				throw new RuntimeException("could not create " + path, e);
			}
		}
	}

	/** Shortens a path for display without losing the part that identifies it. */
	public static String pretty(Path path) {
		String text = path.toAbsolutePath().toString();
		String homeDir = System.getProperty("user.home");

		if (homeDir != null && text.startsWith(homeDir)) {
			return "~" + text.substring(homeDir.length());
		}

		return text;
	}
}
