package dev.shrd.launcher;

import dev.shrd.launcher.ui.LauncherFrame;
import dev.shrd.launcher.ui.Screenshot;
import dev.shrd.launcher.update.Updater;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shrd Launcher — the desktop app that installs and runs Shrd Client.
 * <p>
 * With no arguments it opens the window. The flags exist so the same launcher can be driven and
 * checked from a shell: {@code --prepare} resolves and downloads, {@code --launch} starts the
 * game, {@code --install-mods} copies Shrd Client and Fabric API into an instance, and
 * {@code --shot} renders a page to a PNG for the theme check.
 */
public final class ShrdLauncher {

	private ShrdLauncher() {
	}

	public static void main(String[] args) throws Exception {
		Map<String, String> options = parse(args);

		if (options.containsKey("help")) {
			printUsage();
			return;
		}

		if (options.containsKey("home")) {
			ShrdPaths.setHome(options.get("home"));
		}

		ShrdPaths.ensureDirs();
		LauncherConfig config = LauncherConfig.load();
		String instance = options.get("instance");

		if (instance != null && config.findInstance(instance) != null) {
			config.activeInstance = instance;
		}

		// A one-off override, so a manifest can be tried without saving it into the config.
		if (options.containsKey("manifest")) {
			config.updateManifestUrl = options.get("manifest");
		}

		if (options.containsKey("versions")) {
			System.exit(Cli.versions());
		}

		if (options.containsKey("version")) {
			System.out.println("SHRD-VERSION " + Version.CURRENT + (Updater.canSelfInstall() ? " installed=" + Updater.installDir() : " installed=no"));
			System.exit(0);
		}

		if (options.containsKey("check-update")) {
			System.exit(Cli.checkUpdate(config));
		}

		if (options.containsKey("update")) {
			System.exit(Cli.update(config, options.containsKey("apply")));
		}

		if (options.containsKey("install-mods")) {
			System.exit(Cli.installMods(config, instance));
		}

		if (options.containsKey("prepare")) {
			System.exit(Cli.prepare(config, instance));
		}

		if (options.containsKey("launch")) {
			System.exit(Cli.launch(config, instance, parseLong(options.get("wait"), 0)));
		}

		if (options.containsKey("shot")) {
			String[] parts = options.get("shot").split(",");
			String page = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "home";
			Path output = Path.of(parts.length > 1 && !parts[1].isBlank() ? parts[1] : "launcher-" + page + ".png");
			int width = (int) parseLong(options.get("width"), 1180);
			int height = (int) parseLong(options.get("height"), 740);
			Path written = Screenshot.write(config, page, output, width, height);
			System.out.println("SHRD-SHOT page=" + page + " file=" + written.toAbsolutePath() + " size=" + width + "x" + height);
			System.exit(0);
		}

		SwingUtilities.invokeLater(() -> {
			LauncherFrame frame = new LauncherFrame(config, "home");
			frame.setVisible(true);
			frame.app().startUpdateCheck();
		});
	}

	/**
	 * {@code --flag} for switches and {@code --flag value} for the ones that take a value. Values
	 * that start with a dash are still values, so {@code --jvm -Xmx2G} style input survives.
	 */
	private static Map<String, String> parse(String[] args) {
		Map<String, String> options = new LinkedHashMap<>();			Map<String, String> aliases = Map.of(
				"h", "home",
				"v", "versions",
				"V", "version",
				"?", "help",
			"shot", "shot",
			"launch", "launch",
			"prepare", "prepare",
			"install-mods", "install-mods");

		for (int index = 0; index < args.length; index++) {
			String arg = args[index];

			if (!arg.startsWith("--") && !arg.startsWith("-")) {
				continue;
			}

			String name = arg.replaceFirst("^-+", "");
			String canonical = aliases.getOrDefault(name, name);

			if (!canonical.equals(name)) {
				options.put(canonical, "");
				continue;
			}

			switch (name) {
				case "home", "instance", "wait", "width", "height", "manifest" -> {
					if (index + 1 < args.length) {
						options.put(name, args[++index]);
					}
				}
				case "shot" -> {
					// --shot <page>[,<file.png>]
					String page = index + 1 < args.length && !args[index + 1].startsWith("-") ? args[++index] : "home";
					options.put("shot", page);
				}
				case "launch", "prepare", "install-mods" -> {
					options.put(name, "yes");

					if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
						options.put("instance", args[++index]);
					}
				}
				default -> options.put(name, "yes");
			}
		}

		return options;
	}

	private static long parseLong(String value, long fallback) {
		try {
			return value == null ? fallback : Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static void printUsage() {
		System.out.println("""
			Shrd Launcher — Minecraft launcher for Shrd Client

			  (no arguments)                 open the launcher window
			  --home <dir>                   use a different data folder
			  --version                      print the launcher version and where it is installed
			  --check-update                 compare this build against the update manifest
			  --manifest <url>               use this manifest for the update check/update instead
			  --update [--apply]             download an update (--apply swaps it in and restarts)
			  --versions                     list Minecraft releases from Mojang
			  --install-mods [instance]      copy the built Shrd Client jar + Fabric API into an instance
			  --prepare [instance]           resolve and download everything, without launching
			  --launch [instance]            start the game
			  --wait <seconds>               with --launch: wait this long for the game to exit
			  --shot <page>[,<file.png>]     render a page (home|instances|mods|console|settings) to a PNG
			  --width <px> --height <px>     size used by --shot
			""");
	}
}
