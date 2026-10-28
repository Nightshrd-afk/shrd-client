package dev.shrd.client.core.setting;

public class DoubleSetting extends Setting<Double> {

	private final double min;
	private final double max;
	private final int decimals;

	public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
		this(name, description, defaultValue, min, max, 2);
	}

	public DoubleSetting(String name, String description, double defaultValue, double min, double max, int decimals) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.decimals = decimals;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public int getDecimals() {
		return decimals;
	}

	public void setClamped(double newValue) {
		set(Math.max(min, Math.min(max, newValue)));
	}

	@Override
	public void set(Double newValue) {
		super.set(Math.max(min, Math.min(max, newValue)));
	}

	/** Human readable value, e.g. {@code 1.25} or {@code 60}. */
	public String display() {
		if (decimals <= 0) {
			return String.valueOf(Math.round(value));
		}

		return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
	}

	@Override
	public Object serialize() {
		return value;
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Number n) {
			setClamped(n.doubleValue());
		}
	}
}
