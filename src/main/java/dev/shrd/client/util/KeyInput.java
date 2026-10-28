package dev.shrd.client.util;

import dev.shrd.client.core.setting.KeybindSetting;
import org.lwjgl.glfw.GLFW;

/** Direct GLFW polling, used for hold modules and action keys. */
public final class KeyInput {

	private KeyInput() {
	}

	public static long handle() {
		var window = PlayerUtils.mc().getWindow();
		return window == null ? 0L : window.handle();
	}

	public static boolean isDown(int keybindCode) {
		if (keybindCode == KeybindSetting.NONE) {
			return false;
		}

		long handle = handle();

		if (handle == 0L) {
			return false;
		}

		if (KeyNames.isMouse(keybindCode)) {
			return GLFW.glfwGetMouseButton(handle, keybindCode - KeyNames.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
		}

		return GLFW.glfwGetKey(handle, keybindCode) == GLFW.GLFW_PRESS;
	}

	/** Raw GLFW key or mouse code, without the mouse offset used by keybinds. */
	public static boolean isRawDown(int glfwCode, boolean mouse) {
		long handle = handle();

		if (handle == 0L) {
			return false;
		}

		return mouse
			? GLFW.glfwGetMouseButton(handle, glfwCode) == GLFW.GLFW_PRESS
			: GLFW.glfwGetKey(handle, glfwCode) == GLFW.GLFW_PRESS;
	}
}
