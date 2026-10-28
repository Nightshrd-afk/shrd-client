package dev.shrd.client.core.setting;

/**
 * Base class for every value a module can expose in the ClickGUI.
 * Settings own their value, know how to reset themselves and carry a display name.
 */
public abstract class Setting<T> {

	/**
	 * Bumped whenever any setting anywhere changes value. Deriving code (overlay text,
	 * measured sizes) watches this instead of being told about every individual setting.
	 */
	private static int version;

	private final String name;
	private final String description;
	protected final T defaultValue;
	protected T value;
	private final java.util.function.Consumer<T> onChange;

	protected Setting(String name, String description, T defaultValue) {
		this(name, description, defaultValue, null);
	}

	protected Setting(String name, String description, T defaultValue, java.util.function.Consumer<T> onChange) {
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
		this.onChange = onChange;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T get() {
		return value;
	}

	public void set(T newValue) {
		this.value = newValue;
		version++;

		if (onChange != null) {
			onChange.accept(newValue);
		}
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	/** Current value of the shared settings counter. */
	public static int getVersion() {
		return version;
	}

	public void reset() {
		set(defaultValue);
	}

	/** Value as it should be written to the config file. */
	public abstract Object serialize();

	/** Reads a value back from the config file. Invalid input is ignored. */
	public abstract void deserialize(Object raw);
}
