package dev.shrd.launcher;

import java.nio.file.Path;

/**
 * One playable setup: a Minecraft version, optionally a mod loader, its own memory and its own
 * game directory. Instances are what the Play button launches.
 */
public final class Instance {

	public String name = "Default";
	public String minecraftVersion = "26.2";
	/** {@code vanilla} or {@code fabric}. */
	public String loader = "fabric";
	/** Empty means "the newest loader for this Minecraft version". */
	public String loaderVersion = "";
	public int memoryMb = 4096;
	public String extraJvmArgs = "";
	public boolean customResolution = false;
	public int width = 1280;
	public int height = 720;
	public long lastPlayed = 0L;

	public Instance() {
	}

	public Instance(String name) {
		this.name = name;
	}

	public Path dir() {
		return ShrdPaths.instance(name);
	}

	public Path modsDir() {
		return dir().resolve("mods");
	}

	public Path gameDir() {
		return dir();
	}

	public Path nativesDir() {
		return dir().resolve("natives").resolve(loaderId());
	}

	/** The version id Minecraft is told it is running, e.g. {@code fabric-loader-0.20.1-26.2}. */
	public String loaderId() {
		if (isFabric() && !loaderVersion.isBlank()) {
			return "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
		}

		return minecraftVersion;
	}

	public boolean isFabric() {
		return "fabric".equalsIgnoreCase(loader);
	}

	public Instance copy() {
		Instance other = new Instance(name);
		other.minecraftVersion = minecraftVersion;
		other.loader = loader;
		other.loaderVersion = loaderVersion;
		other.memoryMb = memoryMb;
		other.extraJvmArgs = extraJvmArgs;
		other.customResolution = customResolution;
		other.width = width;
		other.height = height;
		other.lastPlayed = lastPlayed;
		return other;
	}
}
