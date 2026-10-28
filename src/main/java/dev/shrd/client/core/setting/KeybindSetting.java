package dev.shrd.client.core.setting;

/**
 * A key binding stored as a GLFW key code. {@link #NONE} means unbound.
 */
public class KeybindSetting extends Setting<Integer> {

	public static final int NONE = -1;

	public KeybindSetting(String name, String description, int defaultKey) {
		super(name, description, defaultKey);
	}

	public boolean isBound() {
		return value != NONE;
	}

	@Override
	public Object serialize() {
		return value;
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Number n) {
			set(n.intValue());
		}
	}
}
