package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Backgrounds;
import dev.shrd.launcher.ui.ColorWheel;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * One page for the two things that are purely the player's taste: the colour the whole launcher is
 * built from, and the picture behind it.
 * <p>
 * A theme is one accent colour — every panel, row, border and gradient is derived from it in
 * {@link Theme#apply(String)} — so the presets here are the same eight palettes, and any colour at
 * all can be picked instead. Applying rebuilds the window in place, which is why the change lands
 * without a restart.
 */
public final class CustomisationPage extends Page {

	private final JPanel swatches = Ui.row();
	private final JLabel accentValue = Ui.accent("");
	private final Ui.Field hexField = new Ui.Field("#A855F7");
	private final ColorWheel wheel = new ColorWheel(238);
	private final Brightness brightness = new Brightness();
	private final PendingSwatch pendingSwatch = new PendingSwatch();
	private final JLabel pendingValue = Ui.accent("");
	private final JLabel backgroundValue = Ui.dim("");
	private final JLabel backgroundNote = Ui.dim("");
	private final Preview preview = new Preview();
	private final Thumbnail thumbnail = new Thumbnail();
	private final List<Swatch> tiles = new ArrayList<>();
	private String shownAccent = "";

	public CustomisationPage(App app) {
		super(app, "Customisation", "Colour theme and background — both apply straight away");

		JPanel column = Ui.column();
		column.add(themeCard());
		column.add(Ui.gap(16));
		column.add(backgroundCard());
		column.add(Ui.gap(16));
		column.add(previewCard());
		column.add(Ui.gap(20));

		JPanel holder = Ui.column();
		holder.add(column);
		holder.add(Box.createVerticalGlue());
		body(Ui.scroll(holder));

		app.onRefresh(this::reload);
	}

	// ---------------------------------------------------------------- theme

	private JPanel themeCard() {
		Ui.Card card = new Ui.Card("Colour theme");
		card.body().add(Ui.dim("Pick an accent. Every panel, row, border and button is built from it,"));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("so the launcher changes as a whole rather than one highlighted colour."));
		card.body().add(Ui.gap(14));

		swatches.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 10));
		swatches.setAlignmentX(LEFT_ALIGNMENT);
		swatches.setOpaque(false);

		for (Theme.Preset preset : Theme.PRESETS) {
			Swatch swatch = new Swatch(preset);
			tiles.add(swatch);
			swatches.add(swatch);
		}

		swatches.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
		card.body().add(swatches);
		card.body().add(Ui.gap(12));
		card.body().add(wheelBlock());
		card.body().add(Ui.gap(6));

		JPanel chosen = Ui.row();
		chosen.add(Ui.caps("Current accent"));
		chosen.add(Ui.hgap(10));
		accentValue.setFont(Theme.mono(12));
		chosen.add(accentValue);
		chosen.setAlignmentX(LEFT_ALIGNMENT);
		chosen.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		card.body().add(chosen);
		card.body().add(Ui.gap(14));

		JPanel buttons = Ui.row();
		hexField.setPreferredSize(new Dimension(130, 34));
		hexField.setMaximumSize(new Dimension(130, 34));
		hexField.setToolTipText("Any hex colour, for example #22D3EE");
		buttons.add(hexField);
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Use this hex").size(140, 34).onClick(button -> useHex()));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Reset to default").size(170, 34).onClick(button -> app.setAccent(Theme.DEFAULT_ACCENT)));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(buttons);
		return card;
	}

	private void useHex() {
		String text = hexField.getText().trim();

		if (Theme.parse(text, null) == null) {
			app.status("That is not a hex colour — try #A855F7");
			return;
		}

		app.setAccent(Theme.hex(Theme.parse(text, Theme.ACCENT)));
	}

	/** The wheel and everything around it: the picked colour, its hex, and the button that applies it. */
	private JComponent wheelBlock() {
		JPanel block = Ui.row(new BorderLayout(26, 0));
		block.setAlignmentX(LEFT_ALIGNMENT);
		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 258));

		JPanel holder = Ui.column();
		holder.add(wheel);
		holder.setPreferredSize(new Dimension(238, 238));
		holder.setMaximumSize(new Dimension(238, 238));
		block.add(holder, BorderLayout.WEST);

		JPanel column = Ui.column();
		column.setPreferredSize(new Dimension(470, 240));
		column.setMaximumSize(new Dimension(470, Integer.MAX_VALUE));
		JPanel side = column;
		side.add(Ui.caps("Any colour you like"));
		side.add(Ui.gap(8));

		JPanel picked = Ui.row(new BorderLayout(14, 0));
		picked.add(pendingSwatch, BorderLayout.WEST);
		JPanel value = Ui.column();
		pendingValue.setFont(Theme.mono(15));
		value.add(pendingValue);
		value.add(Ui.gap(3));
		value.add(Ui.dim("Drag round the wheel for the hue"));
		picked.add(value, BorderLayout.CENTER);
		picked.setAlignmentX(LEFT_ALIGNMENT);
		picked.setMaximumSize(new Dimension(470, 54));
		side.add(picked);
		side.add(Ui.gap(16));

		side.add(Ui.caps("Brightness"));
		side.add(Ui.gap(6));
		brightness.setAlignmentX(LEFT_ALIGNMENT);
		brightness.setPreferredSize(new Dimension(470, 20));
		brightness.setMaximumSize(new Dimension(470, 20));
		side.add(brightness);
		side.add(Ui.gap(16));

		JPanel buttons = Ui.row();
		Ui.Button apply = Ui.primary("Use this colour").size(200, 38);
		apply.onClick(button -> app.setAccent(pendingHex()));
		buttons.add(apply);
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.dim("The presets above apply at once."));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(470, 40));
		side.add(buttons);
		side.add(Ui.gap(10));
		side.add(Ui.dim("The wheel only reaches full brightness — the bar is what makes a colour darker."));
		side.add(Box.createVerticalGlue());

		JPanel sideHolder = Ui.row();
		sideHolder.add(side);
		sideHolder.add(Box.createHorizontalGlue());
		block.add(sideHolder, BorderLayout.CENTER);

		// Nothing is applied mid-drag on purpose: applying themes the whole window, which would
		// rebuild this page out from under the mouse. The button at the end commits it.
		wheel.onChange(this::show);
		brightness.onChange(level -> {
			wheel.setValue(level);
			show(wheel.color());
		});
		return block;
	}

	private String pendingHex() {
		return Theme.hex(wheel.color());
	}

	/** Shows a candidate colour in the swatch and the hex label, without applying it. */
	private void show(Color colour) {
		pendingSwatch.setColour(colour);
		pendingValue.setText(Theme.hex(colour));
		hexField.setText(Theme.hex(colour));
		brightness.setBase(wheel.fullBrightness());
	}

	// ---------------------------------------------------------------- background

	private JPanel backgroundCard() {
		Ui.Card card = new Ui.Card("Background");
		card.body().add(Ui.dim("Any PNG or JPG. It is copied into the launcher's own folder, so the"));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("original file can move or be deleted afterwards. Without one, the"));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("built-in gradient shows through instead."));
		card.body().add(Ui.gap(14));

		JPanel row = Ui.row(new BorderLayout(14, 0));
		row.add(thumbnail, BorderLayout.WEST);

		JPanel words = Ui.column();
		words.add(Ui.caps("In use"));
		words.add(Ui.gap(4));
		backgroundValue.setMaximumSize(new Dimension(620, 20));
		words.add(backgroundValue);
		words.add(Ui.gap(3));
		backgroundNote.setFont(Theme.font(11));
		words.add(backgroundNote);
		row.add(words, BorderLayout.CENTER);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		card.body().add(row);
		card.body().add(Ui.gap(14));

		JPanel buttons = Ui.row();
		buttons.add(Ui.primary("Choose image…").size(180, 34).withGlyph(Icons::folder).onClick(button -> chooseBackground()));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Remove background").size(190, 34).onClick(button -> app.setBackground("")));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Open folder").size(140, 34).onClick(button -> app.openFolder(ShrdPaths.backgrounds())));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(buttons);
		return card;
	}

	private void chooseBackground() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Pick a background image");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));

		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		try {
			Path source = chooser.getSelectedFile().toPath();
			Path folder = ShrdPaths.backgrounds();
			Files.createDirectories(folder);
			Path target = folder.resolve(source.getFileName().toString());
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			app.setBackground(target.toAbsolutePath().toString());
		} catch (Exception e) {
			app.status("Could not use that image: " + e.getMessage());
		}
	}

	// ---------------------------------------------------------------- preview

	private JPanel previewCard() {
		Ui.Card card = new Ui.Card("Preview");
		card.body().add(Ui.dim("The palette as the launcher draws it: rail, page, card, rows and a primary button."));
		card.body().add(Ui.gap(12));
		preview.setAlignmentX(LEFT_ALIGNMENT);
		preview.setPreferredSize(new Dimension(10, 190));
		preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
		card.body().add(preview);
		return card;
	}

	@Override
	public void reload() {
		String accent = Theme.currentHex();

		if (!accent.equals(shownAccent)) {
			shownAccent = accent;
			hexField.setText(accent);
			wheel.setColor(Theme.parse(accent, Theme.ACCENT));
			brightness.setValue(wheel.value());
			brightness.setBase(wheel.fullBrightness());
			pendingSwatch.setColour(wheel.color());
			pendingValue.setText(pendingHex());
		}

		accentValue.setText(Theme.currentHex() + "  ·  " + nameOf(Theme.currentHex()));

		for (Swatch tile : tiles) {
			tile.setSelected(tile.preset.accent().equalsIgnoreCase(Theme.currentHex()));
		}

		String background = app.config.backgroundImage;

		if (background == null || background.isBlank()) {
			backgroundValue.setText("Built-in gradient");
			backgroundNote.setText("No image set");
		} else {
			backgroundValue.setText(Ui.elide(background.substring(background.lastIndexOf('\\') + 1), 60));
			backgroundNote.setText(Ui.elide(background, 74));
		}

		thumbnail.setPath(background);
		preview.repaint();
		swatches.repaint();
	}

	private static String nameOf(String hex) {
		for (Theme.Preset preset : Theme.PRESETS) {
			if (preset.accent().equalsIgnoreCase(hex)) {
				return preset.name();
			}
		}

		return "custom";
	}

	// ---------------------------------------------------------------- pieces

	/**
	 * The third axis of HSV: a bar from black to the wheel's current colour at full brightness. Without
	 * it the disc can only produce light colours, because it is painted at full value.
	 */
	private final class Brightness extends JComponent {
		private float value = 1f;
		private Color base = Color.WHITE;
		private java.util.function.Consumer<Float> onChange = level -> {
		};

		private Brightness() {
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("How bright the colour is");
			MouseAdapter drag = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent event) {
					pick(event);
				}

				@Override
				public void mouseDragged(MouseEvent event) {
					pick(event);
				}
			};
			addMouseListener(drag);
			addMouseMotionListener(drag);
		}

		private void onChange(java.util.function.Consumer<Float> handler) {
			this.onChange = handler;
		}

		/** Sets the colour at full brightness, which is what the bar fades up to. */
		private void setBase(Color colour) {
			base = colour;
			repaint();
		}

		private void setValue(float level) {
			value = Math.max(0f, Math.min(1f, level));
			repaint();
		}

		private void pick(MouseEvent event) {
			int span = Math.max(1, getWidth() - 16);
			float level = (event.getX() - 8) / (float) span;
			value = Math.max(0f, Math.min(1f, level));
			repaint();
			onChange.accept(value);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight() - 4;

			java.awt.Shape track = new java.awt.geom.RoundRectangle2D.Double(0, 0, width, height, height, height);
			g.setPaint(new GradientPaint(0, 0, Color.BLACK, width, 0, base));
			g.fill(track);
			g.setColor(Theme.alpha(Theme.TEXT, 40));
			g.draw(track);

			int x = (int) Math.round(8 + value * (width - 16));
			int y = height / 2;
			g.setColor(new Color(0, 0, 0, 200));
			g.fillOval(x - 8, y - 8, 16, 16);
			g.setColor(Color.WHITE);
			g.fillOval(x - 6, y - 6, 12, 12);
			g.setColor(Theme.alpha(Theme.TEXT, 90));
			g.drawOval(x - 6, y - 6, 12, 12);
			g.dispose();
		}
	}

	/** The colour under the wheel, before it is applied. */
	private static final class PendingSwatch extends JComponent {
		private Color colour = Color.WHITE;

		private PendingSwatch() {
			setPreferredSize(new Dimension(64, 50));
			setMaximumSize(new Dimension(64, 50));
		}

		private void setColour(Color value) {
			colour = value;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			java.awt.Shape shape = new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 9, 9);
			g.setColor(colour);
			g.fill(shape);
			g.setColor(new Color(0, 0, 0, 90));
			g.draw(shape);
			g.setColor(Theme.alpha(Theme.TEXT, 70));
			g.draw(shape);
			g.dispose();
		}
	}

	/** One preset: the colour, its name, and a ring when it is the one in use. */
	private final class Swatch extends JComponent {
		private final Theme.Preset preset;
		private boolean selected;
		private boolean hover;

		private Swatch(Theme.Preset preset) {
			this.preset = preset;
			setPreferredSize(new Dimension(104, 88));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(preset.name() + "  " + preset.accent());
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					app.setAccent(preset.accent());
				}

				@Override
				public void mouseEntered(MouseEvent event) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent event) {
					hover = false;
					repaint();
				}
			});
		}

		private void setSelected(boolean value) {
			selected = value;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			Color accent = Theme.parse(preset.accent(), Theme.ACCENT);
			int width = getWidth();
			int height = getHeight();

			g.setColor(hover ? Theme.ROW_HOVER : Theme.PANEL_HEADER);
			Ui.roundRect(g, 0, 0, width, height, 9);
			g.setColor(selected ? accent : Theme.alpha(Theme.TEXT_DIM, 60));
			g.setStroke(new BasicStroke(selected ? 1.8f : 1.1f));
			g.drawRoundRect(0, 0, width - 1, height - 1, 9, 9);

			int size = 34;
			int cx = (width - size) / 2;
			int cy = 14;
			g.setPaint(new GradientPaint(cx, cy, Theme.mix(accent, Color.WHITE, 0.25f), cx, cy + size, Theme.mix(accent, Color.BLACK, 0.25f)));
			g.fillOval(cx, cy, size, size);
			g.setColor(new Color(0, 0, 0, 90));
			g.drawOval(cx, cy, size, size);

			if (selected) {
				g.setColor(accent);
				g.setStroke(new BasicStroke(1.6f));
				g.drawOval(cx - 5, cy - 5, size + 10, size + 10);
			}

			g.setFont(Theme.bold(10));
			FontMetrics metrics = g.getFontMetrics();
			String label = preset.name();
			g.setColor(selected ? Theme.TEXT : Theme.TEXT_DIM);
			g.drawString(label, (width - metrics.stringWidth(label)) / 2, height - 14);
			g.dispose();
		}
	}

	/** The background file as a small picture, so the choice is visible and not just a path. */
	private final class Thumbnail extends JComponent {
		private String path = "";

		private Thumbnail() {
			setPreferredSize(new Dimension(104, 58));
			setMaximumSize(new Dimension(104, 58));
		}

		private void setPath(String value) {
			path = value == null ? "" : value;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight();
			java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Double(0, 0, width, height, 8, 8);
			g.clip(clip);

			BufferedImage image = Backgrounds.of(path);

			if (image != null) {
				double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
				int drawWidth = (int) Math.ceil(image.getWidth() * scale);
				int drawHeight = (int) Math.ceil(image.getHeight() * scale);
				g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(image, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
			} else {
				g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.PANEL_HEADER, Theme.ACCENT, 0.25f), width, height, Theme.WINDOW));
				g.fillRect(0, 0, width, height);
				g.setColor(Theme.TEXT_DISABLED);
				g.setFont(Theme.font(10));
				String label = "gradient";
				g.drawString(label, (width - g.getFontMetrics().stringWidth(label)) / 2, height / 2 + 4);
			}

			g.setClip(null);
			g.setColor(Theme.alpha(Theme.ACCENT, 120));
			g.setStroke(new BasicStroke(1.2f));
			g.draw(clip);
			g.dispose();
		}
	}

	/** A miniature launcher in the current palette. */
	private static final class Preview extends JComponent {
		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight();

			java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Double(0, 0, width, height, 14, 14);
			java.awt.Shape previous = g.getClip();
			g.clip(clip);

			g.setColor(Theme.WINDOW);
			g.fillRect(0, 0, width, height);
			g.setPaint(new RadialGradientPaint(width * 0.7f, height * 0.3f, width * 0.6f,
				new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_DEEP, 70), new Color(0, 0, 0, 0) }));
			g.fillRect(0, 0, width, height);

			// rail
			g.setColor(Theme.RAIL);
			g.fillRect(0, 0, 66, height);
			g.setColor(Theme.alpha(Theme.ACCENT_DEEP, 200));
			Ui.roundRect(g, 10, 26, 46, 40, 8);
			g.setColor(Theme.ACCENT_ALT);
			g.fillRoundRect(10, 30, 3, 32, 3, 3);
			g.setColor(Theme.TEXT_DIM);
			for (int i = 0; i < 3; i++) {
				g.fillRoundRect(20, 92 + i * 22, 26, 12, 6, 6);
			}

			// card
			int cardX = 82;
			int cardY = 20;
			int cardW = Math.max(120, width - cardX - 18);
			int cardH = height - 40;
			g.setColor(Theme.PANEL);
			Ui.roundRect(g, cardX, cardY, cardW, cardH, 10);
			g.setColor(Theme.PANEL_HEADER);
			g.fillRoundRect(cardX, cardY, cardW, 26, 10, 10);
			g.setColor(Theme.alpha(Theme.ACCENT, 150));
			g.drawRoundRect(cardX, cardY, cardW, cardH, 10, 10);

			g.setColor(Theme.TEXT);
			g.setFont(Theme.bold(11));
			g.drawString("Preview", cardX + 14, cardY + 18);

			int rowY = cardY + 38;
			g.setColor(Theme.ROW);
			Ui.roundRect(g, cardX + 12, rowY, cardW - 24, 26, 6);
			g.setColor(Theme.TEXT_DIM);
			g.fillRoundRect(cardX + 22, rowY + 10, 90, 6, 3, 3);
			g.setColor(Theme.ACCENT);
			Ui.roundRect(g, cardX + cardW - 92, rowY + 6, 70, 14, 7);

			int buttonY = cardY + cardH - 34;
			g.setPaint(new GradientPaint(cardX + 12, buttonY, Theme.ACCENT_ALT, cardX + 12, buttonY + 24, Theme.ACCENT_DEEP));
			Ui.roundRect(g, cardX + 12, buttonY, 120, 24, 7);
			g.setColor(Theme.mix(Theme.ACCENT, Color.WHITE, 0.55f));
			g.setFont(Theme.bold(9));
			g.drawString("PLAY", cardX + 58, buttonY + 16);

			g.setClip(previous);
			g.setColor(Theme.alpha(Theme.ACCENT, 100));
			g.setStroke(new BasicStroke(1.2f));
			g.draw(clip);
			g.dispose();
		}
	}
}
