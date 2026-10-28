package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.EnumSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public class SpeedModule extends HudModule {

	public enum Unit {
		BLOCKS_PER_SECOND,
		KILOMETRES_PER_HOUR
	}

	private final EnumSetting<Unit> unit = add(new EnumSetting<>("Unit", "How speed is displayed.", Unit.BLOCKS_PER_SECOND));
	private final BooleanSetting vertical = add(new BooleanSetting("Vertical", "Also show vertical speed.", false));

	public SpeedModule() {
		super("Speed", "How fast you are moving, useful for elytra and ice travel.", Category.HUD, 0.005, 0.248);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		double horizontal = PlayerUtils.horizontalSpeed();

		if (unit.get() == Unit.KILOMETRES_PER_HOUR) {
			horizontal *= 3.6;
		}

		String suffix = unit.get() == Unit.KILOMETRES_PER_HOUR ? " km/h" : " b/s";
		StringBuilder builder = new StringBuilder(String.format(Locale.ROOT, "%.2f%s", horizontal, suffix));

		if (vertical.get()) {
			builder.append(String.format(Locale.ROOT, "  v %.2f", PlayerUtils.verticalSpeed()));
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

		graphics.fill(0, 0, 2, getHeight(), Theme.ACCENT_ALT);
		Renderer.text(graphics, text(), 5, 3, Theme.TEXT, hasShadow());
	}
}
