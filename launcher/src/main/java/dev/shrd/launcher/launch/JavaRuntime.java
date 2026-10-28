package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds a Java to run the game with. Minecraft 26.2 needs Java 25 or newer, and the usual case on
 * a development machine is a JDK sitting in the project's {@code .toolchain} folder — that is
 * checked first, then whatever the user configured, then {@code JAVA_HOME}, then PATH.
 */
public final class JavaRuntime {

	/** What Minecraft 26.2 needs; anything older cannot load the game. */
	public static final int REQUIRED_MAJOR = 25;

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

		// The runtime this launcher is itself running on. For the installed copy that is the trimmed
		// Java 25 in the app image, so the game always starts even on a machine with no Java at all.
		Path own = own();

		if (own != null) {
			return own;
		}

		Path bundled = bundled();

		if (bundled != null) {
			return bundled;
		}

		// A machine that already plays Minecraft has a runtime from the official launcher.
		Path minecraft = minecraftRuntime();

		if (minecraft != null) {
			return minecraft;
		}

		// A runtime this launcher downloaded for itself on an earlier run.
		Path managed = managed();

		if (managed != null) {
			return managed;
		}

		Path jdk = newestInstalledJdk();

		if (jdk != null) {
			return jdk;
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
	 * The Java running this process, from {@code java.home}. This is the one answer that is always
	 * right: whatever started the launcher is by definition a Java that works here, and the packaged
	 * build ships its own, so an installed launcher never has to ask the player to install anything.
	 */
	public static Path own() {
		try {
			String home = System.getProperty("java.home");

			if (home != null && !home.isBlank()) {
				Path path = Path.of(home, "bin", executableName());

				if (Files.isRegularFile(path)) {
					return path;
				}
			}
		} catch (Exception ignored) {
			// Falls through to the other candidates.
		}

		return null;
	}

	/**
	 * A runtime the official Minecraft launcher installed, e.g.
	 * {@code %APPDATA%\.minecraft\runtime\java-runtime-delta\windows-x64\...\bin\java.exe}.
	 * The newest one that reports a suitable major version wins.
	 */
	public static Path minecraftRuntime() {
		Path runtime = minecraftDirectory().resolve("runtime");

		if (!Files.isDirectory(runtime)) {
			return null;
		}

		Path best = null;
		int bestMajor = 0;

		try (var paths = Files.walk(runtime, 6)) {
			for (Path candidate : paths.filter(Files::isRegularFile).toList()) {
				if (!candidate.getFileName().toString().equals(executableName())) {
					continue;
				}

				int major = major(version(candidate));

				if (major > bestMajor) {
					bestMajor = major;
					best = candidate;
				}
			}
		} catch (Exception e) {
			return null;
		}

		return bestMajor >= 21 ? best : null;
	}

	/** {@code %APPDATA%\.minecraft} on Windows, {@code ~/.minecraft} elsewhere. */
	private static Path minecraftDirectory() {
		if (isWindows()) {
			String appData = System.getenv("APPDATA");

			if (appData != null && !appData.isBlank()) {
				return Path.of(appData, ".minecraft");
			}
		}

		return Path.of(System.getProperty("user.home", "."), ".minecraft");
	}

	/**
	 * The runtime the installer ships: {@code <install>/runtime/bin/java}. The app image starts the
	 * launcher through {@code <install>/Shrd Launcher.exe}, so {@code java.home} already points here
	 * and {@link #own()} catches it; this covers the unpacked zip and any other layout where it does
	 * not.
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

				// An installed or unpacked launcher keeps its runtime beside itself or beside app/.
				for (Path home : List.of(directory.resolve("runtime"), directory.resolve("jre"),
					directory.resolve("app").resolve("runtime"), directory.resolve("app").resolve("jre"))) {
					Path java = home.resolve("bin").resolve(executableName());

					if (Files.isRegularFile(java)) {
						return java;
					}
				}

				directory = directory.getParent();
			}
		}

		return null;
	}

	/** A Java the launcher downloaded itself, under its own data folder. */
	public static Path managed() {
		Path root = managedRoot();

		if (!Files.isDirectory(root)) {
			return null;
		}

		Path best = null;
		int bestMajor = 0;

		try (var entries = Files.list(root)) {
			for (Path entry : entries.toList()) {
				Path java = entry.resolve("bin").resolve(executableName());

				if (!Files.isRegularFile(java)) {
					continue;
				}

				int major = major(readRelease(entry.resolve("release")));

				if (major > bestMajor) {
					bestMajor = major;
					best = java;
				}
			}
		} catch (Exception e) {
			return null;
		}

		return best;
	}

	public static Path managedRoot() {
		return ShrdPaths.home().resolve("runtime");
	}

	/**
	 * JDKs already on the machine, in the places installers put them. Reading the folders rather
	 * than running each java keeps this instant, and a {@code release} file records the version
	 * without starting a process.
	 */
	public static Path newestInstalledJdk() {
		List<Path> roots = new ArrayList<>();
		String home = System.getProperty("user.home", ".");

		if (isWindows()) {
			addEnv(roots, "ProgramFiles", "Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft", "Semeru", "RedHat");
			addEnv(roots, "ProgramFiles(x86)", "Java");
			addEnv(roots, "LOCALAPPDATA", "Programs/Eclipse Adoptium", "Programs/Microsoft", "Programs/Java", "Programs/Zulu");
			addEnv(roots, "APPDATA", ".minecraft/runtime");
		}

		roots.add(Path.of(home, ".jdks"));
		roots.add(Path.of(home, ".gradle", "jdks"));

		Path best = null;
		int bestMajor = 0;

		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				continue;
			}

			try (var entries = Files.list(root)) {
				for (Path entry : entries.toList()) {
					Path java = javaIn(entry);

					if (java == null) {
						// Minecraft's runtimes nest one more level: java-runtime-gamma/windows-x64/…
						for (Path child : children(entry)) {
							java = javaIn(child);

							if (java != null) {
								break;
							}
						}
					}

					if (java == null) {
						continue;
					}

					int major = major(readRelease(java.getParent().getParent().resolve("release")));

					if (major > bestMajor) {
						bestMajor = major;
						best = java;
					}
				}
			} catch (Exception ignored) {
				// An unreadable install folder is simply not a candidate.
			}
		}

		return best;
	}

	private static void addEnv(List<Path> roots, String variable, String... children) {
		String value = System.getenv(variable);

		if (value == null || value.isBlank()) {
			return;
		}

		for (String child : children) {
			roots.add(Path.of(value, child.split("/")));
		}
	}

	private static Path javaIn(Path home) {
		Path java = home.resolve("bin").resolve(executableName());
		return Files.isRegularFile(java) ? java : null;
	}

	private static List<Path> children(Path directory) {
		try (var entries = Files.list(directory)) {
			return entries.filter(Files::isDirectory).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	/** {@code JAVA_VERSION="25.0.5"} out of a JDK's release file, or null. */
	private static String readRelease(Path release) {
		if (!Files.isRegularFile(release)) {
			return null;
		}

		try {
			for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
				if (line.startsWith("JAVA_VERSION=")) {
					return line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
				}
			}
		} catch (Exception ignored) {
			// A malformed release file just means the java -version probe decides.
		}

		return null;
	}

	/**
	 * A Java 25 for the game, downloading one into the launcher's data folder when this machine has
	 * none. Called after signing in and before the first launch, so the player never has to find a
	 * JDK themselves.
	 */
	public static Path ensure(Consumer<String> note) throws IOException {
		Path found = detect("");

		if (found != null && major(version(found)) >= REQUIRED_MAJOR) {
			return found;
		}

		Path managed = managed();

		if (managed != null && major(readRelease(managed.getParent().getParent().resolve("release"))) >= REQUIRED_MAJOR) {
			return managed;
		}

		note.accept("Java 25 is not installed — downloading it for you …");
		return download(note);
	}

	/**
	 * Fetches Adoptium's JRE 25 for this platform and unpacks it under {@code ~/.shrd-launcher/
	 * runtime/jre-25}. That is a plain Temurin build, so it needs no installer and no admin rights.
	 */
	public static Path download(Consumer<String> note) throws IOException {
		Path root = managedRoot();
		Files.createDirectories(root);
		Path archive = root.resolve("jre-25.zip");
		Path target = root.resolve("jre-25");

		String os = isWindows() ? "windows" : System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac") ? "mac" : "linux";
		String arch = System.getProperty("os.arch", "x86_64").contains("aarch64") ? "aarch64" : "x64";
		String url = "https://api.adoptium.net/v3/binary/latest/" + REQUIRED_MAJOR + "/ga/" + os + "/" + arch
			+ "/jre/hotspot/normal/eclipse";

		Log.info("downloading Java %d from %s", REQUIRED_MAJOR, url);
		long bytes = dev.shrd.launcher.launch.Http.download(url, archive);
		note.accept("Unpacking Java 25 (" + (bytes / 1_048_576) + " MB) …");

		Path staging = root.resolve("jre-25-unpacking");
		deleteTree(staging);
		unzip(archive, staging);
		Files.deleteIfExists(archive);

		// Adoptium zips wrap everything in one folder named after the build.
		Path unpacked = staging;
		try (var entries = Files.list(staging)) {
			List<Path> children = entries.toList();

			if (children.size() == 1 && Files.isDirectory(children.get(0))) {
				unpacked = children.get(0);
			}
		}

		deleteTree(target);
		Files.move(unpacked, target, StandardCopyOption.REPLACE_EXISTING);
		deleteTree(staging);

		Path java = target.resolve("bin").resolve(executableName());

		if (!Files.isRegularFile(java)) {
			throw new IOException("the downloaded Java does not contain " + executableName());
		}

		Log.info("Java %d ready at %s", REQUIRED_MAJOR, java);
		return java;
	}

	private static void unzip(Path archive, Path target) throws IOException {
		Files.createDirectories(target);

		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
			ZipEntry entry;

			while ((entry = zip.getNextEntry()) != null) {
				Path destination = target.resolve(entry.getName()).normalize();

				if (!destination.startsWith(target)) {
					throw new IOException("refusing an archive entry outside the target: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteTree(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		try (var walk = Files.walk(path)) {
			for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(entry);
			}
		}
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
