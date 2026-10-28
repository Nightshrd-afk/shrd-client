package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.CombatTracker;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public class ReachModule extends HudModule {

	private final BooleanSetting showMax = add(new BooleanSetting("Session Max", "Also show the longest hit of this session.", true));
	private final BooleanSetting showSecondsSince = add(new BooleanSetting("Since Last Hit", "Show how long ago the last hit was.", false));

	public ReachModule() {
		super("Reach", "Distance of your most recent attack — a practice tool, not an advantage.", Category.HUD, 0.42, 0.84);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		if (CombatTracker.lastReach() < 0f) {
			return "reach ---";
		}

		StringBuilder builder = new StringBuilder(String.format(Locale.ROOT, "reach %.2f m", CombatTracker.lastReach()));

		if (showMax.get()) {
			builder.append(String.format(Locale.ROOT, "  (max %.2f)", CombatTracker.maxReach()));
		}

		if (showSecondsSince.get()) {
			long since = CombatTracker.millisSinceLastHit();
			builder.append(since < 0 ? "  -" : String.format(Locale.ROOT, "  %.1fs ago", since / 1000.0));
		}

		return builder.toString();
	}

	@Override
	protected int measureWidth() {
		return Math.max(110, Renderer.textWidth(text()) + 8);
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
