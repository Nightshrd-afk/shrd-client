package dev.shrd.launcher.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.shrd.launcher.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft's assets (sounds, languages, textures) live in one content-addressed store shared by
 * every version: each file is named after its own SHA-1, so two versions asking for the same sound
 * download it once. The version's asset index maps logical names to those hashes.
 */
public final class Assets {

	private Assets() {
	}

	/** Every asset object the version's index asks for, as things to download. */
	public static List<LaunchPlan.Download> objects(LaunchPlan plan) throws IOException {
		if (plan.assetIndex == null || !Files.isRegularFile(plan.assetIndex.target())) {
			return List.of();
		}

		JsonObject index;

		try {
			index = MinecraftMeta.parse(Files.readString(plan.assetIndex.target(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			Log.warn("asset index %s is unreadable: %s", plan.assetIndex.target(), e.getMessage());
			return List.of();
		}

		JsonObject objects = MinecraftMeta.object(index, "objects");

		if (objects == null) {
			return List.of();
		}

		List<LaunchPlan.Download> downloads = new ArrayList<>();
		Path objectsRoot = plan.assetsDir.resolve("objects");

		for (String name : objects.keySet()) {
			JsonObject entry = objects.getAsJsonObject(name);
			JsonElement hashElement = entry.get("hash");

			if (hashElement == null) {
				continue;
			}

			String hash = hashElement.getAsString();

			if (hash.length() < 2) {
				continue;
			}

			String prefix = hash.substring(0, 2);
			Path target = objectsRoot.resolve(prefix).resolve(hash);
			String url = MinecraftMeta.ASSET_OBJECT_URL + prefix + "/" + hash;
			downloads.add(new LaunchPlan.Download(target, url, hash, MinecraftMeta.number(entry, "size", 0L), "assets/" + name));
		}

		return downloads;
	}

	public static String megabytes(long bytes) {
		return String.format("%.1f", bytes / 1048576.0);
	}
}
