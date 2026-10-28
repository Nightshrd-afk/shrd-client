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
		// A screenshot is taken of the launcher the player configured, not of the default palette.
		Theme.apply(config.accentColor);

		// The sign-in card is its own window, not a page: render it on its own so the theme of the
		// login a player actually meets is checkable the same way the pages are.
		if ("login".equals(page)) {
			// The sign-in window is its own window, not a page. It is given a real (never shown)
			// peer here so its nested layouts run exactly as they do when a player sees it.
			dev.shrd.launcher.ui.App app = new dev.shrd.launcher.ui.App(config);
			javax.swing.JFrame window = new javax.swing.JFrame("Sign in");
			window.setUndecorated(true);
			window.setContentPane((java.awt.Container) SignInDialog.panel(app));

			SwingUtilities.invokeAndWait(() -> {
				window.setSize(width, height);
				window.addNotify();
				window.validate();
			});

			java.awt.image.BufferedImage card = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D cardGraphics = card.createGraphics();
			Ui.hints(cardGraphics);
			SwingUtilities.invokeAndWait(() -> window.getRootPane().printAll(cardGraphics));
			cardGraphics.dispose();
			window.dispose();
			Files.createDirectories(output.toAbsolutePath().getParent());
			ImageIO.write(card, "png", output.toFile());
			return output;
		}

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
