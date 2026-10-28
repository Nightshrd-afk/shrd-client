package dev.shrd.launcher.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

/**
 * The launcher's identity, and deliberately the same identity the in-game client wears:
 * electric purple ({@code #A855F7}) on near-black violet glass. The exact accent values match
 * {@code dev.shrd.client.gui.Theme} in the mod, so the launcher and the mod menu you get after
 * pressing Play look like one product.
 */
public final class Theme {

	public static final Color WINDOW = new Color(0x0A0612);
	public static final Color WINDOW_EDGE = new Color(0x1D1030);
	public static final Color RAIL = new Color(0x0D0818);
	public static final Color TITLE_BAR = new Color(0x0D0818);

	public static final Color ACCENT = new Color(0xA855F7);
	public static final Color ACCENT_ALT = new Color(0xC084FC);
	public static final Color ACCENT_DEEP = new Color(0x7C3AED);
	public static final Color ON_ACCENT = new Color(0x150A24);

	public static final Color PANEL = new Color(0x120A1F);
	public static final Color PANEL_HEADER = new Color(0x1B0F2E);
	public static final Color PANEL_OUTLINE = new Color(0x66A855F7);
	public static final Color ROW = new Color(0x1A1028);
	public static final Color ROW_HOVER = new Color(0x2A1740);
	public static final Color ROW_ENABLED = new Color(0x5B2A9E);
	public static final Color SETTING = new Color(0x150C24);
	public static final Color TRACK = new Color(0x0F0818);

	public static final Color TEXT = new Color(0xEFE7FB);
	public static final Color TEXT_DIM = new Color(0x9E93B8);
	public static final Color TEXT_DISABLED = new Color(0x6B6280);
	public static final Color TEXT_ACCENT = new Color(0xCDA9FF);

	public static final Color GOOD = new Color(0x55E07A);
	public static final Color WARN = new Color(0xF5C542);
	public static final Color BAD = new Color(0xF0564A);

	private static final String UI_FACE = firstAvailable("Segoe UI", "Inter", "Helvetica Neue", "Dialog");
	private static final String MONO_FACE = firstAvailable("Cascadia Mono", "Consolas", "DejaVu Sans Mono", "Monospaced");

	private Theme() {
	}

	private static String firstAvailable(String... candidates) {
		Set<String> installed = new HashSet<>();

		try {
			installed.addAll(java.util.List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
		} catch (Exception ignored) {
			// A headless or font-less environment: fall back to the first candidate.
		}

		for (String candidate : candidates) {
			if (installed.contains(candidate)) {
				return candidate;
			}
		}

		return candidates[candidates.length - 1];
	}

	public static Font font(int size) {
		return new Font(UI_FACE, Font.PLAIN, size);
	}

	public static Font bold(int size) {
		return new Font(UI_FACE, Font.BOLD, size);
	}

	public static Font mono(int size) {
		return new Font(MONO_FACE, Font.PLAIN, size);
	}

	/** Fades towards transparent, for hover fills. */
	public static Color alpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}

	public static Color mix(Color from, Color to, float amount) {
		float delta = Math.max(0f, Math.min(1f, amount));
		return new Color(
			(int) (from.getRed() + (to.getRed() - from.getRed()) * delta),
			(int) (from.getGreen() + (to.getGreen() - from.getGreen()) * delta),
			(int) (from.getBlue() + (to.getBlue() - from.getBlue()) * delta),
			(int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * delta));
	}
}
