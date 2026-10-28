package dev.shrd.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists everything the user configured to {@code config/shrd-client/config.json}:
 * module toggles, keybinds, every setting value, HUD element positions and the
 * ClickGUI's own preferences.
 */
public class ConfigManager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CONFIG_VERSION = 1;

	private final Path configFile;

	public ConfigManager() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("shrd-client");
		this.configFile = directory.resolve("config.json");
	}

	public Path getConfigFile() {
		return configFile;
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		root.addProperty("guiKey", ShrdClient.get().getGuiKey());

		JsonObject modules = new JsonObject();

		for (Module module : ShrdClient.get().getModules().getModules()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", module.isEnabled());
			entry.addProperty("keybind", module.getKeybind());

			JsonObject settings = new JsonObject();

			for (Setting<?> setting : module.getSettings()) {
				settings.add(setting.getName(), GSON.toJsonTree(setting.serialize()));
			}

			entry.add("settings", settings);

			if (module instanceof HudModule hud) {
				JsonObject position = new JsonObject();
				position.addProperty("x", hud.getX());
				position.addProperty("y", hud.getY());
				entry.add("position", position);
			}

			modules.add(module.getName(), entry);
		}

		root.add("modules", modules);

		try {
			Files.createDirectories(configFile.getParent());
			Files.writeString(configFile, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ShrdClient.LOGGER.error("Could not save Shrd Client config", e);
		}
	}

	public void load() {
		if (!Files.exists(configFile)) {
			return;
		}

		try {
			String json = Files.readString(configFile, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();

			if (root.has("guiKey")) {
				ShrdClient.get().setGuiKey(root.get("guiKey").getAsInt());
			}

			JsonObject modules = root.getAsJsonObject("modules");

			if (modules == null) {
				return;
			}

			for (Module module : ShrdClient.get().getModules().getModules()) {
				if (!modules.has(module.getName())) {
					continue;
				}

				JsonObject entry = modules.getAsJsonObject(module.getName());
				loadModule(module, entry);
			}
		} catch (Exception e) {
			ShrdClient.LOGGER.error("Could not read Shrd Client config — starting from defaults", e);
		}
	}

	private void loadModule(Module module, JsonObject entry) {
		if (entry.has("keybind")) {
			module.setKeybind(entry.get("keybind").getAsInt());
		}

		if (entry.has("settings")) {
			JsonObject settings = entry.getAsJsonObject("settings");

			for (Setting<?> setting : module.getSettings()) {
				JsonElement value = settings.get(setting.getName());

				if (value != null && !value.isJsonNull()) {
					try {
						setting.deserialize(GSON.fromJson(value, Object.class));
					} catch (Exception e) {
						ShrdClient.LOGGER.warn("Ignoring bad value for {}.{}", module.getName(), setting.getName());
					}
				}
			}
		}

		if (module instanceof HudModule hud && entry.has("position")) {
			JsonObject position = entry.getAsJsonObject("position");
			hud.setPosition(position.get("x").getAsDouble(), position.get("y").getAsDouble());
		}

		// Applied last: enabling early would run onEnable() with default settings.
		if (entry.has("enabled")) {
			module.setEnabled(entry.get("enabled").getAsBoolean());
		}
	}
}
