package dev.shrd.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Hand-drawn glyphs for the rail and buttons. They are drawn rather than loaded so the launcher
 * has no image assets to lose and stays crisp at any scale.
 */
public final class Icons {

	/** Anything that can draw one of these glyphs, so callers can pass {@code Icons::home}. */
	@FunctionalInterface
	public interface Glyph {
		void paint(Graphics2D g, int x, int y, int size, Color color);
	}

	private Icons() {
	}

	public static void home(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		Path2D roof = new Path2D.Double();
		roof.moveTo(x, y + size * 0.45);
		roof.lineTo(x + size * 0.5, y + size * 0.1);
		roof.lineTo(x + size, y + size * 0.45);
		g.draw(roof);
		g.draw(new RoundRectangle2D.Double(x + size * 0.17, y + size * 0.42, size * 0.66, size * 0.48, 2, 2));
	}

	public static void instances(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		double offset = size * 0.16;
		g.draw(new RoundRectangle2D.Double(x, y + offset, size * 0.72, size * 0.72, 3, 3));
		g.draw(new RoundRectangle2D.Double(x + offset, y, size * 0.72, size * 0.72, 3, 3));
	}

	public static void mods(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		double cell = size * 0.36;
		double gap = size * 0.16;
		g.fill(new RoundRectangle2D.Double(x, y, cell, cell, 3, 3));
		g.fill(new RoundRectangle2D.Double(x + cell + gap, y, cell, cell, 3, 3));
		g.fill(new RoundRectangle2D.Double(x, y + cell + gap, cell, cell, 3, 3));
		g.fill(new RoundRectangle2D.Double(x + cell + gap, y + cell + gap, cell, cell, 3, 3));
	}

	public static void terminal(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.draw(new RoundRectangle2D.Double(x, y, size, size * 0.86, 4, 4));
		Path2D prompt = new Path2D.Double();
		prompt.moveTo(x + size * 0.22, y + size * 0.3);
		prompt.lineTo(x + size * 0.4, y + size * 0.45);
		prompt.lineTo(x + size * 0.22, y + size * 0.6);
		g.draw(prompt);
		g.drawLine((int) (x + size * 0.52), (int) (y + size * 0.6), (int) (x + size * 0.78), (int) (y + size * 0.6));
	}

	public static void gear(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		double center = size / 2.0;
		g.draw(new java.awt.geom.Ellipse2D.Double(x + center * 0.62, y + center * 0.62, center * 0.76, center * 0.76));

		for (int i = 0; i < 6; i++) {
			double angle = Math.toRadians(i * 60);
			double inner = center * 0.86;
			double outer = center * 0.98;
			int x1 = (int) Math.round(x + center + Math.cos(angle) * inner);
			int y1 = (int) Math.round(y + center + Math.sin(angle) * inner);
			int x2 = (int) Math.round(x + center + Math.cos(angle) * outer);
			int y2 = (int) Math.round(y + center + Math.sin(angle) * outer);
			g.drawLine(x1, y1, x2, y2);
		}
	}

	public static void check(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		Path2D check = new Path2D.Double();
		check.moveTo(x + size * 0.15, y + size * 0.55);
		check.lineTo(x + size * 0.42, y + size * 0.8);
		check.lineTo(x + size * 0.9, y + size * 0.2);
		g.draw(check);
	}

	public static void plus(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.drawLine((int) (x + size * 0.15), (int) (y + size * 0.5), (int) (x + size * 0.85), (int) (y + size * 0.5));
		g.drawLine((int) (x + size * 0.5), (int) (y + size * 0.15), (int) (x + size * 0.5), (int) (y + size * 0.85));
	}

	public static void play(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		Path2D triangle = new Path2D.Double();
		triangle.moveTo(x + size * 0.25, y + size * 0.12);
		triangle.lineTo(x + size * 0.85, y + size * 0.5);
		triangle.lineTo(x + size * 0.25, y + size * 0.88);
		triangle.closePath();
		g.fill(triangle);
	}

	public static void square(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.fill(new RoundRectangle2D.Double(x + size * 0.15, y + size * 0.15, size * 0.7, size * 0.7, 3, 3));
	}

	public static void minus(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.drawLine((int) (x + size * 0.2), (int) (y + size * 0.5), (int) (x + size * 0.8), (int) (y + size * 0.5));
	}

	public static void cross(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.drawLine((int) (x + size * 0.25), (int) (y + size * 0.25), (int) (x + size * 0.75), (int) (y + size * 0.75));
		g.drawLine((int) (x + size * 0.75), (int) (y + size * 0.25), (int) (x + size * 0.25), (int) (y + size * 0.75));
	}

	public static void folder(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.draw(new RoundRectangle2D.Double(x + size * 0.08, y + size * 0.28, size * 0.84, size * 0.5, 3, 3));
		Path2D tab = new Path2D.Double();
		tab.moveTo(x + size * 0.14, y + size * 0.28);
		tab.lineTo(x + size * 0.2, y + size * 0.15);
		tab.lineTo(x + size * 0.48, y + size * 0.15);
		tab.lineTo(x + size * 0.54, y + size * 0.28);
		g.draw(tab);
	}

	/** A server rack: two stacked units with a status dot, for the Servers page. */
	public static void server(Graphics2D g, int x, int y, int size, Color color) {
		g.setColor(color);
		g.setStroke(stroke(size));
		g.draw(new RoundRectangle2D.Double(x + size * 0.08, y + size * 0.1, size * 0.84, size * 0.34, 3, 3));
		g.draw(new RoundRectangle2D.Double(x + size * 0.08, y + size * 0.56, size * 0.84, size * 0.34, 3, 3));
		g.fill(new java.awt.geom.Ellipse2D.Double(x + size * 0.18, y + size * 0.22, size * 0.1, size * 0.1));
		g.fill(new java.awt.geom.Ellipse2D.Double(x + size * 0.18, y + size * 0.68, size * 0.1, size * 0.1));
	}

	private static BasicStroke stroke(int size) {
		return new BasicStroke(Math.max(1.3f, size * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}
}
