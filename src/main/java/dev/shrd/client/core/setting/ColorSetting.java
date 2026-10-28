package dev.shrd.client.core.setting;

/**
 * An ARGB colour setting. Stored as an int so it maps directly onto
 * {@code DrawContext} colour arguments.
 */
public class ColorSetting extends Setting<Integer> {

	public ColorSetting(String name, String description, int defaultArgb) {
		super(name, description, defaultArgb);
	}

	public int argb() {
		return value;
	}

	public float alpha() {
		return ((value >> 24) & 0xFF) / 255f;
	}

	public int rgb() {
		return value & 0x00FFFFFF;
	}

	/** {@code #AARRGGBB}, used by the config file. */
	public String hex() {
		return String.format(java.util.Locale.ROOT, "#%08X", value);
	}

	public void setFromHex(String hex) {
		String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;

		try {
			set((int) Long.parseLong(cleaned, 16));
		} catch (NumberFormatException ignored) {
			// Leave the current colour untouched when the user types nonsense.
		}
	}

	@Override
	public Object serialize() {
		return hex();
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof String s) {
			setFromHex(s);
		} else if (raw instanceof Number n) {
			set(n.intValue());
		}
	}
}
