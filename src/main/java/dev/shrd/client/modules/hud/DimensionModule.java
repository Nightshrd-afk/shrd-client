package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DimensionModule extends HudModule {

	public DimensionModule() {
		super("Dimension", "Overworld, Nether, End or a modded dimension.", Category.HUD, 0.005, 0.328);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		String dimension = PlayerUtils.dimension();

		return switch (dimension) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "The End";
			case "none" -> "no world";
			default -> dimension;
		};
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

		graphics.fill(0, 0, 2, getHeight(), Theme.ACCENT);
		Renderer.text(graphics, text(), 5, 3, Theme.TEXT, hasShadow());
	}
}
