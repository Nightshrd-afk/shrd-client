package dev.shrd.client.util;

import dev.shrd.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small wrapper around {@link GuiGraphicsExtractor} so the ClickGUI and the HUD draw with
 * one consistent look: rounded panels, thin outlines and shadowed text.
 * <p>
 * Rounded corners are drawn with per-row fills rather than a custom render pipeline, which
 * keeps Shrd Client compatible with vanilla and with other mods drawing in the same frame.
 */
public final class Renderer {

	private Renderer() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static int textWidth(String value) {
		return mc().font.width(value);
	}

	public static int textHeight() {
		return mc().font.lineHeight;
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}

		int radius = Math.min(Theme.CORNER_RADIUS, Math.min(width, height) / 2);

		if (radius <= 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}

		// Only the rows within the corner radius are inset. Everything between them spans the
		// full width, so the middle of the panel is one rectangle instead of a fill per row —
		// a 20 pixel tall row drops from 20 fills to 7.
		graphics.fill(x, y + radius, x + width, y + height - radius, color);

		for (int row = 0; row < radius; row++) {
			int inset = insetFor(row, height, radius);
			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);

			int mirrored = height - 1 - row;
			graphics.fill(x + inset, y + mirrored, x + width - inset, y + mirrored + 1, color);
		}
	}

	/** How far a row is inset to fake a rounded corner. */
	private static int insetFor(int row, int height, int radius) {
		if (radius <= 0) {
			return 0;
		}

		int distanceFromEdge = Math.min(row, height - 1 - row);

		if (distanceFromEdge >= radius) {
			return 0;
		}

		double normalized = (radius - distanceFromEdge) / (double) radius;
		double circle = Math.sqrt(Math.max(0.0, 1.0 - normalized * normalized));
		return (int) Math.round(radius - circle * radius);
	}

	/** A rounded panel with a one pixel border — the standard Shrd surface. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int outline) {
		roundedRect(graphics, x, y, width, height, fill);

		if (outline != 0 && (outline >>> 24) != 0) {
			rect(graphics, x, y, width, height, outline);
		}
	}

	/** One pixel rectangular border. */
	public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	public static void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
		graphics.text(mc().font, value, x, y, color, true);
	}

	public static void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color, boolean shadow) {
		graphics.text(mc().font, value, x, y, color, shadow);
	}

	public static void centeredText(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
		text(graphics, value, centerX - textWidth(value) / 2, y, color);
	}

	/** Horizontal progress bar used by the durability and armor overlays. */
	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double progress, int background, int foreground) {
		graphics.fill(x, y, x + width, y + height, background);

		int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * width);

		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + height, foreground);
		}
	}

	/**
	 * Truncates text with an ellipsis so fixed-width widgets never overflow.
	 * <p>
	 * Widths are accumulated one character at a time instead of re-measuring a growing
	 * string for every character. That turns a quadratic walk over an item name into a
	 * single linear pass, at the cost of summing per-character widths rather than measuring
	 * the whole string, which can differ by a pixel on kerning-heavy names.
	 */
	public static String truncate(String value, int maxWidth) {
		if (textWidth(value) <= maxWidth) {
			return value;
		}

		int ellipsis = textWidth("...");
		int used = 0;
		StringBuilder builder = new StringBuilder(value.length());

		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			int characterWidth = textWidth(String.valueOf(character));

			if (used + characterWidth + ellipsis > maxWidth) {
				break;
			}

			builder.append(character);
			used += characterWidth;
		}

		return builder + "...";
	}
}
