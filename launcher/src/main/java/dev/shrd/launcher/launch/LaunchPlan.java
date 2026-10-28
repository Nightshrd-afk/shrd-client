package dev.shrd.launcher.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of resolving a version: every file that must exist, the classpath, and the exact
 * JVM and game arguments, with all {@code ${placeholders}} already filled in. Nothing here touches
 * the network — {@link Resolver} does the reading, {@link Downloader} and {@link Assets} do the
 * fetching, and this is what both of them work from.
 */
public final class LaunchPlan {

	/** A file the launcher must have on disk before it can start the game. */
	public record Download(Path target, String url, String sha1, long size, String label) {
	}

	/** A legacy {@code natives} artifact that has to be unzipped into the natives directory. */
	public record Extract(Download download, List<String> excludes) {
	}

	public String versionId = "";
	public String minecraftVersion = "";
	public String mainClass = "";
	public String assetIndexId = "";
	public int memoryMb = 4096;
	/** Java major version this Minecraft version needs, e.g. 25 for 26.2. */
	public int requiredJavaMajor = 0;
	public Path nativesDir;
	public Path gameDir;
	public Path assetsDir;
	public Path librariesDir;

	public Download clientJar;
	public Download assetIndex;
	public Download logConfig;

	public final List<Download> libraries = new ArrayList<>();
	public final List<Extract> extractions = new ArrayList<>();
	public final List<Path> classpath = new ArrayList<>();
	public final List<String> jvmArgs = new ArrayList<>();
	public final List<String> gameArgs = new ArrayList<>();

	public List<Download> allDownloads() {
		List<Download> downloads = new ArrayList<>();

		if (clientJar != null) {
			downloads.add(clientJar);
		}

		if (logConfig != null) {
			downloads.add(logConfig);
		}

		downloads.addAll(libraries);
		return downloads;
	}

	public long totalBytes() {
		long total = 0;

		for (Download download : allDownloads()) {
			total += Math.max(0, download.size());
		}

		return total;
	}

	public List<String> command(Path java) {
		List<String> command = new ArrayList<>();
		command.add(java.toString());
		command.add("-Xmx" + memoryMb + "M");
		command.add("-Xms" + Math.min(memoryMb, 1024) + "M");
		command.addAll(jvmArgs);
		command.add(mainClass);
		command.addAll(gameArgs);
		return command;
	}

	public String summary() {
		// Plain ASCII: this line is printed to consoles whose encoding is not guaranteed.
		return String.format("%s - main class %s, %d libraries, %d classpath entries, %d MB heap, Java %s+",
			versionId, mainClass, libraries.size(), classpath.size(), memoryMb,
			requiredJavaMajor == 0 ? "?" : String.valueOf(requiredJavaMajor));
	}
}
