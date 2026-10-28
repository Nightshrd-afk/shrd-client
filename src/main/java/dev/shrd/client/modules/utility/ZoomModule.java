package dev.shrd.client.modules.utility;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.DoubleSetting;
import dev.shrd.client.util.PlayerUtils;

/**
 * Hold the bound key to pull the camera in.
 * <p>
 * The zoom is applied through the vanilla FOV option rather than a render mixin: it is
 * exact, survives every graphics change, and eases by stepping the option each tick.
 * The original FOV is restored the moment you let go or switch the module off.
 */
public class ZoomModule extends Module {

	private final DoubleSetting factor = add(new DoubleSetting("Zoom Level", "How many times closer the camera gets.", 4.0, 1.5, 15.0, 1));
	private final BooleanSetting smooth = add(new BooleanSetting("Smooth", "Ease in and out instead of snapping.", true));
	private final BooleanSetting hideHand = add(new BooleanSetting("Hide Hand", "Reserved for the hand-hiding variant of this feature.", false));

	private int savedFov = -1;
	private double currentFov = -1;

	public ZoomModule() {
		super("Zoom", "Hold the key to zoom the camera in, like an optic in your pocket.", Category.UTILITY);
	}

	@Override
	public boolean isHold() {
		return true;
	}

	@Override
	protected void onEnable() {
		if (!PlayerUtils.inGame()) {
			return;
		}

		savedFov = PlayerUtils.mc().options.fov().get();
		currentFov = savedFov;
	}

	@Override
	public void onTick() {
		var options = PlayerUtils.mc().options;

		if (savedFov < 0) {
			savedFov = options.fov().get();
			currentFov = savedFov;
		}

		double target = Math.max(1.0, savedFov / factor.get());
		double step = smooth.get() ? 0.35 : 1.0;

		currentFov += (target - currentFov) * step;

		if (Math.abs(target - currentFov) < 0.35) {
			currentFov = target;
		}

		options.fov().set((int) Math.round(currentFov));
	}

	@Override
	protected void onDisable() {
		if (savedFov < 0) {
			return;
		}

		PlayerUtils.mc().options.fov().set(savedFov);
		savedFov = -1;
		currentFov = -1;
	}

	/** True when the hand-hiding option is on; kept so the flag is not silently unused. */
	public boolean hideHandEnabled() {
		return hideHand.get();
	}
}
