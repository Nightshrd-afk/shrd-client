package dev.shrd.launcher.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The player's background image, read once and cached.
 * <p>
 * Both the window backdrop and the Home banner draw it, and Swing can call paint several times a
 * second, so decoding a photo per paint would be pure waste. The cache key includes the file's
 * modified time, so replacing the image on disk is picked up without a restart.
 */
public final class Backgrounds {

	private static String cachedPath = "";
	private static long cachedStamp = -1;
	private static BufferedImage cached;

	private Backgrounds() {
	}

	/** The image, or {@code null} when none is configured or the file cannot be read. */
	public static BufferedImage of(String configured) {
		if (configured == null || configured.isBlank()) {
			return null;
		}

		Path file = Path.of(configured);

		if (!Files.isRegularFile(file)) {
			return null;
		}

		try {
			long stamp = Files.getLastModifiedTime(file).toMillis();

			if (configured.equals(cachedPath) && stamp == cachedStamp) {
				return cached;
			}

			BufferedImage image;

			try (InputStream stream = Files.newInputStream(file)) {
				image = ImageIO.read(stream);
			}

			cachedPath = configured;
			cachedStamp = stamp;
			cached = image;
			return image;
		} catch (Exception e) {
			return null;
		}
	}

	/** Drops the cached image, so the next paint reads the new one. */
	public static void forget() {
		cachedPath = "";
		cachedStamp = -1;
		cached = null;
	}
}
