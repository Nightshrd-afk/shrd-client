package dev.shrd.launcher.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * A colour wheel: hue around the rim, saturation towards the middle, with a brightness value applied
 * on top.
 * <p>
 * The disc itself is only the hue/saturation field — the same field at full brightness — cached once
 * per size and cheap to draw every repaint; brightness is one translucent black disc over it, which
 * is exactly how HSV works ({@code rgb(v) = rgb(1) * v}). A brightness bar next to it is what makes
 * dark colours reachable, since the disc alone stops at full value.
 */
public final class ColorWheel extends JComponent {

	/** Rendered this much larger and scaled down, so the rim is smooth rather than stepped. */
	private static final int SUPERSAMPLE = 2;

	private final int diameter;
	private BufferedImage disc;
	private int discSize = -1;
	private float hue;
	private float saturation;
	private float value = 1f;
	private Consumer<Color> onChange = color -> {
	};

	public ColorWheel(int diameter) {
		this.diameter = diameter;
		setPreferredSize(new Dimension(diameter, diameter));
		setMinimumSize(new Dimension(diameter, diameter));
		setMaximumSize(new Dimension(diameter, diameter));
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Drag around the wheel to pick a hue and how strong it is");

		MouseAdapter picker = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				pick(event);
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				pick(event);
			}
		};
		addMouseListener(picker);
		addMouseMotionListener(picker);
	}

	/** Notified on every change while dragging. */
	public void onChange(Consumer<Color> handler) {
		this.onChange = handler;
	}

	public Color color() {
		return Color.getHSBColor(hue, saturation, value);
	}

	/** The hue/saturation pair at full brightness, for the brightness bar's gradient. */
	public Color fullBrightness() {
		return getHSB(hue, saturation, 1f);
	}

	public float value() {
		return value;
	}

	public void setValue(float newValue) {
		value = clamp(newValue);
		repaint();
		onChange.accept(color());
	}

	/** Points the marker at an existing colour without telling the listener. */
	public void setColor(Color color) {
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		hue = hsb[0];
		saturation = hsb[1];
		value = hsb[2];
		repaint();
	}

	private void pick(MouseEvent event) {
		int size = Math.min(getWidth(), getHeight());
		double centerX = getWidth() / 2.0;
		double centerY = getHeight() / 2.0;
		double dx = event.getX() - centerX;
		double dy = event.getY() - centerY;
		double radius = size / 2.0;
		double distance = Math.hypot(dx, dy);

		if (event.getID() == MouseEvent.MOUSE_PRESSED && distance > radius + 2) {
			return;
		}

		// Hue runs anticlockwise from the +x axis (red), so the screen's y has to be flipped.
		double angle = Math.atan2(-dy, dx);
		hue = (float) (((angle / (2 * Math.PI)) + 1.0) % 1.0);
		saturation = (float) Math.min(1.0, Math.max(0.0, distance / radius));
		repaint();
		onChange.accept(color());
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		Ui.hints(g);
		int size = Math.min(getWidth(), getHeight());

		if (size <= 0) {
			g.dispose();
			return;
		}

		if (disc == null || discSize != size) {
			disc = renderDisc(size);
			discSize = size;
		}

		int x = (getWidth() - size) / 2;
		int y = (getHeight() - size) / 2;
		g.drawImage(disc, x, y, null);

		// The disc stays at full brightness on purpose: dimming it as the brightness drops would
		// make hues harder to aim at, and the swatch and the bar already show what comes out.
		g.setColor(Theme.alpha(Theme.TEXT, 55));
		g.setStroke(new BasicStroke(1.2f));
		g.drawOval(x, y, size - 1, size - 1);

		// The marker: a white ring with a dark one inside it, so it stays visible on any hue.
		double radius = (size / 2.0) * saturation;
		int markerX = (int) Math.round(x + size / 2.0 + Math.cos(hue * 2 * Math.PI) * radius);
		int markerY = (int) Math.round(y + size / 2.0 - Math.sin(hue * 2 * Math.PI) * radius);

		g.setStroke(new BasicStroke(3.0f));
		g.setColor(new Color(0, 0, 0, 210));
		g.drawOval(markerX - 8, markerY - 8, 16, 16);
		g.setStroke(new BasicStroke(1.8f));
		g.setColor(Color.WHITE);
		g.drawOval(markerX - 8, markerY - 8, 16, 16);
		g.dispose();
	}

	/** The hue/saturation field at full brightness, rendered large and scaled down once per size. */
	private BufferedImage renderDisc(int size) {
		int large = size * SUPERSAMPLE;
		BufferedImage raw = new BufferedImage(large, large, BufferedImage.TYPE_INT_RGB);
		double radius = large / 2.0;
		double center = radius;

		for (int y = 0; y < large; y++) {
			for (int x = 0; x < large; x++) {
				double dx = x + 0.5 - center;
				double dy = y + 0.5 - center;
				double distance = Math.hypot(dx, dy);

				if (distance > radius) {
					raw.setRGB(x, y, 0x000000);
					continue;
				}

				float pixelHue = (float) (((Math.atan2(-dy, dx) / (2 * Math.PI)) + 1.0) % 1.0);
				float pixelSaturation = (float) Math.min(1.0, distance / radius);
				raw.setRGB(x, y, getHSB(pixelHue, pixelSaturation, 1f).getRGB());
			}
		}

		BufferedImage smooth = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(raw, 0, 0, size, size, 0, 0, large, large, null);
		g.dispose();

		// Clip the square to a circle, with a softened rim. The mask covers the whole square, because
		// a composite only touches the pixels the source actually paints — an oval alone would leave
		// the corners opaque black.
		BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D m = mask.createGraphics();
		m.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		m.setColor(Color.WHITE);
		m.fillOval(0, 0, size, size);
		m.dispose();

		Graphics2D cut = smooth.createGraphics();
		cut.setComposite(java.awt.AlphaComposite.DstIn);
		cut.drawImage(mask, 0, 0, null);
		cut.dispose();
		return smooth;
	}

	private static Color getHSB(float hue, float saturation, float brightness) {
		return Color.getHSBColor(hue, saturation, brightness);
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(diameter, diameter);
	}
}
