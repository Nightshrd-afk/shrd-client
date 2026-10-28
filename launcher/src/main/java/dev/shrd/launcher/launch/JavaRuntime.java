package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a Java to run the game with. Minecraft 26.2 needs Java 25 or newer, and the usual case on
 * a development machine is a JDK sitting in the project's {@code .toolchain} folder — that is
 * checked first, then whatever the user configured, then {@code JAVA_HOME}, then PATH.
 */
public final class JavaRuntime {

	private static final Pattern VERSION = Pattern.compile("version \"([^\"]+)\"");

	private JavaRuntime() {
	}

	public static String executableName() {
		return isWindows() ? "java.exe" : "java";
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
	}

	/** The Java a launch should use, or {@code null} when nothing usable can be found. */
	public static Path detect(String configured) {
		if (configured != null && !configured.isBlank()) {
			Path path = Path.of(configured.trim());

			if (Files.isExecutable(path) || Files.isRegularFile(path)) {
				return path;
			}

			// A folder was given instead of the executable.
			Path inside = path.resolve("bin").resolve(executableName());

			if (Files.isRegularFile(inside)) {
				return inside;
			}

			Log.warn("configured Java %s does not exist", configured);
		}

		Path bundled = bundled();

		if (bundled != null) {
			return bundled;
		}

		String javaHome = System.getenv("JAVA_HOME");

		if (javaHome != null && !javaHome.isBlank()) {
			Path path = Path.of(javaHome, "bin", executableName());

			if (Files.isRegularFile(path)) {
				return path;
			}
		}

		Path onPath = which();

		if (onPath != null) {
			return onPath;
		}

		return null;
	}

	/**
	 * A JDK shipped next to the launcher. The launcher jar normally lives in
	 * {@code <project>/launcher/build/libs}, so the search walks up from there and from the
	 * working directory.
	 */
	public static Path bundled() {
		List<Path> starts = new ArrayList<>();

		try {
			Path source = Path.of(JavaRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
			starts.add(Files.isDirectory(source) ? source : source.getParent());
		} catch (Exception ignored) {
			// Falls back to the working directory below.
		}

		starts.add(Path.of(".").toAbsolutePath());

		for (Path start : starts) {
			Path directory = start;

			for (int level = 0; level < 6 && directory != null; level++) {
				Path found = newestJdk(directory.resolve(".toolchain"));

				if (found != null) {
					return found;
				}

				directory = directory.getParent();
			}
		}

		return null;
	}

	private static Path newestJdk(Path toolchain) {
		if (!Files.isDirectory(toolchain)) {
			return null;
		}

		Path best = null;

		try (var entries = Files.list(toolchain)) {
			for (Path entry : entries.toList()) {
				if (!Files.isDirectory(entry) || !entry.getFileName().toString().startsWith("jdk")) {
					continue;
				}

				Path java = entry.resolve("bin").resolve(executableName());

				if (Files.isRegularFile(java)) {
					// Names carry the version, so the highest-sorting name wins.
					if (best == null || entry.getFileName().toString().compareTo(best.getFileName().toString()) > 0) {
						best = java;
					}
				}
			}
		} catch (Exception e) {
			return null;
		}

		return best;
	}

	private static Path which() {
		String pathEnv = System.getenv("PATH");

		if (pathEnv == null) {
			return null;
		}

		for (String element : pathEnv.split(java.io.File.pathSeparator)) {
			if (element.isBlank()) {
				continue;
			}

			Path candidate = Path.of(element, executableName());

			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}

		return null;
	}

	/** {@code 25.0.5} for the given Java, or {@code null} if it cannot be run. */
	public static String version(Path java) {
		if (java == null) {
			return null;
		}

		try {
			Process process = new ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start();
			String output;

			try (InputStream stream = process.getInputStream()) {
				output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}

			process.waitFor();
			Matcher matcher = VERSION.matcher(output);

			if (matcher.find()) {
				return matcher.group(1);
			}

			return output.lines().findFirst().orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/** Major version number: {@code 25} for "25.0.5", {@code 8} for the old "1.8.0_392". */
	public static int major(String version) {
		if (version == null || version.isBlank()) {
			return 0;
		}

		try {
			String[] parts = version.split("[._-]");

			if (parts.length > 1 && parts[0].equals("1")) {
				return Integer.parseInt(parts[1]);
			}

			return Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
