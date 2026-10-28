package dev.shrd.launcher.launch;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Instance;
import dev.shrd.launcher.LauncherConfig;
import dev.shrd.launcher.ShrdPaths;

import java.nio.file.Path;

/** Who is playing, what they are playing, and which Java runs it. */
public final class LaunchContext {

	public static final String LAUNCHER_NAME = "shrd-launcher";
	public static final String LAUNCHER_VERSION = "1.0.0";

	public final Instance instance;
	public final Account account;
	public final LauncherConfig config;
	public final Path java;

	public LaunchContext(Instance instance, Account account, LauncherConfig config, Path java) {
		this.instance = instance;
		this.account = account;
		this.config = config;
		this.java = java;
	}

	public Path gameDir() {
		return instance.gameDir();
	}

	public Path modsDir() {
		return instance.modsDir();
	}

	public Path nativesDir() {
		return instance.nativesDir();
	}

	public Path assetsDir() {
		return ShrdPaths.assets();
	}

	public Path librariesDir() {
		return ShrdPaths.libraries();
	}

	public Path versionsDir() {
		return ShrdPaths.versions();
	}

	public String versionId() {
		return instance.minecraftVersion;
	}

	public String loaderId() {
		return instance.loaderId();
	}

	public int memoryMb() {
		return Math.max(512, instance.memoryMb);
	}

	public boolean customResolution() {
		return instance.customResolution;
	}

	/** Extra JVM flags typed by the user, e.g. {@code -XX:+UseG1GC -Dfoo=bar}. */
	public String extraJvmArgs() {
		String combined = (config.extraJvmArgs == null ? "" : config.extraJvmArgs) + " " + (instance.extraJvmArgs == null ? "" : instance.extraJvmArgs);
		return combined.trim();
	}
}
