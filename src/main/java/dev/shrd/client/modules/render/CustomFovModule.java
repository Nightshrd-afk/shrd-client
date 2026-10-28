package dev.shrd.client.modules.render;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.util.PlayerUtils;

/**
 * Pins the field of view to a fixed value, ignoring sprint and speed effects.
 * Handy for Quake-style aim consistency and for players who dislike speed FOV pumping.
 */
public class CustomFovModule extends Module {

	private final IntSetting fov = add(new IntSetting("FOV", "Field of view to lock in place.", 80, 30, 130));
	private final BooleanSetting keepHands = add(new BooleanSetting("Keep Item Animation", "Leave the held-item FOV untouched.", true));
	private final BooleanSetting restoreOnDisable = add(new BooleanSetting("Restore On Disable", "Put your old FOV back when switched off.", true));

	private int savedFov = -1;

	public CustomFovModule() {
		super("Custom FOV", "Lock the camera to one field of view value.", Category.RENDER);
	}

	@Override
	protected void onEnable() {
		if (!PlayerUtils.inGame()) {
			return;
		}

		savedFov = PlayerUtils.mc().options.fov().get();
	}

	@Override
	public void onTick() {
		if (!PlayerUtils.inGame()) {
			return;
		}

		var options = PlayerUtils.mc().options;

		if (options.fov().get() != fov.get()) {
			options.fov().set(fov.get());
		}

		if (keepHands.get()) {
			// Vanilla derives the held item FOV from the same option; nothing extra to do here,
			// this flag simply documents that behaviour is left untouched.
		}
	}

	@Override
	protected void onDisable() {
		if (restoreOnDisable.get() && savedFov > 0) {
			PlayerUtils.mc().options.fov().set(savedFov);
		}

		savedFov = -1;
	}
}
