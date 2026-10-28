package dev.shrd.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.shrd.launcher.launch.Http;
import dev.shrd.launcher.launch.MinecraftMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code mods} folder of an instance: what is in it, what is switched off, and the two things
 * this launcher knows how to install — the Shrd Client build from this project, and Fabric API,
 * without which no Fabric mod (including this client) will load.
 */
public final class ModsManager {

	private static final String DISABLED_SUFFIX = ".disabled";
	private static final String MODRINTH = "https://api.modrinth.com/v2";

	public record Mod(String fileName, Path path, long size, boolean enabled, boolean isShrd) {
		public String displayName() {
			return fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
		}

		public String sizeText() {
			return size >= 1048576 ? String.format("%.1f MB", size / 1048576.0) : String.format("%d KB", Math.max(1, size / 1024));
		}
	}

	private ModsManager() {
	}

	public static List<Mod> list(Instance instance) {
		Path modsDir = instance.modsDir();
		List<Mod> mods = new ArrayList<>();

		if (!Files.isDirectory(modsDir)) {
			return mods;
		}

		try (Stream<Path> files = Files.list(modsDir)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();

				if (!name.endsWith(".jar") && !name.endsWith(".jar" + DISABLED_SUFFIX)) {
					continue;
				}

				try {
					mods.add(new Mod(name, file, Files.size(file), name.endsWith(".jar"), name.startsWith("shrd-client")));
				} catch (IOException ignored) {
					// A file that vanishes mid-listing is not worth failing the whole list over.
				}
			}
		} catch (IOException e) {
			Log.warn("could not read %s: %s", modsDir, e.getMessage());
		}

		mods.sort(Comparator.comparing(Mod::displayName, String.CASE_INSENSITIVE_ORDER));
		return mods;
	}

	/** Renames the jar to {@code .jar.disabled} or back, which is how Fabric ignores a mod. */
	public static Mod toggle(Mod mod) throws IOException {
		if (mod.enabled()) {
			Path target = mod.path().resolveSibling(mod.fileName() + DISABLED_SUFFIX);
			Files.move(mod.path(), target, StandardCopyOption.REPLACE_EXISTING);
			return new Mod(target.getFileName().toString(), target, Files.size(target), false, mod.isShrd());
		}

		String enabledName = mod.fileName().substring(0, mod.fileName().length() - DISABLED_SUFFIX.length());
		Path target = mod.path().resolveSibling(enabledName);
		Files.move(mod.path(), target, StandardCopyOption.REPLACE_EXISTING);
		return new Mod(enabledName, target, Files.size(target), true, mod.isShrd());
	}

	public static void delete(Mod mod) throws IOException {
		Files.deleteIfExists(mod.path());
	}

	/**
	 * The client jar this launcher should install, in the order it makes sense to look:
	 * a jar shipped inside the launcher itself (a downloaded copy of the app carries one, so it
	 * needs no Gradle at all), then this project's {@code build/libs}. The packaged app comes first
	 * so a release never installs a stale local build by accident.
	 */
	public static Path clientBuildJar() {
		Path bundled = bundledClientJar();

		if (bundled != null) {
			return bundled;
		}

		Path root = projectRoot();

		if (root == null) {
			return null;
		}

		return newestJar(root.resolve("build").resolve("libs"));
	}

	/** A client jar that travels with the launcher, e.g. {@code app/mods/shrd-client-1.0.0.jar}. */
	public static Path bundledClientJar() {
		for (Path directory : bundledModDirs()) {
			Path jar = newestJar(directory);

			if (jar != null) {
				return jar;
			}
		}

		return null;
	}

	private static List<Path> bundledModDirs() {
		List<Path> directories = new ArrayList<>();

		try {
			Path source = Path.of(ModsManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
			Path appDir = Files.isDirectory(source) ? source : source.getParent();

			if (appDir != null) {
				// In a jpackage app image this is <app>/mods; in a development run it is the build folder.
				directories.add(appDir.resolve("mods"));
				directories.add(appDir);
			}
		} catch (Exception ignored) {
			// Falls back to the working directory below.
		}

		directories.add(Path.of("mods").toAbsolutePath());
		return directories;
	}

	private static Path newestJar(Path directory) {
		if (directory == null || !Files.isDirectory(directory)) {
			return null;
		}

		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(path -> {
					String name = path.getFileName().toString();
					return name.startsWith("shrd-client") && name.endsWith(".jar") && !name.endsWith("-sources.jar");
				})
				.max(Comparator.comparing(path -> path.getFileName().toString()))
				.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	/** Installs the client jar into the instance. */
	public static Path installClientBuild(Instance instance) throws IOException {
		Path jar = clientBuildJar();

		if (jar == null) {
			throw new IOException("no client jar found — this launcher carries none, and there is no build output to fall back on");
		}

		Files.createDirectories(instance.modsDir());
		Path target = instance.modsDir().resolve(jar.getFileName());
		Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
		Log.info("installed %s into %s", jar.getFileName(), ShrdPaths.pretty(instance.modsDir()));
		return target;
	}

	/**
	 * Downloads Fabric API for the instance's Minecraft version from Modrinth. Picking the newest
	 * matching release beats hard-coding a version that goes stale the moment Fabric ships one.
	 */
	public static Path installFabricApi(Instance instance) throws IOException {
		String url = MODRINTH + "/project/fabric-api/version?game_versions=%5B%22" + instance.minecraftVersion + "%22%5D&loaders=%5B%22fabric%22%5D";
		JsonArray versions = com.google.gson.JsonParser.parseString(Http.getString(url)).getAsJsonArray();

		if (versions.isEmpty()) {
			throw new IOException("Modrinth has no Fabric API release for Minecraft " + instance.minecraftVersion);
		}

		JsonObject version = versions.get(0).getAsJsonObject();
		String versionNumber = MinecraftMeta.string(version, "version_number", "unknown");
		JsonArray files = MinecraftMeta.array(version, "files");
		JsonObject chosen = null;

		for (JsonElement element : files) {
			JsonObject file = element.getAsJsonObject();

			if (file.has("primary") && file.get("primary").getAsBoolean()) {
				chosen = file;
				break;
			}
		}

		if (chosen == null && !files.isEmpty()) {
			chosen = files.get(0).getAsJsonObject();
		}

		if (chosen == null) {
			throw new IOException("Modrinth returned no downloadable file for Fabric API " + versionNumber);
		}

		String fileName = MinecraftMeta.string(chosen, "filename", "fabric-api.jar");
		String downloadUrl = MinecraftMeta.string(chosen, "url", null);

		if (downloadUrl == null) {
			throw new IOException("Fabric API " + versionNumber + " has no download URL");
		}

		Files.createDirectories(instance.modsDir());
		Path target = instance.modsDir().resolve(fileName);
		Http.download(downloadUrl, target);
		Log.info("installed Fabric API %s (%s)", versionNumber, fileName);
		return target;
	}

	/** The root of this client project — the folder that has the mod's {@code build.gradle}. */
	public static Path projectRoot() {
		Path start = null;

		try {
			Path source = Path.of(ModsManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
			start = Files.isDirectory(source) ? source : source.getParent();
		} catch (Exception ignored) {
			start = Path.of(".").toAbsolutePath();
		}

		Path directory = start;

		for (int level = 0; level < 6 && directory != null; level++) {
			if (Files.isRegularFile(directory.resolve("build.gradle")) && Files.isRegularFile(directory.resolve("gradle.properties"))) {
				return directory;
			}

			directory = directory.getParent();
		}

		return null;
	}
}
