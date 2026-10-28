package dev.shrd.client.modules.render;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.ClientState;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.DoubleSetting;

/**
 * Lifts the minimum ambient brightness so caves, mineshafts and night-time builds stay
 * readable. Implemented as a lightmap blend ({@code Lightmap.getBrightness}) rather than
 * by pushing the gamma option past its limit, so the result is even instead of blown out.
 */
public class FullbrightModule extends Module {

	private final DoubleSetting brightness = add(new DoubleSetting("Brightness", "How bright the darkest areas become.", 1.0, 0.25, 1.0, 2));

	public FullbrightModule() {
		super("Fullbright", "See in the dark with a brighter lightmap.", Category.RENDER);
	}

	@Override
	protected void onEnable() {
		ClientState.setFullbright(true);
		ClientState.setFullbrightStrength(strength());
	}

	@Override
	public void onTick() {
		// Re-asserted every tick so a settings change or reload cannot silently drop it.
		ClientState.setFullbright(true);
		ClientState.setFullbrightStrength(strength());
	}

	@Override
	protected void onDisable() {
		ClientState.setFullbright(false);
	}

	/** Blend strength between vanilla darkness (0.25) and fully lit (1.0). */
	public float strength() {
		return brightness.get().floatValue();
	}
}
