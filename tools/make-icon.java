import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws Shrd Launcher's application icon: the shrd gem — a slender violet leaf on a black tile.
 * <p>
 * The composition is taken from the source artwork pixel by pixel (see the measurements in
 * docs/VERIFICATION.md): the gem is two and a half times taller than it is wide, its waist sits
 * about a third of the way down, the left facet runs from near-white lavender at the top to a
 * mid violet at the point, the right facet is a deep indigo, and a lit ridge runs down the middle.
 * It is drawn rather than scaled from the source because that image is 33x30 pixels; using it
 * directly would give a 33 pixel icon stretched across the taskbar.
 * <p>
 * Three outputs: the 256px PNG jpackage uses, a hand-written multi-size .ico (Windows needs the
 * format and ImageIO cannot write it), and the PNG the running window loads for its own icon.
 * <p>
 * Run: java tools/make-icon.java
 */
public final class MakeIcon {

	/** Sizes Windows asks for, largest first so the .ico list is naturally ordered. */
	private static final int[] SIZES = { 256, 128, 64, 48, 32, 16 };

	private static final Color TILE_TOP = new Color(0x0D0A15);
	private static final Color TILE_BOTTOM = new Color(0x04050A);

	/** The lit left facet: near-white lavender at the shoulder, mid violet at the point. */
	private static final Color LEFT_TOP = new Color(0xE2D8FF);
	private static final Color LEFT_MID = new Color(0xA98FFC);
	private static final Color LEFT_BOTTOM = new Color(0x8E72FA);
	/** The shaded right facet: saturated indigo at the shoulder, deeper at the point. */
	private static final Color RIGHT_TOP = new Color(0x4B33BE);
	private static final Color RIGHT_BOTTOM = new Color(0x392984);

	/** The lit ridge down the centre, and the glow that lifts the gem off the black. */
	private static final Color RIDGE = new Color(0xF6F1FF);
	private static final Color GLOW = new Color(0x8B5CF6);

	public static void main(String[] args) throws Exception {
		List<BufferedImage> images = new ArrayList<>();

		for (int size : SIZES) {
			images.add(draw(size));
		}

		write(new File("launcher/icon.png"), images.get(0));
		write(new File("launcher/src/main/resources/icon.png"), images.get(0));
		writeIco(new File("launcher/icon.ico"), images);
	}

	private static void write(File file, BufferedImage image) throws Exception {
		file.getParentFile().mkdirs();
		ImageIO.write(image, "png", file);
		System.out.println("wrote " + file.getPath() + " (" + file.length() + " bytes, " + image.getWidth() + "px)");
	}

	/**
	 * Geometry of the gem inside a tile of {@code size} pixels. Heights and widths are fractions of
	 * the tile, taken from the source artwork: it spans 10%-80% of the tile's height and 37% of its
	 * width, and its waist is a third of the way down.
	 */
	private static final double TOP = 0.085;
	private static final double BOTTOM = 0.905;
	private static final double HALF_WIDTH = 0.204;
	private static final double WAIST = 0.33;

	/**
	 * At 16 and 24 pixels the tile's padding would leave a gem two pixels wide, so the small sizes
	 * get a tighter margin: same proportions, less air. Windows uses these in the taskbar list and
	 * in title bars, where being recognisable matters more than matching the large icon exactly.
	 */
	private static double margin(int size) {
		return size <= 24 ? 3.0 : size <= 32 ? 1.5 : 1.0;
	}

	private static BufferedImage draw(int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		hints(g);

		float scale = size / 256f;

		// Near-black tile with a faint violet wash, like the launcher window behind it.
		g.setPaint(new GradientPaint(0, 0, TILE_TOP, size * 0.35f, size, TILE_BOTTOM));
		g.fillRoundRect(0, 0, size, size, Math.round(58 * scale), Math.round(58 * scale));

		double centre = size * 0.5;
		double shrink = 1.0 / margin(size);
		double top = size * (0.5 - (0.5 - TOP) * shrink);
		double bottom = size * (0.5 + (BOTTOM - 0.5) * shrink);
		double waistY = top + (bottom - top) * WAIST;
		double shoulder = size * HALF_WIDTH * shrink;

		Path2D gem = new Path2D.Double();
		gem.moveTo(centre, top);
		gem.lineTo(centre - shoulder, waistY);
		gem.lineTo(centre, bottom);
		gem.lineTo(centre + shoulder, waistY);
		gem.closePath();

		// The gem sits on black, so a soft violet halo keeps the silhouette from looking cut out:
		// a few widening rings, each fainter than the last.
		if (size >= 48) {
			for (int ring = 6; ring >= 1; ring--) {
				float thickness = (float) (size * 0.03 * ring);
				Area band = new Area(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(gem));
				band.subtract(new Area(new BasicStroke(thickness * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(gem)));
				g.setColor(new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 7));
				g.fill(band);
			}
		}

		// Left half: the lit facet. Its own top-left highlight is what reads as a cut stone.
		Path2D left = new Path2D.Double();
		left.moveTo(centre, top);
		left.lineTo(centre - shoulder, waistY);
		left.lineTo(centre, bottom);
		left.lineTo(centre, top);
		left.closePath();
		g.setPaint(new LinearGradientPaint(0, (float) top,
			0, (float) bottom,
			new float[] { 0f, 0.34f, 1f },
			new Color[] { LEFT_TOP, LEFT_MID, LEFT_BOTTOM }));
		g.fill(left);

		if (size >= 32) {
			// A brighter wedge inside the upper left facet, angled along its own edge.
			Path2D facet = new Path2D.Double();
			facet.moveTo(centre, top + (waistY - top) * 0.22);
			facet.lineTo(centre - shoulder * 0.62, waistY - (waistY - top) * 0.05);
			facet.lineTo(centre, waistY + (bottom - waistY) * 0.18);
			facet.lineTo(centre, top + (waistY - top) * 0.22);
			facet.closePath();
			g.setColor(new Color(255, 255, 255, 40));
			g.fill(facet);
		}

		// Right half: the shaded facet, a deep indigo that darkens as it drops to the point.
		Path2D right = new Path2D.Double();
		right.moveTo(centre, top);
		right.lineTo(centre + shoulder, waistY);
		right.lineTo(centre, bottom);
		right.lineTo(centre, top);
		right.closePath();
		g.setPaint(new LinearGradientPaint(0, (float) top,
			0, (float) bottom,
			new float[] { 0f, 0.28f, 1f },
			new Color[] { RIGHT_TOP, RIGHT_TOP, RIGHT_BOTTOM }));
		g.fill(right);

		if (size >= 32) {
			// The upper right facet catches a little of the same light, so it is not flat black.
			Path2D facet = new Path2D.Double();
			facet.moveTo(centre, top + (waistY - top) * 0.08);
			facet.lineTo(centre + shoulder * 0.62, waistY - (waistY - top) * 0.14);
			facet.lineTo(centre, waistY - (waistY - top) * 0.06);
			facet.closePath();
			g.setColor(new Color(0xC9BAFF));
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
			g.fill(facet);
			g.setComposite(AlphaComposite.SrcOver);
		}

		if (size >= 32) {
			// The lit ridge down the split, brightest at the top and fading towards the point.
			Path2D ridge = new Path2D.Double();
			double half = Math.max(0.7, size * 0.011);
			ridge.moveTo(centre - half, top);
			ridge.lineTo(centre + half, top);
			ridge.lineTo(centre + half * 0.7, bottom);
			ridge.lineTo(centre - half * 0.7, bottom);
			ridge.closePath();
			g.setPaint(new LinearGradientPaint(0, (float) top, 0, (float) bottom,
				new float[] { 0f, 0.55f, 1f },
				new Color[] { new Color(RIDGE.getRed(), RIDGE.getGreen(), RIDGE.getBlue(), 210),
					new Color(RIDGE.getRed(), RIDGE.getGreen(), RIDGE.getBlue(), 150),
					new Color(RIDGE.getRed(), RIDGE.getGreen(), RIDGE.getBlue(), 55) }));
			g.fill(ridge);

			// Both points carry a sliver of highlight, which is what makes the leaf look sharp.
			g.setColor(new Color(RIDGE.getRed(), RIDGE.getGreen(), RIDGE.getBlue(), 190));
			g.fill(new Ellipse2D.Double(centre - size * 0.012, top - size * 0.006, size * 0.024, size * 0.05));
			g.setColor(new Color(RIDGE.getRed(), RIDGE.getGreen(), RIDGE.getBlue(), 140));
			g.fill(new Ellipse2D.Double(centre - size * 0.009, bottom - size * 0.045, size * 0.018, size * 0.05));
		}

		g.dispose();
		return image;
	}

	private static void hints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
	}

	/** Writes a Windows icon file whose entries are PNG images. */
	private static void writeIco(File file, List<BufferedImage> images) throws Exception {
		List<byte[]> payloads = new ArrayList<>();

		for (BufferedImage image : images) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(image, "png", bytes);
			payloads.add(bytes.toByteArray());
		}

		try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
			writeShort(out, 0);
			writeShort(out, 1);
			writeShort(out, images.size());

			int offset = 6 + 16 * images.size();

			for (int index = 0; index < images.size(); index++) {
				BufferedImage image = images.get(index);
				byte[] payload = payloads.get(index);
				out.writeByte(image.getWidth() >= 256 ? 0 : image.getWidth());
				out.writeByte(image.getHeight() >= 256 ? 0 : image.getHeight());
				out.writeByte(0);
				out.writeByte(0);
				writeShort(out, 1);
				writeShort(out, 32);
				writeInt(out, payload.length);
				writeInt(out, offset);
				offset += payload.length;
			}

			for (byte[] payload : payloads) {
				out.write(payload);
			}
		}

		System.out.println("wrote " + file.getPath() + " (" + file.length() + " bytes, " + SIZES.length + " sizes)");
	}

	private static void writeShort(DataOutputStream out, int value) throws Exception {
		out.writeByte(value & 0xFF);
		out.writeByte((value >> 8) & 0xFF);
	}

	private static void writeInt(DataOutputStream out, int value) throws Exception {
		out.writeByte(value & 0xFF);
		out.writeByte((value >> 8) & 0xFF);
		out.writeByte((value >> 16) & 0xFF);
		out.writeByte((value >> 24) & 0xFF);
	}
}
