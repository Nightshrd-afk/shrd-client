package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CoordsModule extends HudModule {

	private final BooleanSetting showFacing = add(new BooleanSetting("Facing", "Append the direction you are looking.", true));
	private final BooleanSetting showChunk = add(new BooleanSetting("Chunk", "Append the chunk coordinates.", false));
	private final BooleanSetting decimals = add(new BooleanSetting("Decimals", "Show two decimal places.", false));

	public CoordsModule() {
		super("Coordinates", "Your position in the world.", Category.HUD, 0.005, 0.168);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		StringBuilder builder = new StringBuilder(PlayerUtils.coords(decimals.get()));

		if (showFacing.get()) {
			builder.append("  ").append(PlayerUtils.facingShort());
		}

		if (showChunk.get()) {
			builder.append("  chunk ").append(PlayerUtils.chunkCoords());
		}

		return builder.toString();
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
