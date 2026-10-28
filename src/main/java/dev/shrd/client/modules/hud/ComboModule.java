package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.CombatTracker;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ComboModule extends HudModule {

	private final BooleanSetting showBest = add(new BooleanSetting("Session Best", "Also show the best combo of this session.", true));

	public ComboModule() {
		super("Combo", "Consecutive hits landed within three seconds of each other.", Category.HUD, 0.42, 0.78);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		int combo = CombatTracker.combo();

		if (combo <= 0) {
			return "combo -";
		}

		return "combo x" + combo + (showBest.get() ? "  (best x" + CombatTracker.bestCombo() + ")" : "");
	}

	@Override
	protected int measureWidth() {
		return Math.max(96, Renderer.textWidth(text()) + 8);
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

		int combo = CombatTracker.combo();
		int color = combo >= 5 ? Theme.ACCENT_ALT : Theme.TEXT;

		graphics.fill(0, 0, 2, getHeight(), color);
		Renderer.text(graphics, text(), 5, 3, color, hasShadow());
	}
}
