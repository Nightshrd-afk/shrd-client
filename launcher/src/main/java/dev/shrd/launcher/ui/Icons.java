package dev.shrd.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Hand-drawn glyphs for the rail and buttons. They are drawn rather than loaded so the launcher has
 * no image assets to lose and stays crisp at any scale.
 * <p>
 * Every glyph is laid out on the same 24-unit grid — {@code x, y} is the top-left of the box and
 * {@code size} is its width — with one stroke weight, round joins and caps, and the same optical
 * margin. That is what makes a rail of seven icons look like a set instead of seven drawings: the
 * shapes differ, the rules do not.
 */
public final class Icons {

	/** Anything that can draw one of these glyphs, so callers can pass {@code Icons::home}. */
	@FunctionalInterface
	public interface Glyph {
		void paint(Graphics2D g, int x, int y, int size, Color color);
	}

	private Icons() {
	}

	/** A roof and a door, drawn as one continuous line so the two never look mismatched. */
	public static void home(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		Path2D roof = new Path2D.Double();
		roof.moveTo(x + unit * 0.10, y + unit * 0.44);
		roof.lineTo(x + unit * 0.50, y + unit * 0.09);
		roof.lineTo(x + unit * 0.90, y + unit * 0.44);
		g.draw(roof);
		g.draw(new RoundRectangle2D.Double(x + unit * 0.19, y + unit * 0.41, unit * 0.62, unit * 0.50, unit * 0.10, unit * 0.10));
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.50, y + unit * 0.66, x + unit * 0.50, y + unit * 0.91));
	}

	/** Two cards on top of each other: profiles, not a stack of files. */
	public static void instances(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new RoundRectangle2D.Double(x + unit * 0.08, y + unit * 0.10, unit * 0.68, unit * 0.50, unit * 0.12, unit * 0.12));
		g.draw(new RoundRectangle2D.Double(x + unit * 0.24, y + unit * 0.40, unit * 0.68, unit * 0.50, unit * 0.12, unit * 0.12));
	}

	/** Four blocks with even gaps. */
	public static void mods(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		double cell = unit * 0.34;
		double gap = unit * 0.16;
		double radius = unit * 0.11;
		g.fill(new RoundRectangle2D.Double(x + unit * 0.07, y + unit * 0.07, cell, cell, radius, radius));
		g.fill(new RoundRectangle2D.Double(x + unit * 0.07 + cell + gap, y + unit * 0.07, cell, cell, radius, radius));
		g.fill(new RoundRectangle2D.Double(x + unit * 0.07, y + unit * 0.07 + cell + gap, cell, cell, radius, radius));
		g.fill(new RoundRectangle2D.Double(x + unit * 0.07 + cell + gap, y + unit * 0.07 + cell + gap, cell, cell, radius, radius));
	}

	/** A window with a prompt in it. */
	public static void terminal(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new RoundRectangle2D.Double(x + unit * 0.06, y + unit * 0.13, unit * 0.88, unit * 0.74, unit * 0.14, unit * 0.14));
		Path2D prompt = new Path2D.Double();
		prompt.moveTo(x + unit * 0.26, y + unit * 0.40);
		prompt.lineTo(x + unit * 0.42, y + unit * 0.50);
		prompt.lineTo(x + unit * 0.26, y + unit * 0.60);
		g.draw(prompt);
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.54, y + unit * 0.60, x + unit * 0.74, y + unit * 0.60));
	}

	/** A ring with eight teeth and a hole: readable at 20px, which a cog wheel of lines is not. */
	public static void gear(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		double cx = x + unit * 0.5;
		double cy = y + unit * 0.5;
		g.draw(new Ellipse2D.Double(cx - unit * 0.27, cy - unit * 0.27, unit * 0.54, unit * 0.54));
		g.draw(new Ellipse2D.Double(cx - unit * 0.08, cy - unit * 0.08, unit * 0.16, unit * 0.16));

		for (int i = 0; i < 8; i++) {
			double angle = Math.toRadians(i * 45);
			double inner = unit * 0.28;
			double outer = unit * 0.43;
			g.draw(new java.awt.geom.Line2D.Double(
				cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner,
				cx + Math.cos(angle) * outer, cy + Math.sin(angle) * outer));
		}
	}

	public static void check(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		Path2D check = new Path2D.Double();
		check.moveTo(x + unit * 0.16, y + unit * 0.54);
		check.lineTo(x + unit * 0.42, y + unit * 0.78);
		check.lineTo(x + unit * 0.86, y + unit * 0.24);
		g.draw(check);
	}

	public static void plus(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.5, y + unit * 0.14, x + unit * 0.5, y + unit * 0.86));
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.14, y + unit * 0.5, x + unit * 0.86, y + unit * 0.5));
	}

	public static void play(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		Path2D triangle = new Path2D.Double();
		triangle.moveTo(x + unit * 0.30, y + unit * 0.16);
		triangle.lineTo(x + unit * 0.82, y + unit * 0.50);
		triangle.lineTo(x + unit * 0.30, y + unit * 0.84);
		triangle.closePath();
		g.fill(triangle);
	}

	public static void square(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.fill(new RoundRectangle2D.Double(x + unit * 0.22, y + unit * 0.22, unit * 0.56, unit * 0.56, unit * 0.12, unit * 0.12));
	}

	public static void minus(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.22, y + unit * 0.5, x + unit * 0.78, y + unit * 0.5));
	}

	public static void cross(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.26, y + unit * 0.26, x + unit * 0.74, y + unit * 0.74));
		g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.74, y + unit * 0.26, x + unit * 0.26, y + unit * 0.74));
	}

	public static void folder(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		Path2D folder = new Path2D.Double();
		folder.moveTo(x + unit * 0.10, y + unit * 0.76);
		folder.lineTo(x + unit * 0.10, y + unit * 0.30);
		folder.lineTo(x + unit * 0.40, y + unit * 0.30);
		folder.lineTo(x + unit * 0.48, y + unit * 0.20);
		folder.lineTo(x + unit * 0.84, y + unit * 0.20);
		folder.lineTo(x + unit * 0.84, y + unit * 0.76);
		folder.closePath();
		g.draw(folder);
	}

	/** A server rack: two units, each with a status light. */
	public static void server(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new RoundRectangle2D.Double(x + unit * 0.10, y + unit * 0.12, unit * 0.80, unit * 0.32, unit * 0.10, unit * 0.10));
		g.draw(new RoundRectangle2D.Double(x + unit * 0.10, y + unit * 0.56, unit * 0.80, unit * 0.32, unit * 0.10, unit * 0.10));
		g.fill(new Ellipse2D.Double(x + unit * 0.20, y + unit * 0.235, unit * 0.09, unit * 0.09));
		g.fill(new Ellipse2D.Double(x + unit * 0.20, y + unit * 0.675, unit * 0.09, unit * 0.09));
	}

	/**
	 * A painter's palette. The gap in the outline and the thumb hole are what stop it reading as a
	 * face: a closed oval with dots in it looks like a smiley at 20px.
	 */
	public static void palette(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new java.awt.geom.Arc2D.Double(x + unit * 0.09, y + unit * 0.12, unit * 0.82, unit * 0.74, 35, 295,
			java.awt.geom.Arc2D.OPEN));
		g.draw(new Ellipse2D.Double(x + unit * 0.30, y + unit * 0.44, unit * 0.20, unit * 0.20));
		double dot = unit * 0.13;
		g.fill(new Ellipse2D.Double(x + unit * 0.24, y + unit * 0.22, dot, dot));
		g.fill(new Ellipse2D.Double(x + unit * 0.48, y + unit * 0.17, dot, dot));
		g.fill(new Ellipse2D.Double(x + unit * 0.62, y + unit * 0.30, dot, dot));
	}

	/** A clock, for the "last played" row. */
	public static void clock(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		double cx = x + unit * 0.5;
		double cy = y + unit * 0.5;
		g.draw(new Ellipse2D.Double(cx - unit * 0.40, cy - unit * 0.40, unit * 0.80, unit * 0.80));
		g.draw(new java.awt.geom.Line2D.Double(cx, cy - unit * 0.20, cx, cy));
		g.draw(new java.awt.geom.Line2D.Double(cx, cy, cx + unit * 0.16, cy + unit * 0.08));
	}

	/** Three sliders: the appearance/settings icon that stays legible at rail size. */
	public static void sliders(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		double[] rows = { 0.22, 0.50, 0.78 };
		double[] knobs = { 0.68, 0.34, 0.56 };

		for (int i = 0; i < rows.length; i++) {
			double rowY = y + unit * rows[i];
			g.draw(new java.awt.geom.Line2D.Double(x + unit * 0.12, rowY, x + unit * 0.88, rowY));
			g.fill(new Ellipse2D.Double(x + unit * knobs[i] - unit * 0.09, rowY - unit * 0.09, unit * 0.18, unit * 0.18));
		}
	}

	/** A round badge with a person in it, for the account row. */
	public static void account(Graphics2D g, int x, int y, int size, Color color) {
		start(g, size, color);
		double unit = size;
		g.draw(new Ellipse2D.Double(x + unit * 0.09, y + unit * 0.09, unit * 0.82, unit * 0.82));
		g.draw(new Ellipse2D.Double(x + unit * 0.37, y + unit * 0.28, unit * 0.26, unit * 0.26));
		Path2D shoulders = new Path2D.Double();
		shoulders.moveTo(x + unit * 0.27, y + unit * 0.74);
		shoulders.curveTo(x + unit * 0.29, y + unit * 0.56, x + unit * 0.71, y + unit * 0.56, x + unit * 0.73, y + unit * 0.74);
		g.draw(shoulders);
	}

	/** The one rule every glyph follows: this colour, this weight, round ends. */
	private static void start(Graphics2D g, int size, Color color) {
		g.setColor(color);
		g.setStroke(new BasicStroke(
			Math.max(1.35f, size * 0.078f),
			BasicStroke.CAP_ROUND,
			BasicStroke.JOIN_ROUND));
	}
}
