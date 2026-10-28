package dev.shrd.client.modules.hud;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.Renderer;
import dev.shrd.client.util.TimeUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SessionModule extends HudModule {

	public SessionModule() {
		super("Session", "How long this client session has been running.", Category.HUD, 0.005, 0.448);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		return "session " + TimeUtils.formatDuration(ShrdClient.get().getSessionMillis());
	}

	@Override
	protected int measureWidth() {
		return Renderer.textWidth(text()) + 8;
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
		Renderer.text(graphics, text(), 5, 3, Theme.TEXT, hasShadow());
	}
}
