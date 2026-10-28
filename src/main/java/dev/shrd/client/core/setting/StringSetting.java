package dev.shrd.client.core.setting;

public class StringSetting extends Setting<String> {

	private final int maxLength;

	public StringSetting(String name, String description, String defaultValue) {
		this(name, description, defaultValue, 64);
	}

	public StringSetting(String name, String description, String defaultValue, int maxLength) {
		super(name, description, defaultValue);
		this.maxLength = maxLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	public void set(String newValue) {
		super.set(newValue.length() > maxLength ? newValue.substring(0, maxLength) : newValue);
	}

	@Override
	public Object serialize() {
		return value;
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof String s) {
			set(s);
		}
	}
}
