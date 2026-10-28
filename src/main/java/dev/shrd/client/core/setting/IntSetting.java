package dev.shrd.client.core.setting;

public class IntSetting extends Setting<Integer> {

	private final int min;
	private final int max;

	public IntSetting(String name, String description, int defaultValue, int min, int max) {
		this(name, description, defaultValue, min, max, null);
	}

	public IntSetting(String name, String description, int defaultValue, int min, int max, java.util.function.Consumer<Integer> onChange) {
		super(name, description, defaultValue, onChange);
		this.min = min;
		this.max = max;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public void setClamped(int newValue) {
		set(Math.max(min, Math.min(max, newValue)));
	}

	@Override
	public void set(Integer newValue) {
		super.set(Math.max(min, Math.min(max, newValue)));
	}

	@Override
	public Object serialize() {
		return value;
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Number n) {
			setClamped(n.intValue());
		}
	}
}
