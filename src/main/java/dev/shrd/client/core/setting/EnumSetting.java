package dev.shrd.client.core.setting;

/**
 * A setting that cycles through the constants of an enum, e.g. {@code TopLeft -> TopRight -> ...}.
 * Enum constants are serialized by name so configs stay readable.
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {

	private final E[] values;

	public EnumSetting(String name, String description, E defaultValue) {
		super(name, description, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] getValues() {
		return values;
	}

	public void cycle(int direction) {
		int index = value.ordinal() + direction;
		int size = values.length;
		set(values[((index % size) + size) % size]);
	}

	@Override
	public Object serialize() {
		return value.name();
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof String s) {
			for (E candidate : values) {
				if (candidate.name().equalsIgnoreCase(s)) {
					set(candidate);
					return;
				}
			}
		}
	}
}
