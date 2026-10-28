package dev.shrd.client.core;

import dev.shrd.client.core.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every Shrd Client feature.
 * <p>
 * A module is a named, toggleable, key-bindable unit with a list of {@link Setting}s.
 * HUD modules extend {@link HudModule} instead so they can be positioned and drawn.
 */
public abstract class Module {

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();

	private boolean enabled;
	private int keybind = dev.shrd.client.core.setting.KeybindSetting.NONE;

	/**
	 * Bumped every time any module is switched on or off. Caches on the render path
	 * (the list of enabled overlays) watch this number instead of rebuilding every frame.
	 */
	private static int stateVersion;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public int getKeybind() {
		return keybind;
	}

	public boolean hasKeybind() {
		return keybind != dev.shrd.client.core.setting.KeybindSetting.NONE;
	}

	public void setKeybind(int keybind) {
		this.keybind = keybind;
	}

	public List<Setting<?>> getSettings() {
		return settings;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/**
	 * Hold modules are only active while their key is physically held, instead of
	 * toggling on press. Zoom and Freelook work this way.
	 */
	public boolean isHold() {
		return false;
	}

	public void setEnabled(boolean value) {
		if (this.enabled == value) {
			return;
		}

		this.enabled = value;
		stateVersion++;

		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/** Current value of the shared toggle counter, used to invalidate derived caches. */
	public static int getStateVersion() {
		return stateVersion;
	}

	/** Called once per client tick while the module is enabled. */
	public void onTick() {
	}

	/** Called when the module is switched on. */
	protected void onEnable() {
	}

	/** Called when the module is switched off. */
	protected void onDisable() {
	}

	/** Resets every setting of this module back to its default. */
	public void resetSettings() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
	}
}
