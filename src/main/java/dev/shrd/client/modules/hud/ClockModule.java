package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import dev.shrd.client.util.TimeUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;

public class ClockModule extends HudModule {

	private final BooleanSetting seconds = add(new BooleanSetting("Seconds", "Include seconds in the wall clock.", false));
	private final BooleanSetting twelveHour = add(new BooleanSetting("12 Hour", "Use a 12 hour clock.", false));
	private final BooleanSetting gameTime = add(new BooleanSetting("In-game Time", "Also show the Minecraft day time.", true));

	public ClockModule() {
		super("Clock", "Your local time and the in-game time of day.", Category.HUD, 0.005, 0.408);
	}

	private String wallClock() {
		LocalTime now = LocalTime.now();

		if (!twelveHour.get()) {
			return TimeUtils.formatClock(now, seconds.get());
		}

		String formatted = now.format(java.time.format.DateTimeFormatter.ofPattern(seconds.get() ? "hh:mm:ss a" : "hh:mm a"));
		return formatted;
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		StringBuilder builder = new StringBuilder(wallClock());

		if (gameTime.get() && PlayerUtils.level() != null) {
			// 26.2 exposes world clocks; the overworld clock ticks like the old day time.
			long dayTime = PlayerUtils.level().getOverworldClockTime() % 24000L;
			long hours = (dayTime / 1000L + 6L) % 24L;
			long minutes = (dayTime % 1000L) * 60L / 1000L;
			builder.append(String.format(java.util.Locale.ROOT, "  |  %02d:%02d", hours, minutes));
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
