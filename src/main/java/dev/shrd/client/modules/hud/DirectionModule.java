package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public class DirectionModule extends HudModule {

	private final BooleanSetting showPitch = add(new BooleanSetting("Pitch", "Also show up/down pitch.", true));
	private final BooleanSetting showYaw = add(new BooleanSetting("Yaw", "Also show the raw yaw angle.", false));

	public DirectionModule() {
		super("Direction", "Which way you are facing, plus yaw and pitch.", Category.HUD, 0.005, 0.208);
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		StringBuilder builder = new StringBuilder(PlayerUtils.facing());

		if (showYaw.get() && PlayerUtils.inGame()) {
			builder.append(String.format(Locale.ROOT, "  yaw %.0f", PlayerUtils.player().getYRot()));
		}

		if (showPitch.get() && PlayerUtils.inGame()) {
			builder.append(String.format(Locale.ROOT, "  pitch %.0f", PlayerUtils.player().getXRot()));
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
