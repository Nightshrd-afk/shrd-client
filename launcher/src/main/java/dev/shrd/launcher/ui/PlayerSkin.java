package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.launch.Http;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player's own skin, drawn as a front-facing figure on the Home hero.
 * <p>
 * A signed-in account carries the skin URL from its Minecraft profile. An offline account has no
 * profile, so the skin is fetched by name from mc-heads.net (a public, keyless mirror of Mojang's
 * textures) and cached on disk — if the name has no skin there, the launcher draws a plain
 * purple figure rather than pretending.
 * <p>
 * The texture is not interpreted beyond the standard layout: a 64x64 sheet, or the older 64x32 one
 * which is mirrored into the modern layout first. Drawing is pixel-nearest so a 64-pixel skin stays
 * crisp at 250 pixels tall.
 */
public final class PlayerSkin {

	/** Skin sheets already read this run, keyed by the account's cache name. */
	private static final Map<String, BufferedImage> MEMORY = new ConcurrentHashMap<>();

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

	// ------------------------------------------------------------------ drawing

	/**
	 * Draws the figure with its feet at {@code y + height}: head, body, arms and legs, front view,
	 * with the skin's own overlay layer (hat and jacket) on top.
	 */
	public static void paint(Graphics2D g, BufferedImage skin, int x, int y, int height) {
		double unit = height / 32.0;
		int left = x;
		int top = y;

		// A soft shadow anchors the figure instead of leaving it floating on the background.
		g.setColor(new Color(0, 0, 0, 90));
		g.fill(new java.awt.geom.Ellipse2D.Double(left + 3 * unit, top + 31.2 * unit, 10 * unit, 1.6 * unit));

		if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) {
			paintPlaceholder(g, left, top, unit);
			return;
		}

		Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		// Front view. Right arm/leg are the player's right, which is the viewer's left.
		part(g, skin, 44, 20, 4, 12, left + 0, top + 8 * unit, unit);        // right arm
		part(g, skin, 36, 52, 4, 12, left + 12 * unit, top + 8 * unit, unit); // left arm
		part(g, skin, 20, 20, 8, 12, left + 4 * unit, top + 8 * unit, unit);  // body
		part(g, skin, 4, 20, 4, 12, left + 4 * unit, top + 20 * unit, unit);  // right leg
		part(g, skin, 20, 52, 4, 12, left + 8 * unit, top + 20 * unit, unit); // left leg
		part(g, skin, 8, 8, 8, 8, left + 4 * unit, top, unit);                // head

		// Overlay layer: hair, hats, jackets, sleeves. Transparent pixels stay transparent.
		g.setComposite(AlphaComposite.SrcOver);
		part(g, skin, 44, 36, 4, 12, left + 0, top + 8 * unit, unit);
		part(g, skin, 52, 52, 4, 12, left + 12 * unit, top + 8 * unit, unit);
		part(g, skin, 20, 36, 8, 12, left + 4 * unit, top + 8 * unit, unit);
		part(g, skin, 4, 36, 4, 12, left + 4 * unit, top + 20 * unit, unit);
		part(g, skin, 4, 52, 4, 12, left + 8 * unit, top + 20 * unit, unit);
		part(g, skin, 40, 8, 8, 8, left + 4 * unit, top, unit);

		if (previous != null) {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
		}
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
			g.setPaint(new java.awt.GradientPaint(x, y, Theme.ACCENT_ALT, x + size, y + size, Theme.ACCENT_DEEP));
			g.fill(clip);
			g.setColor(Theme.ON_ACCENT);
			g.setFont(Theme.bold(Math.max(10, size / 2)));
			String initial = "";
			g.drawString(initial, x + size / 2, y + size / 2);
		}

		g.setClip(previous);
		g.setColor(Theme.alpha(Theme.ACCENT_ALT, 90));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(clip);
	}

	private static void part(Graphics2D g, BufferedImage skin, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
		double targetX, double targetY, double unit) {
		g.drawImage(skin,
			(int) Math.round(targetX), (int) Math.round(targetY),
			(int) Math.round(targetX + sourceWidth * unit), (int) Math.round(targetY + sourceHeight * unit),
			sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, null);
	}

	/** The fallback figure: the launcher's own colours, so a nameless skin still looks deliberate. */
	private static void paintPlaceholder(Graphics2D g, int x, int y, double unit) {
		g.setPaint(new java.awt.GradientPaint(x, y, Theme.ACCENT_ALT, x, (float) (y + 32 * unit), Theme.ACCENT_DEEP));
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
