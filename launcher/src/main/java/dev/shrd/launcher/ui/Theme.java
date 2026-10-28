package dev.shrd.launcher.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The launcher's identity: an electric accent on near-black glass, with every other surface derived
 * from that one accent so a different colour is a whole theme rather than one highlighted button.
 * <p>
 * The accent is chosen on the Customisation page and stored in the config, so these are mutable
 * statics rather than constants: {@link #apply(String)} recomputes every derived colour, and the
 * window rebuilds itself so nothing is left painted in the old palette. The default
 * ({@code #A855F7}) is the same purple the in-game client wears, so out of the box the launcher and
 * the mod menu still look like one product.
 */
public final class Theme {

	public static final String DEFAULT_ACCENT = "#A855F7";

	/** A named accent offered on the Customisation page. */
	public record Preset(String name, String accent) {
	}

	public static final List<Preset> PRESETS = List.of(
		new Preset("Shrd purple", "#A855F7"),
		new Preset("Violet", "#7C3AED"),
		new Preset("Crimson", "#F43F5E"),
		new Preset("Ember", "#F97316"),
		new Preset("Emerald", "#22C55E"),
		new Preset("Toxic", "#A3E635"),
		new Preset("Cyan", "#06B6D4"),
		new Preset("Ice", "#60A5FA"));

	public static Color WINDOW = new Color(0x0A0612);
	public static Color WINDOW_EDGE = new Color(0x1D1030);
	public static Color RAIL = new Color(0x0D0818);
	public static Color TITLE_BAR = new Color(0x0D0818);

	public static Color ACCENT = new Color(0xA855F7);
	public static Color ACCENT_ALT = new Color(0xC084FC);
	public static Color ACCENT_DEEP = new Color(0x7C3AED);
	public static Color ON_ACCENT = new Color(0x150A24);

	public static Color PANEL = new Color(0x120A1F);
	public static Color PANEL_HEADER = new Color(0x1B0F2E);
	public static Color PANEL_OUTLINE = new Color(0x66A855F7);
	public static Color ROW = new Color(0x1A1028);
	public static Color ROW_HOVER = new Color(0x2A1740);
	public static Color ROW_ENABLED = new Color(0x5B2A9E);
	public static Color SETTING = new Color(0x150C24);
	public static Color TRACK = new Color(0x0F0818);

	public static Color TEXT = new Color(0xEFE7FB);
	public static Color TEXT_DIM = new Color(0x9E93B8);
	public static Color TEXT_DISABLED = new Color(0x6B6280);
	public static Color TEXT_ACCENT = new Color(0xCDA9FF);

	public static Color GOOD = new Color(0x55E07A);
	public static Color WARN = new Color(0xF5C542);
	public static Color BAD = new Color(0xF0564A);

	private static final String UI_FACE = firstAvailable("Segoe UI", "Inter", "Helvetica Neue", "Dialog");
	private static final String MONO_FACE = firstAvailable("Cascadia Mono", "Consolas", "DejaVu Sans Mono", "Monospaced");

	/** The accent everything is derived from, hex, without the hash. */
	private static String accentHex = DEFAULT_ACCENT;

	static {
		apply(DEFAULT_ACCENT);
	}

	private Theme() {
	}

	/**
	 * Switches the palette. Every dark surface is mixed a little towards the accent, which is what
	 * keeps a red theme from looking like the purple one with a red button bolted on.
	 */
	public static void apply(String hex) {
		Color accent = parse(hex, new Color(0xA855F7));
		accentHex = "#" + String.format("%06X", accent.getRGB() & 0xFFFFFF);

		ACCENT = accent;
		ACCENT_ALT = mix(accent, Color.WHITE, 0.28f);
		ACCENT_DEEP = mix(accent, Color.BLACK, 0.32f);
		ON_ACCENT = mix(accent, Color.BLACK, 0.86f);

		WINDOW = mix(new Color(0x0A0612), accent, 0.06f);
		WINDOW_EDGE = mix(new Color(0x1D1030), accent, 0.18f);
		RAIL = mix(new Color(0x0D0818), accent, 0.05f);
		TITLE_BAR = RAIL;

		PANEL = mix(new Color(0x120A1F), accent, 0.08f);
		PANEL_HEADER = mix(new Color(0x1B0F2E), accent, 0.14f);
		PANEL_OUTLINE = alpha(ACCENT, 0x66);
		ROW = mix(new Color(0x1A1028), accent, 0.10f);
		ROW_HOVER = mix(new Color(0x2A1740), accent, 0.18f);
		ROW_ENABLED = mix(accent, new Color(0x140A20), 0.55f);
		SETTING = mix(new Color(0x150C24), accent, 0.07f);
		TRACK = mix(new Color(0x0F0818), accent, 0.05f);

		TEXT = mix(new Color(0xEFE7FB), accent, 0.04f);
		TEXT_DIM = mix(new Color(0x9E93B8), accent, 0.10f);
		TEXT_DISABLED = mix(new Color(0x6B6280), accent, 0.10f);
		TEXT_ACCENT = mix(accent, Color.WHITE, 0.52f);
	}

	/** The accent currently in use, as {@code #RRGGBB}. */
	public static String currentHex() {
		return accentHex;
	}

	/** {@code #RRGGBB}, or {@code fallback} when the string is not a colour. */
	public static Color parse(String hex, Color fallback) {
		if (hex == null) {
			return fallback;
		}

		String cleaned = hex.trim();

		if (cleaned.startsWith("#")) {
			cleaned = cleaned.substring(1);
		}

		if (cleaned.length() != 6 || !cleaned.matches("[0-9a-fA-F]{6}")) {
			return fallback;
		}

		try {
			return new Color(Integer.parseInt(cleaned, 16));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static String hex(Color color) {
		return String.format("#%06X", color.getRGB() & 0xFFFFFF).toUpperCase(Locale.ROOT);
	}

	private static String firstAvailable(String... candidates) {
		Set<String> installed = new HashSet<>();

		try {
			installed.addAll(List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
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
