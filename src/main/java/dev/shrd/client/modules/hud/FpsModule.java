package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FpsModule extends HudModule {

	private final BooleanSetting label = add(new BooleanSetting("Label", "Show the 'FPS' prefix.", true));

	private int smoothedFps = 60;

	public FpsModule() {
		super("FPS", "Frames per second, smoothed slightly so it stays readable.", Category.HUD, 0.005, 0.048);
	}

	@Override
	public void onTick() {
		// Exponential smoothing keeps the number from flickering every frame.
		smoothedFps = (int) Math.round(smoothedFps * 0.85 + PlayerUtils.fps() * 0.15);
	}

	private String text() {
		return label.get() ? smoothedFps + " FPS" : String.valueOf(smoothedFps);
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
