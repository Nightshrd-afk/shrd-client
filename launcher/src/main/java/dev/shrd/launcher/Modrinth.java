package dev.shrd.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shrd.launcher.launch.Http;
import dev.shrd.launcher.launch.MinecraftMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modrinth's public search API, as much of it as the launcher needs: search a project type filtered
 * by the instance's game version and loader, then download the newest matching file into the right
 * folder of that instance.
 * <p>
 * No API key is involved — Modrinth's v2 read endpoints are open. Downloads go to the URLs Modrinth
 * returns (cdn.modrinth.com), straight into the instance, so a player browsing here ends up with the
 * same jars they would have dropped in by hand.
 */
public final class Modrinth {

	/** "any" is the launcher's own value; Modrinth simply gets no facet for it. */
	public static final String ANY = "any";

	public static final String MODS = "mod";
	public static final String RESOURCE_PACKS = "resourcepack";
	/** Modrinth's project type is "shader"; the launcher calls them shader packs, which is what
	 * they are called in game. */
	public static final String SHADER_PACKS = "shader";

	private static final String API = "https://api.modrinth.com/v2";
	private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

	private Modrinth() {
	}

	/** One search result: what the list shows, and the id a version lookup needs. */
	public record Hit(String id, String title, String author, String description, String iconUrl,
		long downloads, long followers, String updated, String projectType) {

		public String downloadsText() {
			return compact(downloads);
		}

		public String followersText() {
			return compact(followers);
		}

		public String updatedText() {
			try {
				return UPDATED.format(Instant.parse(updated));
			} catch (Exception e) {
				return "";
			}
		}
	}

	public record Page(List<Hit> hits, int total) {
	}

	/**
	 * Searches one project type. Every filter that is {@code null} or {@link #ANY} is left out, so
	 * "any version, any loader" really means unfiltered rather than filtered on the literal word.
	 */
	public static Page search(String query, String type, String loader, String gameVersion, String category,
		String index, int limit, int offset) throws IOException {
		StringBuilder url = new StringBuilder(API).append("/search");
		url.append("?query=").append(encode(query == null ? "" : query.trim()));
		url.append("&limit=").append(Math.max(1, Math.min(100, limit)));
		url.append("&offset=").append(Math.max(0, offset));
		url.append("&index=").append(encode(index == null || index.isBlank() ? "relevance" : index));

		List<String> facets = new ArrayList<>();
		facets.add("[\"project_type:" + type + "\"]");

		// Only mods are loaded by a mod loader; a resource or shader pack carrying that facet
		// would filter the list down to nothing.
		if (MODS.equals(type) && loader != null && !loader.isBlank() && !loader.equals(ANY)) {
			facets.add("[\"categories:" + loader.toLowerCase(Locale.ROOT) + "\"]");
		}

		if (gameVersion != null && !gameVersion.isBlank() && !gameVersion.equals(ANY)) {
			facets.add("[\"versions:" + gameVersion + "\"]");
		}

		if (category != null && !category.isBlank() && !category.equals(ANY)) {
			facets.add("[\"categories:" + category.toLowerCase(Locale.ROOT) + "\"]");
		}

		url.append("&facets=").append(encode("[" + String.join(",", facets) + "]"));

		JsonObject root = JsonParser.parseString(Http.getString(url.toString())).getAsJsonObject();
		int total = root.has("total_hits") ? root.get("total_hits").getAsInt() : 0;
		List<Hit> hits = new ArrayList<>();

		for (JsonElement element : MinecraftMeta.array(root, "hits")) {
			JsonObject hit = element.getAsJsonObject();
			hits.add(new Hit(
				MinecraftMeta.string(hit, "project_id", ""),
				MinecraftMeta.string(hit, "title", "unknown"),
				MinecraftMeta.string(hit, "author", ""),
				MinecraftMeta.string(hit, "description", ""),
				MinecraftMeta.string(hit, "icon_url", null),
				hit.has("downloads") ? hit.get("downloads").getAsLong() : 0,
				// Modrinth's search API calls this field "follows"; the list shows it as followers.
				hit.has("follows") ? hit.get("follows").getAsLong() : 0,
				MinecraftMeta.string(hit, "date_modified", ""),
				MinecraftMeta.string(hit, "project_type", type)));
		}

		return new Page(hits, total);
	}

	/**
	 * Downloads a project's newest file that matches the instance. Returns the file written. The
	 * version list comes back newest first, so the first compatible entry is the one a player
	 * pressing Install expects.
	 */
	public static Path install(Instance instance, Hit hit, String type, String loader, String gameVersion) throws IOException {
		StringBuilder url = new StringBuilder(API).append("/project/").append(hit.id()).append("/version");
		List<String> params = new ArrayList<>();

		if (loader != null && !loader.isBlank() && !loader.equals(ANY) && MODS.equals(type)) {
			params.add("loaders=" + encode("[\"" + loader.toLowerCase(Locale.ROOT) + "\"]"));
		}

		if (gameVersion != null && !gameVersion.isBlank() && !gameVersion.equals(ANY)) {
			params.add("game_versions=" + encode("[\"" + gameVersion + "\"]"));
		}

		if (!params.isEmpty()) {
			url.append("?").append(String.join("&", params));
		}

		JsonArray versions = JsonParser.parseString(Http.getString(url.toString())).getAsJsonArray();

		if (versions.isEmpty()) {
			throw new IOException("Modrinth has no " + label(type) + " release of " + hit.title()
				+ " for Minecraft " + gameVersion + (loader == null ? "" : " on " + loader));
		}

		JsonObject version = versions.get(0).getAsJsonObject();
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
			throw new IOException("Modrinth returned no downloadable file for " + hit.title());
		}

		String downloadUrl = MinecraftMeta.string(chosen, "url", null);

		if (downloadUrl == null) {
			throw new IOException(hit.title() + " has no download URL");
		}

		String fileName = MinecraftMeta.string(chosen, "filename", hit.title() + ".jar");
		Path folder = folder(instance, type);
		Files.createDirectories(folder);
		Path target = folder.resolve(fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
		long bytes = Http.download(downloadUrl, target);
		Log.info("installed %s (%s) into %s — %d bytes", hit.title(), fileName, ShrdPaths.pretty(folder), bytes);
		return target;
	}

	/** Where a project type belongs inside an instance. */
	public static Path folder(Instance instance, String type) {
		return switch (type) {
			case RESOURCE_PACKS -> instance.dir().resolve("resourcepacks");
			case SHADER_PACKS -> instance.dir().resolve("shaderpacks");
			default -> instance.modsDir();
		};
	}

	public static String label(String type) {
		return switch (type) {
			case RESOURCE_PACKS -> "resource pack";
			case SHADER_PACKS -> "shader pack";
			default -> "mod";
		};
	}

	/** The icon for a project, cached on disk so a list scroll does not re-download it. */
	public static Path icon(Hit hit) throws IOException {
		if (hit.iconUrl() == null || hit.iconUrl().isBlank()) {
			return null;
		}

		Path folder = ShrdPaths.cache().resolve("modrinth");
		Path file = folder.resolve(hit.id() + ".png");

		if (Files.isRegularFile(file) && Files.size(file) > 0) {
			return file;
		}

		Files.createDirectories(folder);
		Http.download(hit.iconUrl(), file);
		return file;
	}

	private static String encode(String value) {
		return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** 1.2M rather than 1234567: the numbers in a mod list are read, not compared. */
	private static String compact(long value) {
		if (value >= 1_000_000_000L) {
			return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
		}

		if (value >= 1_000_000L) {
			return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
		}

		if (value >= 1_000L) {
			return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
		}

		return Long.toString(value);
	}
}
