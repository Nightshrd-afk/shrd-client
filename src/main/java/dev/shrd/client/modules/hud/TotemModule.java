package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class TotemModule extends HudModule {

	private final IntSetting lowThreshold = add(new IntSetting("Low Warning", "Highlight the counter at or below this many totems.", 1, 0, 10));

	public TotemModule() {
		super("Totems", "How many totems of undying you are carrying.", Category.HUD, 0.42, 0.9);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		int count = PlayerUtils.totemCount();
		return "Totems: " + count + (count <= lowThreshold.get() ? "  !" : "");
	}

	@Override
	protected int measureWidth() {
		return Renderer.textWidth("Totems: 99  !") + 8;
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

		int count = PlayerUtils.totemCount();
		int color = count <= lowThreshold.get() ? Theme.healthColor(0.1f) : Theme.TEXT;

		graphics.fill(0, 0, 2, getHeight(), count <= lowThreshold.get() ? Theme.healthColor(0.1f) : Theme.ACCENT);
		Renderer.text(graphics, text(), 5, 3, color, hasShadow());
	}
}
