package dev.shrd.client.util;

import dev.shrd.client.gui.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Prints Shrd Client messages in the player's own chat, the way most clients do:
 * purely local, never sent to the server.
 */
public final class Chat {

	private static final MutableComponent PREFIX = Component.literal("shrd").withStyle(Style.EMPTY.withColor(Theme.ACCENT & 0xFFFFFF));

	private Chat() {
	}

	public static void info(String message) {
		send(Component.literal(" ").append(Component.literal(message).withStyle(Style.EMPTY.withColor(Theme.TEXT & 0xFFFFFF))));
	}

	public static void warn(String message) {
		send(Component.literal(" ").append(Component.literal(message).withStyle(Style.EMPTY.withColor(0xF5C542))));
	}

	public static void error(String message) {
		send(Component.literal(" ").append(Component.literal(message).withStyle(Style.EMPTY.withColor(0xF0564A))));
	}

	private static void send(Component body) {
		var client = PlayerUtils.mc();

		if (client.gui == null || client.gui.hud == null) {
			return;
		}

		client.gui.hud.getChat().addClientSystemMessage(Component.empty().append(PREFIX).append(Component.literal(" »").withStyle(Style.EMPTY.withColor(0x6E7488))).append(body));
	}
}
