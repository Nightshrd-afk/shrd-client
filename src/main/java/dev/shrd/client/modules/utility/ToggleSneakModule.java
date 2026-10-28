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
 * Press the sneak key once to stay sneaking. Handy for bridging and for building along
 * ledge edges without cramping your hand — and it is still just your own key press.
 */
public class ToggleSneakModule extends Module {

	private final KeybindSetting customKey = add(new KeybindSetting("Custom Key", "Override the sneak key. Leave on NONE to use your Options sneak key.", KeybindSetting.NONE));
	private final BooleanSetting releaseOnJump = add(new BooleanSetting("Release On Jump", "Stop sneaking when you jump.", true));

	private boolean active;
	private boolean keyWasDown;

	public ToggleSneakModule() {
		super("Toggle Sneak", "Turn sneaking into a toggle with the key you already use.", Category.UTILITY);
	}

	private InputConstants.Key boundKey() {
		if (customKey.isBound()) {
			return InputConstants.Type.KEYSYM.getOrCreate(customKey.get());
		}

		return PlayerUtils.mc().options.keyShift.getDefaultKey();
	}

	@Override
	public void onTick() {
		InputConstants.Key key = boundKey();
		boolean down = KeyInput.isRawDown(key.getValue(), key.getType() == InputConstants.Type.MOUSE);

		if (down && !keyWasDown) {
			active = !active;
			KeyMapping.set(key, active);
		}

		if (active && releaseOnJump.get() && PlayerUtils.mc().options.keyJump.isDown() && !PlayerUtils.player().onGround()) {
			active = false;
			KeyMapping.set(key, false);
		}

		if (active) {
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

	public boolean isSneakToggled() {
		return active;
	}
}
