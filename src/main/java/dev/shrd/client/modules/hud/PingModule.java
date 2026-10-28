package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class PingModule extends HudModule {

	public PingModule() {
		super("Ping", "Server latency from the tab list.", Category.HUD, 0.005, 0.128);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		int ping = PlayerUtils.ping();
		return ping < 0 ? "--- ms" : ping + " ms";
	}

	private int color() {
		int ping = PlayerUtils.ping();

		if (ping < 0) {
			return Theme.TEXT_DIM;
		}

		if (ping < 80) {
			return Theme.healthColor(1f);
		}

		return ping < 200 ? Theme.healthColor(0.5f) : Theme.healthColor(0.1f);
	}

	@Override
	protected int measureWidth() {
		return Renderer.textWidth("9999 ms") + 8;
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

		graphics.fill(0, 0, 2, getHeight(), color());
		Renderer.text(graphics, text(), 5, 3, Theme.TEXT, hasShadow());
	}
}
