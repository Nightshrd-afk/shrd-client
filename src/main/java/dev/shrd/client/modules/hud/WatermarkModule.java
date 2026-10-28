package dev.shrd.client.modules.hud;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.EnumSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The client's signature: {@code shrd} with the client accent, optionally followed by
 * the client version and live FPS. Every other overlay is recognisably the same product.
 */
public class WatermarkModule extends HudModule {

	public enum Style {
		SHRD_ONLY,
		SHRD_VERSION,
		SHRD_VERSION_FPS,
		FULL
	}

	private final EnumSetting<Style> style = add(new EnumSetting<>("Style", "How much the watermark shows.", Style.SHRD_VERSION_FPS));
	private final BooleanSetting uppercase = add(new BooleanSetting("Uppercase", "Draw SHRD instead of shrd.", false));

	public WatermarkModule() {
		super("Watermark", "Shrd Client brand mark shown in the corner of the screen.", Category.HUD, 0.005, 0.008);
	}

	private String text() {
		return cached(this::buildText);
	}

	private String buildText() {
		String brand = uppercase.get() ? "SHRD" : "shrd";

		return switch (style.get()) {
			case SHRD_ONLY -> brand;
			case SHRD_VERSION -> brand + " " + ShrdClient.VERSION;
			case SHRD_VERSION_FPS -> brand + " " + ShrdClient.VERSION + "  " + PlayerUtils.fps() + " fps";
			case FULL -> brand + " " + ShrdClient.VERSION + "  " + PlayerUtils.fps() + " fps  "
				+ (PlayerUtils.ping() < 0 ? "sp" : PlayerUtils.ping() + " ms");
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
		int width = getWidth();
		int height = getHeight();

		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, width, height, backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		// Accent bar on the left edge of the panel.
		graphics.fill(0, 0, 2, height, Theme.ACCENT);

		String text = text();
		String brand = uppercase.get() ? "SHRD" : "shrd";
		Renderer.text(graphics, brand, 5, 3, Theme.ACCENT, hasShadow());

		int brandWidth = Renderer.textWidth(brand);
		String rest = text.substring(brand.length());
		Renderer.text(graphics, rest, 5 + brandWidth, 3, Theme.TEXT, hasShadow());
	}
}
