package dev.shrd.launcher.launch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Talks to the two metadata sources a launcher needs: Mojang's version manifest (which versions
 * exist and where each one's JSON lives) and Fabric's meta server (loader versions and a ready
 * made launch profile). Both answers are cached on disk — a version JSON never changes, so it is
 * fetched once.
 */
public final class MinecraftMeta {

	public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
	/** Content-addressed asset storage. Assets are shared by every version that asks for them. */
	public static final String ASSET_OBJECT_URL = "https://resources.download.minecraft.net/";
	public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JsonObject manifest;

	private MinecraftMeta() {
	}

	public record VersionEntry(String id, String type, String url, String releaseTime) {
		public boolean isRelease() {
			return "release".equals(type);
		}
	}

	public static synchronized JsonObject manifest() throws IOException {
		if (manifest == null) {
			manifest = readCached(MANIFEST_URL, ShrdPaths.cache().resolve("mojang-version-manifest.json"), true);
		}

		return manifest;
	}

	public static List<VersionEntry> versions() throws IOException {
		List<VersionEntry> entries = new ArrayList<>();

		for (JsonElement element : manifest().getAsJsonArray("versions")) {
			JsonObject version = element.getAsJsonObject();
			entries.add(new VersionEntry(version.get("id").getAsString(), version.get("type").getAsString(),
				version.get("url").getAsString(), version.has("releaseTime") ? version.get("releaseTime").getAsString() : ""));
		}

		return entries;
	}

	public static List<VersionEntry> releases() throws IOException {
		List<VersionEntry> entries = new ArrayList<>();

		for (VersionEntry entry : versions()) {
			if (entry.isRelease()) {
				entries.add(entry);
			}
		}

		return entries;
	}

	public static String latestRelease() throws IOException {
		JsonObject latest = manifest().getAsJsonObject("latest");
		return latest != null && latest.has("release") ? latest.get("release").getAsString() : "";
	}

	/** The full version JSON for {@code id}, from the on-disk cache when it is already there. */
	public static JsonObject versionJson(String id) throws IOException {
		Path cached = ShrdPaths.versions().resolve(id).resolve(id + ".json");

		if (Files.isRegularFile(cached)) {
			try {
				return parse(Files.readString(cached, StandardCharsets.UTF_8));
			} catch (Exception e) {
				Log.warn("cached %s version JSON is unreadable, refetching (%s)", id, e.getMessage());
			}
		}

		String url = null;

		for (VersionEntry entry : versions()) {
			if (entry.id().equals(id)) {
				url = entry.url();
				break;
			}
		}

		if (url == null) {
			throw new IOException("Minecraft " + id + " is not in Mojang's version manifest");
		}

		JsonObject json = parse(Http.getString(url));
		Files.createDirectories(cached.getParent());
		Files.writeString(cached, GSON.toJson(json), StandardCharsets.UTF_8);
		return json;
	}

	/**
	 * Fabric publishes a complete launch profile per loader version, so a modded instance is the
	 * vanilla JSON plus this. It declares {@code inheritsFrom}, which {@link Resolver} merges.
	 */
	public static JsonObject fabricProfile(String minecraftVersion, String loaderVersion) throws IOException {
		String url = FABRIC_META + "/versions/loader/" + minecraftVersion + "/" + loaderVersion + "/profile/json";
		return parse(Http.getString(url));
	}

	/** The loader profile for an instance, cached under its own version id so it is fetched once. */
	public static JsonObject loaderProfile(String minecraftVersion, String loaderVersion, String loaderId) throws IOException {
		Path cached = ShrdPaths.versions().resolve(loaderId).resolve(loaderId + ".json");

		if (Files.isRegularFile(cached)) {
			try {
				return parse(Files.readString(cached, StandardCharsets.UTF_8));
			} catch (Exception e) {
				Log.warn("cached %s profile is unreadable, refetching (%s)", loaderId, e.getMessage());
			}
		}

		JsonObject profile = fabricProfile(minecraftVersion, loaderVersion);
		Files.createDirectories(cached.getParent());
		Files.writeString(cached, GSON.toJson(profile), StandardCharsets.UTF_8);
		return profile;
	}

	/** Loader versions Fabric offers for a Minecraft version, newest first. */
	public static List<String> fabricLoaderVersions(String minecraftVersion) throws IOException {
		List<String> versions = new ArrayList<>();

		for (JsonElement element : JsonParser.parseString(Http.getString(FABRIC_META + "/versions/loader/" + minecraftVersion)).getAsJsonArray()) {
			JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");

			if (loader != null && loader.has("version")) {
				versions.add(loader.get("version").getAsString());
			}
		}

		versions.sort(Comparator.comparing(MinecraftMeta::versionKey).reversed());
		return versions;
	}

	/** Sortable key for dotted version numbers, so "0.20.1" beats "0.9.9". */
	private static long versionKey(String version) {
		String[] parts = version.split("[^0-9]+");
		long key = 0;

		for (int i = 0; i < 3; i++) {
			long value = 0;

			if (i < parts.length && !parts[i].isEmpty()) {
				try {
					value = Long.parseLong(parts[i]);
				} catch (NumberFormatException ignored) {
					value = 0;
				}
			}

			key = key * 1000 + Math.min(value, 999);
		}

		return key;
	}

	public static JsonObject parse(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	public static JsonObject readCached(String url, Path cache, boolean refreshOnFailure) throws IOException {
		if (Files.isRegularFile(cache)) {
			try {
				return parse(Files.readString(cache, StandardCharsets.UTF_8));
			} catch (Exception e) {
				Log.warn("cached %s is unreadable, refetching", cache.getFileName());
			}
		}

		byte[] bytes = Http.getBytes(url);
		Files.createDirectories(cache.getParent());
		Files.write(cache, bytes);
		return parse(new String(bytes, StandardCharsets.UTF_8));
	}

	/** The Java major version this Minecraft version needs, e.g. 25 for 26.2. */
	public static int requiredJavaMajor(JsonObject versionJson) {
		JsonObject javaVersion = object(versionJson, "javaVersion");
		return javaVersion == null ? 0 : (int) number(javaVersion, "majorVersion", 0L);
	}

	public static JsonObject object(JsonObject parent, String key) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	public static JsonArray array(JsonObject parent, String key) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	public static String string(JsonObject parent, String key, String fallback) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}

	public static long number(JsonObject parent, String key, long fallback) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
	}
}
