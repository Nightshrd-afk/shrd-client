package dev.shrd.client.core;

/**
 * Flags that mixins need to read without holding a reference to a module instance.
 * Modules are the only writers; mixins only read.
 */
public final class ClientState {

	private ClientState() {
	}

	private static volatile float fullbrightStrength = 1.0f;
	private static volatile boolean fullbright;
	private static volatile boolean noWeather;

	public static void setFullbright(boolean value) {
		fullbright = value;
	}

	public static boolean fullbright() {
		return fullbright;
	}

	/** Blend used by the lightmap mixin: how bright dark areas should become (0.25 - 1.0). */
	public static void setFullbrightStrength(float strength) {
		fullbrightStrength = Math.max(0.25f, Math.min(1.0f, strength));
	}

	public static float fullbrightStrength() {
		return fullbrightStrength;
	}

	public static void setNoWeather(boolean value) {
		noWeather = value;
	}

	public static boolean noWeather() {
		return noWeather;
	}
}
