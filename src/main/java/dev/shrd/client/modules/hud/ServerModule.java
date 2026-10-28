package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ServerModule extends HudModule {

	private final BooleanSetting ip = add(new BooleanSetting("Address", "Show the server address.", true));

	public ServerModule() {
		super("Server", "Which server or world you are playing on.", Category.HUD, 0.005, 0.368);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		if (!ip.get()) {
			return PlayerUtils.mc().isLocalServer() ? "Singleplayer" : "Multiplayer";
		}

		return PlayerUtils.serverLabel();
	}

	@Override
	protected int measureWidth() {
		return Math.max(60, Renderer.textWidth(text()) + 8);
	}

	@Override
	protected int measureHeight() {
		return Renderer.textHeight() + 5;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, getWidth(), getHeight(), backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		graphics.fill(0, 0, 2, getHeight(), Theme.ACCENT_ALT);
		Renderer.text(graphics, Renderer.truncate(text(), getWidth() - 8), 5, 3, Theme.TEXT, hasShadow());
	}
}
