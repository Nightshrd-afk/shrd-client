package dev.shrd.client.util;

import org.lwjgl.glfw.GLFW;

/**
 * Turns GLFW keyboard/mouse codes into something readable for the ClickGUI.
 * Mouse buttons are stored as {@link #MOUSE_OFFSET} + the GLFW mouse button.
 */
public final class KeyNames {

	public static final int NONE = -1;
	public static final int MOUSE_OFFSET = 1000;

	private KeyNames() {
	}

	public static boolean isMouse(int code) {
		return code >= MOUSE_OFFSET;
	}

	/**
	 * Parses a key name for {@code /shrd bind}, e.g. {@code c}, {@code f6}, {@code lshift},
	 * {@code mouse1} or {@code none}.
	 */
	public static int parse(String name) {
		String cleaned = name.trim().toUpperCase(java.util.Locale.ROOT);

		if (cleaned.equals("NONE") || cleaned.equals("UNBOUND")) {
			return NONE;
		}

		if (cleaned.startsWith("MOUSE") || cleaned.startsWith("MB")) {
			try {
				int button = Integer.parseInt(cleaned.replace("MOUSE", "").replace("MB", "").trim());
				return MOUSE_OFFSET + Math.max(0, Math.min(7, button - 1));
			} catch (NumberFormatException e) {
				return NONE;
			}
		}

		if (cleaned.length() == 1) {
			char character = cleaned.charAt(0);

			if (character >= 'A' && character <= 'Z') {
				return GLFW.GLFW_KEY_A + (character - 'A');
			}

			if (character >= '0' && character <= '9') {
				return GLFW.GLFW_KEY_0 + (character - '0');
			}
		}

		if (cleaned.startsWith("F") && cleaned.length() <= 3) {
			try {
				int index = Integer.parseInt(cleaned.substring(1));

				if (index >= 1 && index <= 25) {
					return GLFW.GLFW_KEY_F1 + (index - 1);
				}
			} catch (NumberFormatException ignored) {
				// Fall through to the named-key table below.
			}
		}

		return switch (cleaned) {
			case "SPACE" -> GLFW.GLFW_KEY_SPACE;
			case "TAB" -> GLFW.GLFW_KEY_TAB;
			case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
			case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
			case "LSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
			case "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
			case "LCTRL", "LCONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
			case "RCTRL", "RCONTROL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
			case "LALT" -> GLFW.GLFW_KEY_LEFT_ALT;
			case "RALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
			case "UP" -> GLFW.GLFW_KEY_UP;
			case "DOWN" -> GLFW.GLFW_KEY_DOWN;
			case "LEFT" -> GLFW.GLFW_KEY_LEFT;
			case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
			case "GRAVE", "BACKTICK" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
			case "MINUS" -> GLFW.GLFW_KEY_MINUS;
			case "EQUAL" -> GLFW.GLFW_KEY_EQUAL;
			case "COMMA" -> GLFW.GLFW_KEY_COMMA;
			case "PERIOD" -> GLFW.GLFW_KEY_PERIOD;
			case "SLASH" -> GLFW.GLFW_KEY_SLASH;
			case "SEMICOLON" -> GLFW.GLFW_KEY_SEMICOLON;
			case "BRACKET_LEFT" -> GLFW.GLFW_KEY_LEFT_BRACKET;
			case "BRACKET_RIGHT" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
			case "BACKSLASH" -> GLFW.GLFW_KEY_BACKSLASH;
			default -> NONE;
		};
	}

	public static String get(int code) {
		if (code == NONE) {
			return "NONE";
		}

		if (isMouse(code)) {
			return switch (code - MOUSE_OFFSET) {
				case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE LEFT";
				case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE RIGHT";
				case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE MIDDLE";
				case GLFW.GLFW_MOUSE_BUTTON_4 -> "MOUSE 4";
				case GLFW.GLFW_MOUSE_BUTTON_5 -> "MOUSE 5";
				default -> "MOUSE " + (code - MOUSE_OFFSET + 1);
			};
		}

		String glfwName = GLFW.glfwGetKeyName(code, 0);

		if (glfwName != null) {
			return glfwName.toUpperCase(java.util.Locale.ROOT);
		}

		return switch (code) {
			case GLFW.GLFW_KEY_ESCAPE -> "ESC";
			case GLFW.GLFW_KEY_ENTER -> "ENTER";
			case GLFW.GLFW_KEY_TAB -> "TAB";
			case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
			case GLFW.GLFW_KEY_INSERT -> "INSERT";
			case GLFW.GLFW_KEY_DELETE -> "DELETE";
			case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
			case GLFW.GLFW_KEY_LEFT -> "LEFT";
			case GLFW.GLFW_KEY_DOWN -> "DOWN";
			case GLFW.GLFW_KEY_UP -> "UP";
			case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
			case GLFW.GLFW_KEY_HOME -> "HOME";
			case GLFW.GLFW_KEY_END -> "END";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS LOCK";
			case GLFW.GLFW_KEY_SCROLL_LOCK -> "SCROLL LOCK";
			case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRINT SCREEN";
			case GLFW.GLFW_KEY_PAUSE -> "PAUSE";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
			case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
			case GLFW.GLFW_KEY_LEFT_SUPER -> "LWIN";
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
			case GLFW.GLFW_KEY_RIGHT_SUPER -> "RWIN";
			case GLFW.GLFW_KEY_SPACE -> "SPACE";
			case GLFW.GLFW_KEY_GRAVE_ACCENT -> "GRAVE";
			case GLFW.GLFW_KEY_MINUS -> "MINUS";
			case GLFW.GLFW_KEY_EQUAL -> "EQUAL";
			case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
			case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
			case GLFW.GLFW_KEY_BACKSLASH -> "BACKSLASH";
			case GLFW.GLFW_KEY_SEMICOLON -> "SEMICOLON";
			case GLFW.GLFW_KEY_APOSTROPHE -> "APOSTROPHE";
			case GLFW.GLFW_KEY_COMMA -> "COMMA";
			case GLFW.GLFW_KEY_PERIOD -> "PERIOD";
			case GLFW.GLFW_KEY_SLASH -> "SLASH";
			default -> {
				if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
					yield "F" + (code - GLFW.GLFW_KEY_F1 + 1);
				}

				if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) {
					yield "NUM " + (code - GLFW.GLFW_KEY_KP_0);
				}

				yield "KEY " + code;
			}
		};
	}
}
