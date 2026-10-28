import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws assets/shrdclient/icon.png — the mark Fabric shows in the mod list.
 * <p>
 * Colours mirror {@code dev.shrd.client.gui.Theme}: near-black violet glass, electric purple
 * accent, lavender highlight. Keep the two in step when the theme changes.
 * <p>
 * Run with: java tools/generate-icon.java (Java 25 single-file source launch).
 */
public final class GenerateIcon {

	private static final int SIZE = 128;

	/** Matches Theme.WINDOW_BACKGROUND / ACCENT / ACCENT_ALT / ACCENT_DEEP / TEXT / TEXT_DIM. */
	private static final Color TILE = new Color(0x0A0612);
	private static final Color ACCENT = new Color(0xA855F7);
	private static final Color ACCENT_ALT = new Color(0xC084FC);
	private static final Color ACCENT_DEEP = new Color(0x7C3AED);
	private static final Color TEXT = new Color(0xEFE7FB);
	private static final Color TEXT_DIM = new Color(0x9E93B8);

	public static void main(String[] args) throws Exception {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Rounded near-black tile with the client's purple gradient edge.
		g.setColor(TILE);
		g.fillRoundRect(0, 0, SIZE, SIZE, 26, 26);
		g.setPaint(new GradientPaint(0, 0, ACCENT_DEEP, SIZE, SIZE, ACCENT_ALT));
		g.setStroke(new BasicStroke(3f));
		g.drawRoundRect(1, 1, SIZE - 3, SIZE - 3, 26, 26);

		// Accent bar, matching the watermark's left edge.
		g.setPaint(new GradientPaint(0, 0, ACCENT, 0, SIZE, ACCENT_DEEP));
		g.fillRoundRect(14, 20, 8, SIZE - 40, 4, 4);

		// "shrd" wordmark.
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
		g.setColor(TEXT);
		g.drawString("s", 34, 84);

		g.setColor(ACCENT);
		g.drawString("h", 60, 84);

		g.setColor(ACCENT_ALT);
		g.drawString("rd", 84, 84);

		// Baseline dots.
		g.setColor(TEXT_DIM);
		for (int i = 0; i < 3; i++) {
			g.fillOval(36 + i * 12, 96, 5, 5);
		}

		g.dispose();

		File output = new File("src/main/resources/assets/shrdclient/icon.png");
		output.getParentFile().mkdirs();
		ImageIO.write(image, "png", output);
		System.out.println("wrote " + output.getPath() + " (" + output.length() + " bytes)");
	}
}
