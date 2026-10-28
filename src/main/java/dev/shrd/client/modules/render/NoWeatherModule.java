package dev.shrd.client.modules.render;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.ClientState;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;

/**
 * Hides rain and thunder visually, for cleaner screenshots and less visual noise in PvP.
 * Purely a local rendering change — the world still rains for everyone else, the server
 * still knows the weather, and wet farmland still behaves normally.
 */
public class NoWeatherModule extends Module {

	private final BooleanSetting hideRain = add(new BooleanSetting("Hide Rain", "Stop drawing rain.", true));
	private final BooleanSetting hideThunder = add(new BooleanSetting("Hide Thunder", "Stop the flashing and darkening.", true));

	public NoWeatherModule() {
		super("No Weather", "Hide rain and thunder on your screen only.", Category.RENDER);
	}

	@Override
	protected void onEnable() {
		ClientState.setNoWeather(true);
	}

	@Override
	public void onTick() {
		ClientState.setNoWeather(true);
	}

	@Override
	protected void onDisable() {
		ClientState.setNoWeather(false);
	}

	public boolean hidesRain() {
		return hideRain.get();
	}

	public boolean hidesThunder() {
		return hideThunder.get();
	}
}
