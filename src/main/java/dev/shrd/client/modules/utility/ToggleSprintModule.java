package dev.shrd.client.modules.utility;

import com.mojang.blaze3d.platform.InputConstants;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.KeybindSetting;
import dev.shrd.client.util.KeyInput;
import dev.shrd.client.util.PlayerUtils;
import net.minecraft.client.KeyMapping;

/**
 * Press the sprint key once to keep sprinting, press it again to stop — no more holding
 * Ctrl for an entire game. Uses the key you already have bound in Options.
 */
public class ToggleSprintModule extends Module {

	private final KeybindSetting customKey = add(new KeybindSetting("Custom Key", "Override the sprint key. Leave on NONE to use your Options sprint key.", KeybindSetting.NONE));
	private final BooleanSetting keepSprint = add(new BooleanSetting("Stay Sprinting", "Re-apply the sprint state every tick so it never drops.", true));

	private boolean active;
	private boolean keyWasDown;

	public ToggleSprintModule() {
		super("Toggle Sprint", "Turn sprinting into a toggle with the key you already use.", Category.UTILITY);
	}

	private InputConstants.Key boundKey() {
		if (customKey.isBound()) {
			return InputConstants.Type.KEYSYM.getOrCreate(customKey.get());
		}

		return PlayerUtils.mc().options.keySprint.getDefaultKey();
	}

	@Override
	public void onTick() {
		InputConstants.Key key = boundKey();
		boolean down = KeyInput.isRawDown(key.getValue(), key.getType() == InputConstants.Type.MOUSE);

		if (down && !keyWasDown) {
			active = !active;
			KeyMapping.set(key, active);
		}

		if (active && keepSprint.get()) {
			KeyMapping.set(key, true);
		}

		keyWasDown = down;
	}

	@Override
	protected void onDisable() {
		active = false;
		keyWasDown = false;
		KeyMapping.set(boundKey(), false);
	}

	public boolean isSprintToggled() {
		return active;
	}
}
