package dev.shrd.client;

import dev.shrd.client.command.ShrdCommand;
import dev.shrd.client.config.ConfigManager;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.ModuleManager;
import dev.shrd.client.core.setting.KeybindSetting;
import dev.shrd.client.gui.ClickGuiScreen;
import dev.shrd.client.gui.HudEditorScreen;
import dev.shrd.client.gui.ModMenuScreen;
import dev.shrd.client.modules.client.TitleModule;
import dev.shrd.client.modules.hud.*;
import dev.shrd.client.modules.player.*;
import dev.shrd.client.modules.render.*;
import dev.shrd.client.modules.utility.*;
import dev.shrd.client.util.KeyInput;
import dev.shrd.client.util.KeyNames;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Shrd Client — a Dawn-style, server-safe client mod for Minecraft.
 * <p>
 * Everything here is client-side: information overlays, camera conveniences, and quality
 * of life toggles you could reproduce by hand. No module automates combat, movement or
 * interaction, and nothing in this client tries to defeat server-side checks.
 */
public class ShrdClient implements ClientModInitializer {

	public static final String NAME = "Shrd Client";
	public static final String ID = "shrdclient";
	public static final String VERSION = "1.0.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	private static final Identifier GUI_KEY_ID = Identifier.fromNamespaceAndPath(ID, "open_gui");

	private static ShrdClient instance;

	private final ModuleManager modules = new ModuleManager();
	private final ConfigManager config = new ConfigManager();
	private final Set<Integer> heldKeys = new HashSet<>();
	private boolean guiKeyHeld;

	private KeyMapping.Category keyCategory;
	private KeyMapping guiKey;
	private int guiKeyCode = GLFW.GLFW_KEY_RIGHT_SHIFT;
	private long startupTime;
	private boolean modulesRegistered;
	private boolean wasConnected;
	private boolean hudEditorOpen;

	public static ShrdClient get() {
		return instance;
	}

	public ModuleManager getModules() {
		return modules;
	}

	public ConfigManager getConfig() {
		return config;
	}

	public int getGuiKey() {
		return guiKeyCode;
	}

	public void setGuiKey(int guiKeyCode) {
		this.guiKeyCode = guiKeyCode;

		if (guiKey != null) {
			guiKey.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(guiKeyCode));
			KeyMapping.resetMapping();
		}
	}

	/** Milliseconds since client start — the session overlay reads this. */
	public long getSessionMillis() {
		return System.currentTimeMillis() - startupTime;
	}

	public boolean isHudEditorOpen() {
		return hudEditorOpen;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		startupTime = System.currentTimeMillis();

		registerModules();
		registerKeybinds();
		config.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ShrdCommand.register(dispatcher));
		ShrdHudRenderer.register();

		LOGGER.info("{} v{} loaded — {} modules, {} overlays", NAME, VERSION, modules.getModules().size(), modules.getHudModules().size());
	}

	private void registerModules() {
		if (modulesRegistered) {
			return;
		}

		modulesRegistered = true;

		// Overlays
		modules.register(new WatermarkModule());
		modules.register(new FpsModule());
		modules.register(new CpsModule());
		modules.register(new PingModule());
		modules.register(new CoordsModule());
		modules.register(new DirectionModule());
		modules.register(new SpeedModule());
		modules.register(new BiomeModule());
		modules.register(new DimensionModule());
		modules.register(new ServerModule());
		modules.register(new ClockModule());
		modules.register(new SessionModule());
		modules.register(new MemoryModule());
		modules.register(new KeystrokesModule());
		modules.register(new ArmorModule());
		modules.register(new PotionModule());
		modules.register(new DurabilityModule());
		modules.register(new TotemModule());
		modules.register(new ReachModule());
		modules.register(new ComboModule());
		modules.register(new ArrayListModule());

		// Utility
		modules.register(new ZoomModule());
		modules.register(new FreelookModule());
		modules.register(new ToggleSprintModule());
		modules.register(new ToggleSneakModule());
		modules.register(new CopyCoordsModule());

		// Render
		modules.register(new FullbrightModule());
		modules.register(new NoWeatherModule());
		modules.register(new CustomFovModule());

		// Player
		modules.register(new DeathCoordsModule());

		// Client
		modules.register(new TitleModule());
		modules.register(new dev.shrd.client.modules.client.PerformanceModule());

		applyDefaultKeybinds();
		applyDefaultOverlays();
	}

	/** A new install starts with the three overlays most players keep on. */
	private void applyDefaultOverlays() {
		enableByDefault("Watermark");
		enableByDefault("FPS");
		enableByDefault("Coordinates");
	}

	private void enableByDefault(String moduleName) {
		Module module = modules.getByName(moduleName);

		if (module != null) {
			module.setEnabled(true);
		}
	}

	private void applyDefaultKeybinds() {
		bind("Zoom", GLFW.GLFW_KEY_C);
		bind("Freelook", GLFW.GLFW_KEY_LEFT_ALT);
		bind("Toggle Sprint", GLFW.GLFW_KEY_V);
		bind("Toggle Sneak", KeybindSetting.NONE);
		bind("Fullbright", GLFW.GLFW_KEY_G);
	}

	private void bind(String moduleName, int key) {
		Module module = modules.getByName(moduleName);

		if (module != null) {
			module.setKeybind(key);
		}
	}

	private void registerKeybinds() {
		keyCategory = KeyMapping.Category.register(GUI_KEY_ID);
		guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + ID + ".gui",
			com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
			guiKeyCode,
			keyCategory
		));
	}

	private void onEndTick() {
		handleGuiKey();
		handleModuleKeybinds();
		handleConnectionState();

		if (modules.getModules().isEmpty()) {
			return;
		}

		if (isInGame()) {
			modules.tick();
		}
	}

	private void handleGuiKey() {
		var client = net.minecraft.client.Minecraft.getInstance();
		var screen = client.gui.screen();



		// The bound key is polled directly rather than through KeyMapping.consumeClick():
		// polling is identical to how module keybinds work, keeps working while another
		// mod swallows vanilla key handling, and still leaves the binding visible and
		// rebindable in Options -> Controls.
		boolean down = KeyInput.isDown(guiKeyCode);
		boolean mappingFired = guiKey != null && guiKey.consumeClick();
		boolean pressed = (down && !guiKeyHeld) || mappingFired;

		if (pressed) {
			if (screen == null) {
				openModMenu();
			} else if (screen instanceof ModMenuScreen modMenu) {
				modMenu.onClose();
			} else if (screen instanceof ClickGuiScreen panels) {
				panels.onClose();
			}
		}

		guiKeyHeld = down;
	}

	private void handleModuleKeybinds() {
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() != null) {
			heldKeys.clear();
			return;
		}

		for (Module module : modules.getModules()) {
			if (!module.hasKeybind()) {
				continue;
			}

			int key = module.getKeybind();
			boolean down = KeyInput.isDown(key);

			if (module.isHold()) {
				// Hold modules track the key directly instead of latching on press.
				module.setEnabled(down);
				continue;
			}

			if (down && !heldKeys.contains(key)) {
				heldKeys.add(key);
				module.toggle();
			} else if (!down) {
				heldKeys.remove(key);
			}
		}
	}

	private void handleConnectionState() {
		boolean connected = isInGame();

		if (wasConnected && !connected) {
			modules.onDisconnect();
		}

		wasConnected = connected;
	}

	public static boolean isInGame() {
		var client = net.minecraft.client.Minecraft.getInstance();
		return client.player != null && client.level != null;
	}

	/**
	 * Opens the Feather-style mod menu. This is what Right Shift is bound to.
	 */
	public void openModMenu() {
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new ModMenuScreen());
	}

	/** Opens the draggable panel layout, kept for players who prefer it ({@code /shrd panels}). */
	public void openClassicGui() {
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new ClickGuiScreen());
	}

	public void openHudEditor() {
		hudEditorOpen = true;
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new HudEditorScreen());
	}

	public void onHudEditorClosed() {
		hudEditorOpen = false;
		config.save();
	}

	public Module getModule(String name) {
		return modules.getByName(name);
	}

	public int countEnabled() {
		int count = 0;

		for (Module module : modules.getModules()) {
			if (module.isEnabled()) {
				count++;
			}
		}

		return count;
	}

	public int countHudModules() {
		return modules.getHudModules().size();
	}

	/** Names of bound keys, used by {@code /shrd} help output. */
	public String describeKeybinds() {
		StringBuilder builder = new StringBuilder();

		for (Module module : modules.getModules()) {
			if (module.hasKeybind()) {
				builder.append(module.getName()).append("=").append(KeyNames.get(module.getKeybind())).append(" ");
			}
		}

		return builder.toString().trim();
	}

	public Category[] getCategories() {
		return Category.values();
	}
}
