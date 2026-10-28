package dev.shrd.client.modules.utility;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.EnumSetting;
import dev.shrd.client.core.setting.KeybindSetting;
import dev.shrd.client.util.Chat;
import dev.shrd.client.util.KeyInput;
import dev.shrd.client.util.PlayerUtils;
import org.lwjgl.glfw.GLFW;

/**
 * One key press puts your position on the clipboard, ready to paste anywhere: chat,
 * Discord, a bug report. Add the dimension and biome if you want a full breadcrumb.
 */
public class CopyCoordsModule extends Module {

	public enum Format {
		PLAIN,
		SLASH_TP,
		FULL
	}

	private final KeybindSetting copyKey = add(new KeybindSetting("Copy Key", "Press this to copy your position.", GLFW.GLFW_KEY_F6));
	private final EnumSetting<Format> format = add(new EnumSetting<>("Format", "How the position is written.", Format.PLAIN));
	private final BooleanSetting notify = add(new BooleanSetting("Notify", "Print a chat confirmation.", true));

	private boolean keyWasDown;

	public CopyCoordsModule() {
		super("Copy Coords", "Press a key to copy your coordinates to the clipboard.", Category.UTILITY);
	}

	@Override
	public void onTick() {
		boolean down = KeyInput.isDown(copyKey.get());

		if (down && !keyWasDown && PlayerUtils.inGame()) {
			String text = buildText();
			PlayerUtils.mc().keyboardHandler.setClipboard(text);

			if (notify.get()) {
				Chat.info("copied: " + text);
			}
		}

		keyWasDown = down;
	}

	private String buildText() {
		String coords = PlayerUtils.coords(false);

		return switch (format.get()) {
			case PLAIN -> coords;
			case SLASH_TP -> "/tp @s " + coords;
			case FULL -> coords + "  [" + PlayerUtils.dimension() + " | " + PlayerUtils.biome() + "]";
		};
	}

	/** Exposed so the {@code /shrd coords} command can reuse the same formatting. */
	public String currentText() {
		return buildText();
	}
}
