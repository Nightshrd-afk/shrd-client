package dev.shrd.client.gui;

/**
 * The single source of truth for Shrd Client's look.
 * <p>
 * Deep black glass with a violet tint and two purples: an electric purple for accents and a
 * light lavender for highlights on top of dark fills. This is the "shrd" identity you see on
 * the watermark, the panels, the ClickGUI and the HUD editor.
 * <p>
 * Everything the client draws reads from here, so a whole-client recolour is one file.
 */
public final class Theme {

	private Theme() {
	}

	/** Primary accent — electric purple. */
	public static final int ACCENT = 0xFFA855F7;
	/** Secondary accent — light lavender, for highlights on dark purple surfaces. */
	public static final int ACCENT_ALT = 0xFFC084FC;
	/** Deep violet used for large "enabled" fills where the bright accent would glare. */
	public static final int ACCENT_DEEP = 0xFF7C3AED;
	/** Text and glyphs drawn on top of an accent fill. */
	public static final int ON_ACCENT = 0xFF150A24;

	public static final int WINDOW_BACKGROUND = 0xE60A0612;
	public static final int PANEL_BACKGROUND = 0xD9120A1F;
	public static final int PANEL_HEADER = 0xF01B0F2E;
	public static final int PANEL_OUTLINE = 0x66A855F7;
	public static final int MODULE_BACKGROUND = 0x4D1A1028;
	public static final int MODULE_HOVER = 0x7A2A1740;
	public static final int MODULE_ENABLED = 0xCC5B2A9E;
	public static final int SETTING_BACKGROUND = 0x8A150C24;
	public static final int SLIDER_TRACK = 0x8A0F0818;

	public static final int TEXT = 0xFFEFE7FB;
	public static final int TEXT_DIM = 0xFF9E93B8;
	public static final int TEXT_DISABLED = 0xFF6B6280;
	public static final int TEXT_ACCENT = 0xFFCDA9FF;

	public static final int SHADOW = 0x80000000;

	public static final int CORNER_RADIUS = 3;

	/** Accent with a custom alpha, handy for translucent highlights. */
	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	public static int lerp(int from, int to, float delta) {
		int a = (int) (((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * delta);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * delta);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * delta);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * delta);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 * Health and durability colouring. Kept as red/amber/green on purpose: those convey state,
	 * so they are deliberately not part of the purple identity.
	 */
	public static int healthColor(float ratio) {
		if (ratio > 0.66f) {
			return 0xFF55E07A;
		}

		if (ratio > 0.33f) {
			return 0xFFF5C542;
		}

		return 0xFFF0564A;
	}
}
