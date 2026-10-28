package dev.shrd.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * The launcher's widgets. Everything here paints itself: Swing's stock look and feel is a light
 * grey 1990s thing that would fight the black-and-purple theme, so buttons, switches, sliders,
 * fields, scrollbars and list rows are all drawn by hand on top of the theme colours.
 */
public final class Ui {

	public static final int RADIUS = 8;

	private Ui() {
	}

	public static void hints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	public static void roundRect(Graphics2D g, int x, int y, int width, int height, int radius) {
		g.fillRoundRect(x, y, width, height, radius, radius);
	}

	// ---------------------------------------------------------------- text

	public static JLabel pageTitle(String text) {
		JLabel label = new JLabel(text);
		label.setFont(Theme.bold(24));
		label.setForeground(Theme.TEXT);
		return label;
	}

	public static JLabel heading(String text) {
		JLabel label = new JLabel(text);
		label.setFont(Theme.bold(14));
		label.setForeground(Theme.TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static JLabel text(String value) {
		JLabel label = new JLabel(value);
		label.setFont(Theme.font(13));
		label.setForeground(Theme.TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static JLabel dim(String value) {
		JLabel label = new JLabel(value);
		label.setFont(Theme.font(12));
		label.setForeground(Theme.TEXT_DIM);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static JLabel accent(String value) {
		JLabel label = new JLabel(value);
		label.setFont(Theme.bold(13));
		label.setForeground(Theme.TEXT_ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	public static JLabel caps(String value) {
		JLabel label = new JLabel(value.toUpperCase(java.util.Locale.ROOT));
		label.setFont(Theme.bold(10));
		label.setForeground(Theme.TEXT_DISABLED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Shortens long text for display, keeping the tail — the end of a path is the part that says
	 * which file it is. Long labels are also what force a card wider than the window, so this is
	 * a layout fix as much as a cosmetic one.
	 */
	public static String elide(String value, int maxChars) {
		if (value == null) {
			return "";
		}

		if (value.length() <= maxChars) {
			return value;
		}

		return "…" + value.substring(value.length() - (maxChars - 1));
	}

	/** A label that cannot widen past {@code maxChars}, for paths and other long values. */
	public static JLabel elided(String value, int maxChars) {
		JLabel label = dim(elide(value, maxChars));
		label.setToolTipText(value);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Makes a control stretch to the width of the box it is in instead of centring itself. */
	public static <T extends JComponent> T fill(T component) {
		Dimension preferred = component.getPreferredSize();
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(preferred.height, 26)));
		return component;
	}

	public static Component gap(int height) {
		return Box.createRigidArea(new Dimension(0, height));
	}

	public static Component hgap(int width) {
		return Box.createRigidArea(new Dimension(width, 0));
	}

	// ---------------------------------------------------------------- layout

	public static JPanel column() {
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	public static JPanel row() {
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		return panel;
	}

	public static JPanel row(java.awt.LayoutManager layout) {
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	/** A rounded dark card with an optional header row. */
	public static final class Card extends JPanel {
		private final JPanel content;

		public Card(String title, JComponent trailing) {
			setOpaque(false);
			setLayout(new java.awt.BorderLayout());
			setBorder(new EmptyBorder(14, 16, 16, 16));
			setAlignmentX(LEFT_ALIGNMENT);

			if (title != null) {
				JPanel header = row();
				header.add(heading(title));
				header.add(Box.createHorizontalGlue());

				if (trailing != null) {
					header.add(trailing);
				}

				header.setAlignmentX(LEFT_ALIGNMENT);
				add(header, java.awt.BorderLayout.NORTH);
			}

			content = column();
			content.setAlignmentX(LEFT_ALIGNMENT);
			add(content, java.awt.BorderLayout.CENTER);
		}

		public Card(String title) {
			this(title, null);
		}

		/** The vertical box rows and controls are added to. */
		public JPanel body() {
			return content;
		}

		public void addRow(Component component) {
			component.setFont(Theme.font(13));
			content.add(component);
			content.add(gap(8));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			g.setColor(Theme.PANEL);
			roundRect(g, 0, 0, getWidth(), getHeight(), RADIUS);
			g.setColor(Theme.PANEL_OUTLINE);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	// ---------------------------------------------------------------- buttons

	public enum Variant {
		PRIMARY, SECONDARY, GHOST, DANGER
	}

	public static final class Button extends javax.swing.JButton {
		private final Variant variant;
		private Consumer<Button> onClick;
		private Glyph glyph;

		public interface Glyph {
			void paint(Graphics2D g, int x, int y, int size, Color color);
		}

		public Button(String text, Variant variant) {
			super(text);
			this.variant = variant;
			setFocusPainted(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setForeground(variant == Variant.PRIMARY ? Theme.ON_ACCENT : Theme.TEXT);
			setFont(variant == Variant.PRIMARY ? Theme.bold(14) : Theme.font(13));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setMargin(new Insets(0, 0, 0, 0));
		}

		public Button withGlyph(Glyph glyph) {
			this.glyph = glyph;
			return this;
		}

		public Button size(int width, int height) {
			setPreferredSize(new Dimension(width, height));
			setMinimumSize(new Dimension(width, height));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
			return this;
		}

		/** Runs on the EDT when clicked; the listener is not an ActionListener, so no boxing. */
		public Button onClick(Consumer<Button> handler) {
			this.onClick = handler;
			addActionListener(event -> {
				if (onClick != null) {
					onClick.accept(this);
				}
			});
			return this;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			int width = getWidth();
			int height = getHeight();
			boolean hover = getModel().isRollover() && isEnabled();
			boolean pressed = getModel().isPressed() && isEnabled();
			Color fillTop;
			Color fillBottom;
			Color border;
			Color textColor;

			switch (variant) {
				case PRIMARY -> {
					fillTop = hover ? Theme.ACCENT_ALT : Theme.ACCENT;
					fillBottom = pressed ? Theme.ACCENT_DEEP : Theme.mix(Theme.ACCENT_DEEP, Theme.ACCENT, 0.35f);
					border = Theme.ACCENT_ALT;
					textColor = Theme.ON_ACCENT;
				}
				case SECONDARY -> {
					fillTop = hover ? Theme.ROW_HOVER : Theme.PANEL_HEADER;
					fillBottom = fillTop;
					border = hover ? Theme.ACCENT : Theme.PANEL_OUTLINE;
					textColor = hover ? Theme.TEXT : Theme.TEXT_ACCENT;
				}
				case DANGER -> {
					fillTop = Theme.alpha(Theme.BAD, hover ? 60 : 28);
					fillBottom = fillTop;
					border = Theme.alpha(Theme.BAD, hover ? 220 : 130);
					textColor = Theme.BAD;
				}
				default -> {
					fillTop = Theme.alpha(Theme.ACCENT, hover ? 34 : 0);
					fillBottom = fillTop;
					border = new Color(0, 0, 0, 0);
					textColor = hover ? Theme.TEXT : Theme.TEXT_DIM;
				}
			}

			if (!isEnabled()) {
				textColor = Theme.TEXT_DISABLED;
			}

			if (fillTop.getAlpha() > 0) {
				g.setPaint(new GradientPaint(0, 0, fillTop, 0, height, fillBottom));
				roundRect(g, 0, 0, width, height, variant == Variant.PRIMARY ? RADIUS : 6);
			}

			if (border.getAlpha() > 0) {
				g.setColor(border);
				g.drawRoundRect(0, 0, width - 1, height - 1, variant == Variant.PRIMARY ? RADIUS : 6, variant == Variant.PRIMARY ? RADIUS : 6);
			}

			FontMetrics metrics = g.getFontMetrics(getFont());
			String label = getText();
			int textWidth = metrics.stringWidth(label);
			int glyphSize = 14;
			int total = textWidth + (glyph == null ? 0 : glyphSize + 8);

			if (glyph != null) {
				int startX = (width - total) / 2;
				glyph.paint(g, startX, (height - glyphSize) / 2, glyphSize, textColor);
			}

			g.setFont(getFont());
			g.setColor(textColor);
			int textX = (width - total) / 2 + (glyph == null ? 0 : glyphSize + 8);
			int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
			g.drawString(label, textX, baseline);
			g.dispose();
		}
	}

	public static Button primary(String text) {
		return new Button(text, Variant.PRIMARY);
	}

	public static Button secondary(String text) {
		return new Button(text, Variant.SECONDARY);
	}

	// ---------------------------------------------------------------- switch

	/** A purple pill switch, the same shape the in-game mod menu uses. */
	public static final class Switch extends JComponent {
		private boolean on;
		private boolean hover;
		private Consumer<Boolean> onChange;

		public Switch(boolean initial, Consumer<Boolean> onChange) {
			this.on = initial;
			this.onChange = onChange;
			setPreferredSize(new Dimension(42, 24));
			setMinimumSize(new Dimension(42, 24));
			setMaximumSize(new Dimension(42, 24));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					set(!Switch.this.on, true);
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

		public void set(boolean value, boolean fire) {
			on = value;
			repaint();

			if (fire && onChange != null) {
				onChange.accept(value);
			}
		}

		public boolean isOn() {
			return on;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			int height = Math.min(getHeight(), 24);
			int y = (getHeight() - height) / 2;
			g.setColor(on ? Theme.mix(Theme.ACCENT_DEEP, Theme.ACCENT, hover ? 0.4f : 0.15f) : (hover ? Theme.ROW_HOVER : Theme.TRACK));
			roundRect(g, 0, y, getWidth(), height, height);
			g.setColor(on ? Theme.ACCENT_ALT : Theme.alpha(Theme.TEXT_DISABLED, 160));
			g.drawRoundRect(0, y, getWidth() - 1, height - 1, height, height);
			int knob = height - 6;
			int knobX = on ? getWidth() - knob - 3 : 3;
			g.setColor(on ? Theme.TEXT : Theme.TEXT_DIM);
			g.fillOval(knobX, y + 3, knob, knob);
			g.dispose();
		}
	}

	// ---------------------------------------------------------------- slider

	public static final class Slider extends JSlider {
		public Slider(int min, int max, int value) {
			super(min, max, value);
			setOpaque(false);
			setFocusable(false);
			setUI(new SliderUi(this));
			setPreferredSize(new Dimension(200, 22));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		private static final class SliderUi extends BasicSliderUI {
			private SliderUi(JSlider slider) {
				super(slider);
			}

			@Override
			protected void installDefaults(JSlider slider) {
				super.installDefaults(slider);
				focusInsets = new Insets(0, 0, 0, 0);
			}

			@Override
			public void paintTrack(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				hints(g);
				int height = 6;
				int y = trackRect.y + (trackRect.height - height) / 2;
				g.setColor(Theme.TRACK);
				roundRect(g, trackRect.x, y, trackRect.width, height, height);
				int filled = thumbRect.x + thumbRect.width / 2 - trackRect.x;
				g.setColor(Theme.ACCENT_DEEP);
				roundRect(g, trackRect.x, y, Math.max(height, filled), height, height);
				g.setColor(Theme.alpha(Theme.ACCENT, 90));
				g.drawRoundRect(trackRect.x, y, trackRect.width - 1, height - 1, height, height);
				g.dispose();
			}

			@Override
			public void paintThumb(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				hints(g);
				int size = 16;
				int x = thumbRect.x + (thumbRect.width - size) / 2;
				int y = thumbRect.y + (thumbRect.height - size) / 2;
				g.setColor(Theme.ACCENT_ALT);
				g.fillOval(x, y, size, size);
				g.setColor(Theme.WINDOW);
				g.fillOval(x + 5, y + 5, size - 10, size - 10);
				g.dispose();
			}

			@Override
			protected Dimension getThumbSize() {
				return new Dimension(18, 18);
			}
		}
	}

	// ---------------------------------------------------------------- text input

	public static final class Field extends JTextField {
		private final String placeholder;
		private boolean focused;

		public Field(String placeholder, String value) {
			super(value == null ? "" : value);
			this.placeholder = placeholder;
			setOpaque(false);
			setBorder(new EmptyBorder(8, 12, 8, 12));
			setFont(Theme.font(13));
			setForeground(Theme.TEXT);
			setCaretColor(Theme.ACCENT);
			setSelectionColor(Theme.alpha(Theme.ACCENT, 90));
			setSelectedTextColor(Theme.TEXT);
			addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent event) {
					focused = true;
					repaint();
				}

				@Override
				public void focusLost(FocusEvent event) {
					focused = false;
					repaint();
				}
			});
		}

		public Field(String placeholder) {
			this(placeholder, "");
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			g.setColor(Theme.SETTING);
			roundRect(g, 0, 0, getWidth(), getHeight(), 6);
			g.setColor(focused ? Theme.ACCENT : Theme.PANEL_OUTLINE);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			g.dispose();
			super.paintComponent(graphics);

			if (getText().isEmpty() && placeholder != null) {
				Graphics2D overlay = (Graphics2D) graphics.create();
				hints(overlay);
				overlay.setFont(getFont());
				overlay.setColor(Theme.TEXT_DISABLED);
				FontMetrics metrics = overlay.getFontMetrics();
				overlay.drawString(placeholder, 12, (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
				overlay.dispose();
			}
		}
	}

	public static final class Area extends JTextArea {
		public Area(int rows, int columns) {
			super(rows, columns);
			// A JTextArea starts with a PlainDocument, which cannot carry per-line colours; the
			// console needs to colour log levels, so it gets a styled document from the start.
			setDocument(new javax.swing.text.DefaultStyledDocument());
			setOpaque(true);
			setEditable(false);
			setBackground(Theme.SETTING);
			setForeground(Theme.TEXT);
			setFont(Theme.mono(12));
			setBorder(new EmptyBorder(10, 12, 10, 12));
			setSelectionColor(Theme.alpha(Theme.ACCENT, 110));
			setLineWrap(true);
			setWrapStyleWord(true);
		}
	}

	// ---------------------------------------------------------------- scrollbars

	public static final class ScrollBar extends JScrollBar {
		public ScrollBar(int orientation) {
			super(orientation);
			setUI(new ScrollBarUi());
			setOpaque(false);
			setPreferredSize(new Dimension(orientation == VERTICAL ? 10 : 10, 10));
			setUnitIncrement(18);
		}

		private static final class ScrollBarUi extends BasicScrollBarUI {
			@Override
			protected void configureScrollBarColors() {
				trackColor = Theme.alpha(Theme.WINDOW, 0);
				thumbColor = Theme.alpha(Theme.ACCENT_DEEP, 200);
			}

			@Override
			protected javax.swing.JButton createDecreaseButton(int orientation) {
				return zeroButton();
			}

			@Override
			protected javax.swing.JButton createIncreaseButton(int orientation) {
				return zeroButton();
			}

			private javax.swing.JButton zeroButton() {
				javax.swing.JButton button = new javax.swing.JButton();
				button.setPreferredSize(new Dimension(0, 0));
				button.setMinimumSize(new Dimension(0, 0));
				button.setMaximumSize(new Dimension(0, 0));
				return button;
			}

			@Override
			protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
				Graphics2D g = (Graphics2D) graphics.create();
				hints(g);
				g.setColor(Theme.alpha(Theme.WINDOW, 120));
				roundRect(g, bounds.x + 2, bounds.y, bounds.width - 4, bounds.height, 5);
				g.dispose();
			}

			@Override
			protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
				if (bounds.height <= 4 && bounds.width <= 4) {
					return;
				}

				Graphics2D g = (Graphics2D) graphics.create();
				hints(g);
				g.setColor(isThumbRollover() ? Theme.ACCENT : Theme.alpha(Theme.ACCENT_DEEP, 230));
				roundRect(g, bounds.x + 2, bounds.y + 1, Math.max(4, bounds.width - 4), Math.max(6, bounds.height - 2), 5);
				g.dispose();
			}
		}
	}

	public static JScrollPane scroll(Component content) {
		JScrollPane pane = new JScrollPane(content);
		pane.setBorder(BorderFactory.createEmptyBorder());
		pane.setOpaque(false);
		pane.getViewport().setOpaque(false);
		pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		pane.setVerticalScrollBar(new ScrollBar(JScrollBar.VERTICAL));
		pane.getVerticalScrollBar().setOpaque(false);
		pane.getVerticalScrollBar().setUnitIncrement(18);
		return pane;
	}

	// ---------------------------------------------------------------- rows and combos

	/** A list row: rounded, hover-highlighted, optionally selected. */
	public static final class Row extends JPanel {
		private boolean hover;
		private boolean selected;

		public Row() {
			setOpaque(false);
			setLayout(new java.awt.BorderLayout(10, 0));
			setBorder(new EmptyBorder(10, 12, 10, 12));
		}

		public Row selectable(Consumer<Row> onClick) {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					onClick.accept(Row.this);
				}
			});
			return hoverable();
		}

		public Row hoverable() {
			addMouseListener(new MouseAdapter() {
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
			return this;
		}

		public void setSelected(boolean value) {
			selected = value;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);

			if (selected) {
				g.setColor(Theme.alpha(Theme.ACCENT_DEEP, 190));
			} else if (hover) {
				g.setColor(Theme.ROW_HOVER);
			} else {
				g.setColor(Theme.ROW);
			}

			roundRect(g, 0, 0, getWidth(), getHeight(), 6);

			if (selected) {
				g.setColor(Theme.ACCENT);
				roundRect(g, 0, 4, 3, getHeight() - 8, 3);
			}

			g.dispose();
			super.paintComponent(graphics);
		}
	}

	public static final class Combo<E> extends JComboBox<E> {
		public Combo(E[] items, E selected) {
			super(items);

			if (selected != null) {
				setSelectedItem(selected);
			}

			setOpaque(false);
			setFocusable(false);
			setFont(Theme.font(13));
			setBackground(Theme.SETTING);
			setForeground(Theme.TEXT);
			setBorder(new EmptyBorder(6, 10, 6, 26));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setUI(new ComboUi<>(this));
			setRenderer(new Renderer());
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			g.setColor(Theme.SETTING);
			roundRect(g, 0, 0, getWidth(), getHeight(), 6);
			g.setColor(isPopupVisible() ? Theme.ACCENT : Theme.PANEL_OUTLINE);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			int size = 7;
			int x = getWidth() - 16;
			int y = getHeight() / 2 - 2;
			g.setColor(Theme.TEXT_ACCENT);
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(x, y, x + size / 2, y + size / 2);
			g.drawLine(x + size / 2, y + size / 2, x + size, y);
			g.dispose();
			super.paintComponent(graphics);
		}

		private static final class ComboUi<E> extends BasicComboBoxUI {
			private ComboUi(Combo<E> combo) {
				combo.setOpaque(false);
			}

			@Override
			protected javax.swing.JButton createArrowButton() {
				// The Combo paints its own chevron; the stock arrow is a light grey square that would
				// punch a hole in the theme, so it is hidden rather than merely made small.
				javax.swing.JButton button = new javax.swing.JButton();
				button.setPreferredSize(new Dimension(0, 0));
				button.setMinimumSize(new Dimension(0, 0));
				button.setMaximumSize(new Dimension(0, 0));
				button.setBorder(BorderFactory.createEmptyBorder());
				button.setOpaque(false);
				button.setContentAreaFilled(false);
				button.setFocusable(false);
				button.setVisible(false);
				return button;
			}

			@Override
			public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
				// The Combo paints its own background.
			}

			@Override
			public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
				Object value = comboBox.getSelectedItem();
				JLabel label = new JLabel(value == null ? "" : String.valueOf(value));
				label.setFont(comboBox.getFont());
				label.setForeground(Theme.TEXT_ACCENT);
				label.setOpaque(false);
				label.setBorder(new EmptyBorder(0, 2, 0, 0));
				currentValuePane.paintComponent(graphics, label, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, false);
			}
		}

		private static final class Renderer implements ListCellRenderer<Object> {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				JLabel label = new JLabel(value == null ? "" : String.valueOf(value));
				label.setFont(Theme.font(13));
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(6, 10, 6, 10));
				label.setBackground(isSelected ? Theme.ROW_ENABLED : Theme.SETTING);
				label.setForeground(isSelected ? Theme.TEXT : Theme.TEXT_ACCENT);
				return label;
			}
		}
	}

	// ---------------------------------------------------------------- misc

	/** Thin progress bar for the status strip. */
	public static final class Progress extends JComponent {
		private double fraction;

		public Progress() {
			setPreferredSize(new Dimension(220, 6));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		}

		public void setFraction(double value) {
			fraction = Math.max(0, Math.min(1, value));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			g.setColor(Theme.TRACK);
			roundRect(g, 0, 0, getWidth(), getHeight(), getHeight());
			int width = (int) (getWidth() * fraction);

			if (width > 2) {
				g.setPaint(new GradientPaint(0, 0, Theme.ACCENT_ALT, getWidth(), 0, Theme.ACCENT_DEEP));
				roundRect(g, 0, 0, width, getHeight(), getHeight());
			}

			g.dispose();
		}
	}

	/** The "shrd" wordmark: gradient purple, drawn rather than themed as a font. */
	public static final class Wordmark extends JComponent {
		private final String text;

		public Wordmark(String text, int fontSize) {
			this.text = text;
			setFont(Theme.bold(fontSize));
			setPreferredSize(new Dimension(fontSize * text.length() / 2 + 6, fontSize + 8));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			hints(g);
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
			g.setPaint(new GradientPaint(0, 0, Theme.ACCENT_ALT, Math.max(20, metrics.stringWidth(text)), 0, Theme.ACCENT));
			g.drawString(text, 0, baseline);
			g.dispose();
		}
	}
}
