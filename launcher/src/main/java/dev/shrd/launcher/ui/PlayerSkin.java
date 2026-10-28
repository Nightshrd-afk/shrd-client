package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.launch.Http;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player's own skin, drawn as a standing figure on the Home hero and on the sign-in card.
 * <p>
 * A signed-in account carries the skin URL from its Minecraft profile. An offline account has no
 * profile, so the skin is fetched by name from mc-heads.net (a public, keyless mirror of Mojang's
 * textures) and cached on disk — if the name has no skin there, the launcher draws a plain purple
 * figure rather than pretending.
 * <p>
 * The figure is a set of six boxes (head, body, two arms, two legs) built from the standard skin
 * layout and turned slightly towards the viewer, so the front, one side and the top of the head are
 * all visible. Each face is lit separately — front full, side in shade, top catching the light — and
 * the whole thing is rendered at three times its display size and scaled down, which is what gives
 * it clean edges instead of the stair-stepped sprite look of a flat front view.
 */
public final class PlayerSkin {

	/** Skin sheets already read this run, keyed by the account's cache name. */
	private static final Map<String, BufferedImage> MEMORY = new ConcurrentHashMap<>();

	/**
	 * Turn towards the viewer, in radians. Negative, so the front and the player's left side are
	 * both visible: the pose every 3D skin preview uses, because a straight-on view hides the depth.
	 */
	private static final double YAW = Math.toRadians(-24);
	/** Look down on the figure very slightly, so the top of the head catches the light. */
	private static final double PITCH = Math.toRadians(7);
	/** Render at this multiple and scale down: the cheap way to antialias a pixel texture. */
	private static final int SUPERSAMPLE = 3;
	private static final double SIDE_LIGHT = 0.78;
	private static final double TOP_LIGHT = 1.10;
	/** How far the dark rim around the silhouette reaches, in device pixels before downsampling. */
	private static final int RIM = 2;
	/** Width of the seam between touching limbs, in skin pixels. */
	private static final double CREASE = 1.5;

	private PlayerSkin() {
	}

	/** Memory-only lookup, safe on the event thread (no network, no disk). */
	public static BufferedImage peek(Account account) {
		return MEMORY.get(key(account));
	}

	/** Reads the cached sheet from disk if it is there, without touching the network. */
	public static BufferedImage cached(Account account) {
		BufferedImage memory = peek(account);

		if (memory != null) {
			return memory;
		}

		Path file = cacheFile(account);

		if (Files.isRegularFile(file)) {
			try {
				BufferedImage image = ImageIO.read(file.toFile());

				if (image != null) {
					BufferedImage normalised = normalise(image);
					MEMORY.put(key(account), normalised);
					return normalised;
				}
			} catch (Exception e) {
				Log.warn("could not read the cached skin for %s: %s", account.name, e.getMessage());
			}
		}

		return null;
	}

	/** Fetches the skin if it is not cached. Blocks, so call it off the event thread. */
	public static BufferedImage load(Account account) {
		BufferedImage cached = cached(account);

		if (cached != null) {
			return cached;
		}

		String url = account.skinUrl != null && !account.skinUrl.isBlank()
			? account.skinUrl
			: "https://mc-heads.net/skin/" + account.name;

		try {
			byte[] bytes = Http.getBytes(url);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

			if (image == null) {
				return null;
			}

			BufferedImage normalised = normalise(image);
			Path file = cacheFile(account);
			Files.createDirectories(file.getParent());
			ImageIO.write(normalised, "png", file.toFile());
			MEMORY.put(key(account), normalised);
			return normalised;
		} catch (Exception e) {
			// A missing skin is normal for an offline name; it is not worth a dialog.
			Log.info("no skin for %s (%s)", account.name, Http.describe(e));
			return null;
		}
	}

	/** Forgets a cached sheet, so a new skin fetched later is not shadowed by the old one. */
	public static void forget(Account account) {
		MEMORY.remove(key(account));
	}

	private static String key(Account account) {
		return (account.online() ? account.uuid() : account.name).toLowerCase(java.util.Locale.ROOT);
	}

	private static Path cacheFile(Account account) {
		String name = key(account).replaceAll("[^a-z0-9_-]", "_");
		return ShrdPaths.skins().resolve(name + ".png");
	}

	/** Old 64x32 skins are mirrored into the 64x64 layout so one drawing path covers both. */
	private static BufferedImage normalise(BufferedImage image) {
		if (image.getWidth() == 64 && image.getHeight() == 64) {
			return image;
		}

		if (image.getWidth() != 64 || image.getHeight() != 32) {
			return image;
		}

		BufferedImage wide = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = wide.createGraphics();
		g.drawImage(image, 0, 0, null);
		// A legacy skin has one arm and one leg; the other two are the same pixels mirrored.
		// Only the fronts are filled — a legacy skin has no overlay layer, and inventing one would
		// paint opaque copies over the limbs.
		drawMirrored(g, image, 44, 20, 4, 12, 36, 52);
		drawMirrored(g, image, 4, 20, 4, 12, 20, 52);
		g.dispose();
		return wide;
	}

	private static void drawMirrored(java.awt.Graphics2D g, BufferedImage source,
		int sourceX, int sourceY, int width, int height, int targetX, int targetY) {
		BufferedImage part = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D partGraphics = part.createGraphics();
		partGraphics.drawImage(source.getSubimage(sourceX, sourceY, width, height), 0, 0, null);
		partGraphics.dispose();

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				g.setColor(new Color(part.getRGB(x, y), true));
				g.fillRect(targetX + (width - 1 - x), targetY + y, 1, 1);
			}
		}
	}

	// ------------------------------------------------------------------ the model

	/** One box of the figure, in skin pixels: feet at y=0, top of the head at y=32. */
	private record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
	}

	/** Where a box's three visible faces sit on the skin sheet, top-left corner of each tile. */
	private record Uv(int frontU, int frontV, int sideU, int sideV, int topU, int topV) {
	}

	/**
	 * A box, its texture tiles, and how strongly each side of its front face is creased — the seam
	 * where it meets the torso or the other leg.
	 */
	private record Part(Box box, Uv base, Uv overlay, int creaseLeft, int creaseRight) {
	}

	/**
	 * The standard character in skin pixels. The player's right arm and leg are on -x (the viewer's
	 * left in a front view); the body is four pixels deep, the head eight.
	 */
	private static final List<Part> PARTS = List.of(
		new Part(new Box(-4, 24, -4, 4, 32, 4), new Uv(8, 8, 16, 8, 8, 0), new Uv(40, 8, 48, 8, 40, 0), 0, 0),
		new Part(new Box(-4, 12, -2, 4, 24, 2), new Uv(20, 20, 28, 20, 20, 16), new Uv(20, 36, 28, 36, 20, 32), 26, 26),
		new Part(new Box(-8, 12, -2, -4, 24, 2), new Uv(44, 20, 48, 20, 44, 16), new Uv(44, 36, 48, 36, 44, 32), 0, 74),
		new Part(new Box(4, 12, -2, 8, 24, 2), new Uv(36, 52, 40, 52, 36, 48), new Uv(52, 52, 56, 52, 52, 48), 74, 0),
		new Part(new Box(-4, 0, -2, 0, 12, 2), new Uv(4, 20, 8, 20, 4, 16), new Uv(4, 36, 8, 36, 4, 32), 0, 86),
		new Part(new Box(0, 0, -2, 4, 12, 2), new Uv(20, 52, 24, 52, 20, 48), new Uv(4, 52, 8, 52, 4, 48), 86, 0)
	);

	/** A textured quad ready to paint: four projected corners, a skin tile and a light level. */
	private static final class Face {
		private final double[] px = new double[4];
		private final double[] py = new double[4];
		private final int u;
		private final int v;
		private final int w;
		private final int h;
		private final double light;
		private final int creaseLeft;
		private final int creaseRight;
		private double depth;

		private Face(int u, int v, int w, int h, double light, int creaseLeft, int creaseRight) {
			this.u = u;
			this.v = v;
			this.w = w;
			this.h = h;
			this.light = light;
			this.creaseLeft = creaseLeft;
			this.creaseRight = creaseRight;
		}
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Draws the figure with its feet at {@code y + height} and its body centred on the
	 * {@code x .. x + 16 units} box callers already reserve for it.
	 */
	public static void paint(Graphics2D g, BufferedImage skin, int x, int y, int height) {
		if (height < 8) {
			return;
		}

		double unit = height / 32.0;

		// A soft contact shadow, so the figure stands on the banner instead of floating over it.
		double shadowWidth = 19 * unit;
		double shadowX = x + 8 * unit - shadowWidth / 2;
		double shadowY = y + height - 1.6 * unit;
		g.setPaint(new RadialGradientPaint(new Point((int) Math.round(shadowX + shadowWidth / 2), (int) Math.round(shadowY)),
			(float) (shadowWidth / 2), new float[] { 0f, 1f },
			new Color[] { new Color(0, 0, 0, 115), new Color(0, 0, 0, 0) }));
		g.fill(new Ellipse2D.Double(shadowX, shadowY, shadowWidth, 3.2 * unit));

		if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 64) {
			paintPlaceholder(g, x, y, unit);
			return;
		}

		List<Face> faces = faces();

		// Projection bounds, so the result can be centred on the box the caller reserved.
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;

		for (Face face : faces) {
			for (int i = 0; i < 4; i++) {
				minX = Math.min(minX, face.px[i]);
				minY = Math.min(minY, face.py[i]);
				maxX = Math.max(maxX, face.px[i]);
				maxY = Math.max(maxY, face.py[i]);
			}
		}

		double scale = unit * SUPERSAMPLE;
		int margin = RIM + 1;
		int width = (int) Math.ceil((maxX - minX + 2 * margin) * scale);
		int heightPx = (int) Math.ceil((maxY - minY + 2 * margin) * scale);

		if (width <= 0 || heightPx <= 0) {
			return;
		}

		BufferedImage canvas = new BufferedImage(width, heightPx, BufferedImage.TYPE_INT_ARGB);
		Graphics2D c = canvas.createGraphics();
		c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		c.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		BufferedImage bright = shade(skin, TOP_LIGHT);
		BufferedImage dim = shade(skin, SIDE_LIGHT);

		for (Face face : faces) {
			double[] px = new double[4];
			double[] py = new double[4];

			for (int i = 0; i < 4; i++) {
				px[i] = (face.px[i] - minX + margin) * scale;
				py[i] = (face.py[i] - minY + margin) * scale;
			}

			// Each face is a parallelogram under an orthographic projection, so an affine transform
			// maps the skin tile onto it exactly — no perspective maths, no per-pixel work.
			AffineTransform mapping = new AffineTransform(
				(px[1] - px[0]) / face.w, (py[1] - py[0]) / face.w,
				(px[3] - px[0]) / face.h, (py[3] - py[0]) / face.h,
				px[0], py[0]);
			c.setTransform(mapping);

			BufferedImage source = face.light > 1 ? bright : (face.light < 1 ? dim : skin);
			c.drawImage(source, 0, 0, face.w, face.h,
				face.u, face.v, face.u + face.w, face.v + face.h, null);

			// A crease down the side of a limb. Limbs touch the torso and each other, and two flat
			// faces of the same colour with no seam between them read as one block; the crease is
			// what turns the arms and legs back into four separate shapes.
			crease(c, face, face.creaseLeft, true);
			crease(c, face, face.creaseRight, false);
		}

		c.dispose();

		// A dark rim under the silhouette: one pixel of contrast is what keeps a pale skin legible
		// on the launcher's near-black banner, and it is cheaper than drawing every edge.
		BufferedImage framed = new BufferedImage(width + 2 * RIM, heightPx + 2 * RIM, BufferedImage.TYPE_INT_ARGB);
		Graphics2D f = framed.createGraphics();
		BufferedImage silhouette = silhouette(canvas);

		for (int dx = -RIM; dx <= RIM; dx++) {
			for (int dy = -RIM; dy <= RIM; dy++) {
				if (dx * dx + dy * dy <= RIM * RIM) {
					f.drawImage(silhouette, RIM + dx, RIM + dy, null);
				}
			}
		}

		f.drawImage(canvas, RIM, RIM, null);
		f.dispose();

		int outWidth = Math.max(1, Math.round(framed.getWidth() / (float) SUPERSAMPLE));
		int outHeight = Math.max(1, Math.round(framed.getHeight() / (float) SUPERSAMPLE));
		BufferedImage figure = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D o = figure.createGraphics();
		o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		o.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		o.setComposite(AlphaComposite.SrcOver);
		o.drawImage(framed, 0, 0, outWidth, outHeight, null);
		o.dispose();

		int drawX = (int) Math.round(x + 8 * unit - (maxX - minX) * unit / 2 - margin * unit);
		int drawY = (int) Math.round(y + height - (maxY - minY) * unit - margin * unit);
		g.drawImage(figure, drawX, drawY, null);
	}

	/** A short dark fade along one edge of a face, drawn in that face's own texture space. */
	private static void crease(Graphics2D c, Face face, int alpha, boolean left) {
		if (alpha <= 0) {
			return;
		}

		double from = left ? 0 : face.w - CREASE;
		float dark = (float) (left ? from : from + CREASE);
		float clear = (float) (left ? from + CREASE : from);
		c.setPaint(new GradientPaint(dark, 0, new Color(0, 0, 0, alpha), clear, 0, new Color(0, 0, 0, 0)));
		c.fill(new java.awt.geom.Rectangle2D.Double(from, 0, CREASE, face.h));
	}

	/** The skin's face on its own, for account chips. */
	public static void paintFace(Graphics2D g, BufferedImage skin, int x, int y, int size) {
		java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Double(x, y, size, size, size * 0.28, size * 0.28);
		java.awt.Shape previous = g.getClip();
		g.setClip(clip);

		if (skin != null && skin.getWidth() >= 64) {
			Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(skin, x, y, x + size, y + size, 8, 8, 16, 16, null);
			g.drawImage(skin, x, y, x + size, y + size, 40, 8, 48, 16, null);

			if (interpolation != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			}
		} else {
			g.setPaint(new GradientPaint(x, y, Theme.ACCENT_ALT, x + size, y + size, Theme.ACCENT_DEEP));
			g.fill(clip);
		}

		g.setClip(previous);
		g.setColor(Theme.alpha(Theme.ACCENT_ALT, 90));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(clip);
	}

	// ------------------------------------------------------------------ geometry

	/** Builds every visible face of every part, ordered far to near so overlaps resolve correctly. */
	private static List<Face> faces() {
		List<Face> faces = new ArrayList<>(PARTS.size() * 6);

		for (Part part : PARTS) {
			add(faces, part.box(), part.base(), false, part.creaseLeft(), part.creaseRight());
			add(faces, part.box(), part.overlay(), true, 0, 0);
		}

		faces.sort(Comparator.comparingDouble(face -> face.depth));
		return faces;
	}

	private static void add(List<Face> faces, Box box, Uv uv, boolean overlay, int creaseLeft, int creaseRight) {
		double dx = box.x1() - box.x0();
		double dy = box.y1() - box.y0();
		double dz = box.z1() - box.z0();

		// The front face, facing +z. Only the part under an overlay is creased: an overlay is drawn
		// on top of it, so a crease painted over that would only smudge whatever covers it.
		quad(faces, uv.frontU(), uv.frontV(), (int) Math.round(dx), (int) Math.round(dy), 1.0, overlay,
			overlay ? 0 : creaseLeft, overlay ? 0 : creaseRight,
			new double[] { box.x0(), box.y1(), box.z1() },
			new double[] { box.x1(), box.y1(), box.z1() },
			new double[] { box.x1(), box.y0(), box.z1() },
			new double[] { box.x0(), box.y0(), box.z1() });

		// The player's left side, facing +x. On the skin sheet a side tile has the figure's front on
		// its right edge, so the tile's left column is the back of the box.
		quad(faces, uv.sideU(), uv.sideV(), (int) Math.round(dz), (int) Math.round(dy), SIDE_LIGHT, overlay, 0, 0,
			new double[] { box.x1(), box.y1(), box.z0() },
			new double[] { box.x1(), box.y1(), box.z1() },
			new double[] { box.x1(), box.y0(), box.z1() },
			new double[] { box.x1(), box.y0(), box.z0() });

		// The top face, seen because the camera looks down a little.
		quad(faces, uv.topU(), uv.topV(), (int) Math.round(dx), (int) Math.round(dz), TOP_LIGHT, overlay, 0, 0,
			new double[] { box.x0(), box.y1(), box.z0() },
			new double[] { box.x1(), box.y1(), box.z0() },
			new double[] { box.x1(), box.y1(), box.z1() },
			new double[] { box.x0(), box.y1(), box.z1() });
	}

	/**
	 * Projects four corners given in texture order (top-left, top-right, bottom-right, bottom-left)
	 * and stores the face. Overlays get a hair more depth than the part under them, so the sort
	 * always paints them after it.
	 */
	private static void quad(List<Face> faces, int u, int v, int w, int h, double light, boolean overlay,
		int creaseLeft, int creaseRight, double[]... corners) {
		Face face = new Face(u, v, w, h, light, creaseLeft, creaseRight);
		double depth = 0;

		for (int i = 0; i < 4; i++) {
			double[] point = project(corners[i][0], corners[i][1], corners[i][2]);
			face.px[i] = point[0];
			face.py[i] = point[1];
			depth += point[2];
		}

		face.depth = depth / 4 + (overlay ? 0.05 : 0);
		faces.add(face);
	}

	/** Orthographic projection: turn around the vertical axis, tilt, then drop the depth. */
	private static double[] project(double x, double y, double z) {
		double yawCos = Math.cos(YAW);
		double yawSin = Math.sin(YAW);
		double pitchCos = Math.cos(PITCH);
		double pitchSin = Math.sin(PITCH);

		double rx = x * yawCos + z * yawSin;
		double rz = -x * yawSin + z * yawCos;
		double ry = y * pitchCos - rz * pitchSin;
		double depth = y * pitchSin + rz * pitchCos;

		return new double[] { rx, -ry, depth };
	}

	/** A copy of the sheet with every colour scaled, keeping the alpha. */
	private static BufferedImage shade(BufferedImage source, double factor) {
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;

				if (alpha == 0) {
					continue;
				}

				int red = Math.min(255, (int) Math.round(((argb >> 16) & 0xFF) * factor));
				int green = Math.min(255, (int) Math.round(((argb >> 8) & 0xFF) * factor));
				int blue = Math.min(255, (int) Math.round((argb & 0xFF) * factor));
				out.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
			}
		}

		return out;
	}

	/** The figure as a flat dark shape, for the rim around the silhouette. */
	private static BufferedImage silhouette(BufferedImage source) {
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;

				if (alpha > 40) {
					out.setRGB(x, y, (Math.min(210, alpha) << 24) | 0x0A0412);
				}
			}
		}

		return out;
	}

	/** The fallback figure: the launcher's own colours, so a nameless skin still looks deliberate. */
	private static void paintPlaceholder(Graphics2D g, int x, int y, double unit) {
		g.setPaint(new GradientPaint(x, y, Theme.ACCENT_ALT, x, (float) (y + 32 * unit), Theme.ACCENT_DEEP));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x + 4 * unit, y, 8 * unit, 8 * unit, 2 * unit, 2 * unit));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x + 4 * unit, y + 8 * unit, 8 * unit, 12 * unit, 1.5 * unit, 1.5 * unit));
		g.setColor(Theme.alpha(Theme.ACCENT, 220));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y + 8 * unit, 4 * unit, 12 * unit, 1.5 * unit, 1.5 * unit));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x + 12 * unit, y + 8 * unit, 4 * unit, 12 * unit, 1.5 * unit, 1.5 * unit));
		g.setColor(Theme.mix(Theme.ACCENT_DEEP, Theme.WINDOW, 0.35f));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x + 4 * unit, y + 20 * unit, 3.6 * unit, 12 * unit, 1 * unit, 1 * unit));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(x + 8.4 * unit, y + 20 * unit, 3.6 * unit, 12 * unit, 1 * unit, 1 * unit));
	}
}
