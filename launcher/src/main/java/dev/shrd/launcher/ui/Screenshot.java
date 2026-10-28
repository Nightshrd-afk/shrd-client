package dev.shrd.launcher.ui;

import dev.shrd.launcher.LauncherConfig;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Paints a launcher page into a PNG without showing a window. Used by {@code --shot so the theme
 * can be checked the same way the in-game client's UI is checked: by counting the client's exact
 * theme colours on a real render.
 */
public final class Screenshot {

	private Screenshot() {
	}

	public static Path write(LauncherConfig config, String page, Path output, int width, int height) throws Exception {
		LauncherFrame frame = new LauncherFrame(config, page);

		SwingUtilities.invokeAndWait(() -> {
			frame.setSize(width, height);
			// Creates the native peer so the whole tree can lay out and paint, without showing it.
			frame.addNotify();
			frame.validate();
		});

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		Ui.hints(graphics);
		SwingUtilities.invokeAndWait(() -> frame.getRootPane().printAll(graphics));
		graphics.dispose();

		Files.createDirectories(output.toAbsolutePath().getParent());
		ImageIO.write(image, "png", output.toFile());
		return output;
	}
}
