package dev.shrd.launcher.update;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.Version;
import dev.shrd.launcher.launch.Http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Keeps the launcher up to date. It fetches a small manifest, compares versions, downloads the
 * published zip, verifies its SHA-256, unpacks it, and then hands the swap to a short script —
 * because a running program cannot overwrite its own files on Windows. The script waits for the
 * launcher to exit, copies the new build over the installation and starts it again.
 */
public final class Updater {

	public interface Listener {
		void stage(String message);

		default void progress(int percent) {
		}
	}

	public static final String DEFAULT_MANIFEST_URL =
		"https://github.com/Nightshrd-afk/shrd-client/releases/latest/download/shrd-launcher-latest.json";

	private Updater() {
	}

	// ------------------------------------------------------------------ checking

	/** Fetches the manifest and returns it only if it is newer than what is running. */
	public static UpdateManifest check(String manifestUrl, Listener listener) throws IOException {
		if (manifestUrl == null || manifestUrl.isBlank()) {
			throw new IOException("no update manifest URL is set");
		}

		if (listener != null) {
			listener.stage("Checking " + Http.shorten(manifestUrl));
		}

		UpdateManifest manifest = UpdateManifest.parse(Http.getString(manifestUrl.trim()));
		Log.info("update check: running %s, published %s", Version.CURRENT, manifest.version.isBlank() ? "?" : manifest.version);
		return Version.isNewer(manifest.version, Version.CURRENT) ? manifest : null;
	}

	// ------------------------------------------------------------------ downloading

	/** The published archive's URL: absolute when the manifest gives one, otherwise manifest-relative. */
	public static String downloadUrl(String manifestUrl, UpdateManifest manifest) throws IOException {
		if (!manifest.url.isBlank()) {
			return manifest.url.trim();
		}

		URI base = URI.create(manifestUrl.trim());
		return base.resolve(manifest.fileName().toString()).toString();
	}

	/** Downloads the update and returns the zip, refusing anything whose hash does not match. */
	public static Path download(String manifestUrl, UpdateManifest manifest, Listener listener) throws IOException {
		String url = downloadUrl(manifestUrl, manifest);
		Path directory = updateDirectory(manifest.version);
		Path zip = directory.resolve(manifest.fileName());
		Files.createDirectories(directory);

		if (Files.isRegularFile(zip) && (manifest.sha256.isBlank() || manifest.sha256.equalsIgnoreCase(sha256(zip)))) {
			Log.info("update %s is already downloaded", manifest.version);
		} else {
			if (listener != null) {
				listener.stage("Downloading Shrd Launcher " + manifest.version + " (" + megabytes(manifest.size) + ")");
			}

			Http.download(url, zip);
		}

		if (!manifest.sha256.isBlank()) {
			String actual = sha256(zip);

			if (!manifest.sha256.equalsIgnoreCase(actual)) {
				Files.deleteIfExists(zip);
				throw new IOException("the downloaded update failed its SHA-256 check (expected " + manifest.sha256 + ", got " + actual + ")");
			}
		}

		return zip;
	}

	/** Unpacks the update and returns the folder that should be copied over the installation. */
	public static Path unpack(Path zip, String version, Listener listener) throws IOException {
		Path directory = updateDirectory(version);
		Path unpacked = directory.resolve("unpacked");

		if (Files.isDirectory(unpacked)) {
			deleteRecursively(unpacked);
		}

		Files.createDirectories(unpacked);

		if (listener != null) {
			listener.stage("Unpacking Shrd Launcher " + version);
		}

		try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;

			while ((entry = input.getNextEntry()) != null) {
				Path target = unpacked.resolve(entry.getName()).normalize();

				if (!target.startsWith(unpacked)) {
					throw new IOException("the update archive contains an unsafe path: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}

				Files.createDirectories(target.getParent());

				try (var output = Files.newOutputStream(target)) {
					input.transferTo(output);
				}
			}
		}

		return applicationRoot(unpacked);
	}

	/** A release zip may hold the app in a single top-level folder; find the folder with the launcher. */
	private static Path applicationRoot(Path unpacked) throws IOException {
		if (Files.isRegularFile(unpacked.resolve(executableName()))) {
			return unpacked;
		}

		try (Stream<Path> entries = Files.list(unpacked)) {
			for (Path candidate : entries.toList()) {
				if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve(executableName()))) {
					return candidate;
				}
			}
		}

		// A jar-only build: the launcher can still copy it, so accept the unpacked folder as-is.
		return unpacked;
	}

	// ------------------------------------------------------------------ installing

	/** Downloads and unpacks an update, ready to be swapped in. */
	public static Path prepare(String manifestUrl, UpdateManifest manifest, Listener listener) throws IOException {
		Path zip = download(manifestUrl, manifest, listener);
		return unpack(zip, manifest.version, listener);
	}

	/**
	 * Writes and starts the swap script and returns immediately: the caller is expected to exit, so
	 * that the script can replace the files it is standing on.
	 */
	public static Path apply(Path sourceRoot, Path installDir) throws IOException {
		// The swap mirrors the update over the install, so a folder that is not an app image — a
		// half-unpacked archive, say — would delete the installation it was meant to replace.
		if (!Files.isRegularFile(sourceRoot.resolve(executableName()))) {
			throw new IOException(sourceRoot + " is not a Shrd Launcher build (no " + executableName() + " in it)");
		}

		Path script = ShrdPaths.updates().resolve("apply-update.bat");
		Files.createDirectories(script.getParent());
		String contents = script(sourceRoot, installDir);
		Files.writeString(script, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		Log.title("applying update from %s", ShrdPaths.pretty(sourceRoot));
		new ProcessBuilder("cmd.exe", "/c", "start", "\"Shrd Launcher update\"", "/min", script.toAbsolutePath().toString()).start();
		return script;
	}

	/** The swap script: wait for this process to go, copy everything over, start it again. */
	static String script(Path sourceRoot, Path installDir) {
		String newline = "\r\n";
		return String.join(newline,
			"@echo off",
			"setlocal",
			"title Shrd Launcher update",
			"set \"SOURCE=" + sourceRoot.toAbsolutePath() + "\"",
			"set \"TARGET=" + installDir.toAbsolutePath() + "\"",
			"set \"EXE=" + executableName() + "\"",
			"echo Applying Shrd Launcher update...",
			"echo Waiting for Shrd Launcher to close.",
			":wait",
			"tasklist /FI \"IMAGENAME eq %EXE%\" /NH 2>NUL | find /I \"%EXE%\" >NUL",
			"if not errorlevel 1 (",
			"  timeout /t 1 /nobreak >NUL",
			"  goto wait",
			")",
			"rem /PURGE mirrors the update over the install, so files a release dropped do not linger.",
			"robocopy \"%SOURCE%\" \"%TARGET%\" /E /PURGE /NFL /NDL /NJH /NJS /R:2 /W:1 >NUL",
			"if errorlevel 8 (",
			"  echo The install folder needs administrator rights to update.",
			"  echo Windows will ask for them now.",
			"  powershell -NoProfile -Command \"Start-Process -FilePath '%~f0' -Verb RunAs\"",
			"  exit /b 0",
			")",
			"echo Starting Shrd Launcher...",
			"start \"\" \"%TARGET%\\%EXE%\"",
			"exit /b 0", "");
	}

	// ------------------------------------------------------------------ environment

	/** The installation folder, or {@code null} when this is not a packaged build. */
	public static Path installDir() {
		try {
			Path source = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
			Path appDir = Files.isDirectory(source) ? source : source.getParent();

			if (appDir == null) {
				return null;
			}

			Path root = appDir.getParent();

			if (root != null && Files.isRegularFile(root.resolve(executableName()))) {
				return root;
			}
		} catch (Exception ignored) {
			// A development run: updates can be downloaded, not installed.
		}

		return null;
	}

	public static boolean canSelfInstall() {
		return installDir() != null;
	}

	public static String executableName() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		return windows ? "Shrd Launcher.exe" : "shrd-launcher";
	}

	private static Path updateDirectory(String version) {
		return ShrdPaths.updates().resolve(version.isBlank() ? "unknown" : version);
	}

	public static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			try (InputStream stream = Files.newInputStream(file); DigestInputStream digestStream = new DigestInputStream(stream, digest)) {
				byte[] buffer = new byte[65536];

				while (digestStream.read(buffer) != -1) {
					// Reading feeds the digest.
				}
			}

			StringBuilder builder = new StringBuilder();

			for (byte value : digest.digest()) {
				builder.append(String.format("%02x", value));
			}

			return builder.toString();
		} catch (Exception e) {
			throw new IOException("could not hash " + file, e);
		}
	}

	public static String megabytes(long bytes) {
		return bytes <= 0 ? "unknown size" : String.format("%.1f MB", bytes / 1048576.0);
	}

	static void deleteRecursively(Path directory) throws IOException {
		List<Path> paths = new ArrayList<>();

		try (Stream<Path> stream = Files.walk(directory)) {
			paths.addAll(stream.sorted(Comparator.reverseOrder()).toList());
		}

		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}
}
