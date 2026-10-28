package dev.shrd.client.core.setting;

public class BooleanSetting extends Setting<Boolean> {

	public BooleanSetting(String name, String description, boolean defaultValue) {
		this(name, description, defaultValue, null);
	}

	public BooleanSetting(String name, String description, boolean defaultValue, java.util.function.Consumer<Boolean> onChange) {
		super(name, description, defaultValue, onChange);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public Object serialize() {
		return value;
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Boolean b) {
			set(b);
		}
	}
}
