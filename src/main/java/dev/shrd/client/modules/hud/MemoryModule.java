package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public class MemoryModule extends HudModule {

	private long usedMb;
	private long maxMb;

	public MemoryModule() {
		super("Memory", "Java heap usage, handy for spotting leak-prone modpacks.", Category.HUD, 0.005, 0.488);
	}

	@Override
	public void onTick() {
		Runtime runtime = Runtime.getRuntime();
		usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
		maxMb = runtime.maxMemory() / 1024L / 1024L;
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		return String.format(Locale.ROOT, "RAM %d / %d MB", usedMb, maxMb);
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

		// Thin usage bar under the text.
		double ratio = maxMb <= 0 ? 0 : Math.min(1.0, usedMb / (double) maxMb);
		Renderer.bar(graphics, 5, getHeight() - 2, getWidth() - 10, 1, ratio, Theme.SLIDER_TRACK, Theme.ACCENT);
	}
}
