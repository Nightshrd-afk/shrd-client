package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.CpsTracker;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CpsModule extends HudModule {

	private final BooleanSetting bothButtons = add(new BooleanSetting("Show Right", "Also show the right click counter.", true));

	private final CpsTracker tracker = new CpsTracker();

	public CpsModule() {
		super("CPS", "Clicks per second for the left and right mouse buttons.", Category.HUD, 0.005, 0.088);
	}

	public CpsTracker tracker() {
		return tracker;
	}

	@Override
	public void onTick() {
		tracker.poll(Renderer.mc().getWindow() == null ? 0L : Renderer.mc().getWindow().handle());
	}

	private String text() {
		return bothButtons.get()
			? tracker.left() + " | " + tracker.right() + " CPS"
			: tracker.left() + " CPS";
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
