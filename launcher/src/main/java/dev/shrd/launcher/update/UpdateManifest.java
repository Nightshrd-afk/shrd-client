package dev.shrd.launcher.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.shrd.launcher.launch.MinecraftMeta;

import java.nio.file.Path;
import java.util.List;

/**
 * What a published update says about itself. One small JSON file is all a release has to publish
 * next to its zip:
 *
 * <pre>
 * {
 *   "version": "1.0.1",
 *   "file": "shrd-launcher-1.0.1-windows-x64.zip",
 *   "url": "https://example.com/shrd-launcher-1.0.1-windows-x64.zip",
 *   "sha256": "…",
 *   "size": 35140459,
 *   "notes": "Fixes …"
 * }
 * </pre>
 *
 * {@code url} may be left out, in which case {@code file} is resolved relative to wherever the
 * manifest was served from — which is what makes a local folder or a plain static host work.
 */
public final class UpdateManifest {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public String version = "";
	public String url = "";
	public String file = "";
	public String sha256 = "";
	public long size = 0L;
	public String notes = "";

	public static UpdateManifest parse(String json) {
		JsonObject object = MinecraftMeta.parse(json);
		UpdateManifest manifest = new UpdateManifest();
		manifest.version = MinecraftMeta.string(object, "version", "");
		manifest.url = MinecraftMeta.string(object, "url", "");
		manifest.file = MinecraftMeta.string(object, "file", "");
		manifest.sha256 = MinecraftMeta.string(object, "sha256", "");
		manifest.size = MinecraftMeta.number(object, "size", 0L);
		manifest.notes = MinecraftMeta.string(object, "notes", "");
		return manifest;
	}

	/** The manifest a release publishes: same fields, written by the packaging script. */
	public static String write(String version, String file, String url, String sha256, long size, String notes) {
		UpdateManifest manifest = new UpdateManifest();
		manifest.version = version;
		manifest.file = file;
		manifest.url = url;
		manifest.sha256 = sha256;
		manifest.size = size;
		manifest.notes = notes;
		return GSON.toJson(manifest);
	}

	/** The archive name to download when no absolute URL was published. */
	public Path fileName() {
		return Path.of(file.isBlank() ? "shrd-launcher-" + version + "-windows-x64.zip" : file).getFileName();
	}

	public boolean downloadable() {
		return !version.isBlank() && (!url.isBlank() || !file.isBlank());
	}

	public List<String> notesLines() {
		if (notes == null || notes.isBlank()) {
			return List.of();
		}

		return List.of(notes.split("\\r?\\n"));
	}

	public String describe() {
		return "Shrd Launcher " + version + (notes == null || notes.isBlank() ? "" : " — " + notes);
	}
}
