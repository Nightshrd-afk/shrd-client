package dev.shrd.client.modules.client;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.StringSetting;
import dev.shrd.client.util.PlayerUtils;

/**
 * Puts Shrd Client in the window title so the client is identifiable while tabbing
 * through windows, and can optionally show the server you are connected to.
 */
public class TitleModule extends Module {

	private final StringSetting suffix = add(new StringSetting("Title", "Text shown after the game name.", " - Shrd Client " + ShrdClient.VERSION));
	private final BooleanSetting showServer = add(new BooleanSetting("Show Server", "Append the server address.", false));

	private int tickCounter;

	public TitleModule() {
		super("Window Title", "Brands the Minecraft window title.", Category.CLIENT);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	public void onTick() {
		// Refreshed twice a second: cheap, and it always wins over vanilla's own updates.
		if (++tickCounter % 10 == 0) {
			apply();
		}
	}

	private void apply() {
		var window = PlayerUtils.mc().getWindow();

		if (window == null) {
			return;
		}

		String server = showServer.get() && PlayerUtils.inGame() ? " | " + PlayerUtils.serverLabel() : "";
		window.setTitle("Minecraft " + PlayerUtils.mc().getLaunchedVersion() + suffix.get() + server);
	}

	@Override
	protected void onDisable() {
		PlayerUtils.mc().updateTitle();
	}
}
