package dev.shrd.client.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Holds every registered module and dispatches ticks and HUD rendering.
 */
public class ModuleManager {

	private final List<Module> modules = new ArrayList<>();

	private List<Module> hudCache = List.of();
	private int hudCacheVersion = -1;

	public void register(Module module) {
		modules.add(module);
		hudCacheVersion = -1;
	}

	public List<Module> getModules() {
		return modules;
	}

	public List<Module> getModules(Category category) {
		List<Module> result = new ArrayList<>();

		for (Module module : modules) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}

		result.sort(Comparator.comparing(Module::getName));
		return result;
	}

	/**
	 * Every enabled overlay, in registration order.
	 * <p>
	 * This runs once per frame, so the list is only rebuilt when a module has actually been
	 * toggled (or registered) rather than on every call: no per-frame list allocation.
	 */
	public List<Module> getHudModules() {
		if (hudCacheVersion != Module.getStateVersion()) {
			List<Module> result = new ArrayList<>();

			for (Module module : modules) {
				if (module instanceof HudModule && module.isEnabled()) {
					result.add(module);
				}
			}

			hudCache = result;
			hudCacheVersion = Module.getStateVersion();
		}

		return hudCache;
	}

	public Module getByName(String name) {
		for (Module module : modules) {
			if (module.getName().equalsIgnoreCase(name)) {
				return module;
			}
		}

		return null;
	}

	/** Finds the module bound to a GLFW key, if any. */
	public Module getByKeybind(int key) {
		if (key == dev.shrd.client.core.setting.KeybindSetting.NONE) {
			return null;
		}

		for (Module module : modules) {
			if (module.getKeybind() == key) {
				return module;
			}
		}

		return null;
	}

	public void tick() {
		for (Module module : modules) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}

		// Overlay content is now one tick old, so every cached size is stale. Measuring here
		// rather than in the renderer means the font layout happens at 20 Hz, not per frame.
		for (Module module : modules) {
			if (module.isEnabled() && module instanceof HudModule hud) {
				hud.invalidateLayout();
			}
		}
	}

	/** Called when the player disconnects so per-session state does not leak between servers. */
	public void onDisconnect() {
		for (Module module : modules) {
			if (module instanceof SessionAware sessionAware) {
				sessionAware.onSessionEnd();
			}
		}
	}

	/** Modules implementing this get told when the client leaves a world. */
	public interface SessionAware {
		void onSessionEnd();
	}
}
