package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.LauncherConfig;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ui.pages.ConsolePage;
import dev.shrd.launcher.ui.pages.CustomisationPage;
import dev.shrd.launcher.ui.pages.HomePage;
import dev.shrd.launcher.ui.pages.InstancesPage;
import dev.shrd.launcher.ui.pages.ModsPage;
import dev.shrd.launcher.ui.pages.ServersPage;
import dev.shrd.launcher.ui.pages.SettingsPage;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The window itself. Undecorated on purpose: the purple-on-black identity would be broken by an
 * operating-system title bar, so the window carries its own — a drag handle, a minimise button and
 * a close button — and puts the navigation rail on the left where a launcher's rail belongs.
 */
public final class LauncherFrame extends JFrame {

	public static final String[] PAGE_IDS = { "home", "instances", "mods", "servers", "console", "customise", "settings" };

	private final App app;
	private final CardLayout cards = new CardLayout();
	/**
	 * The page area. It paints the window colour and the client's plus-sign texture itself, so every
	 * page shares one backdrop instead of each one having to remember to draw it.
	 */
	private final JPanel content = new JPanel(cards) {
		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(Theme.WINDOW);
			g.fillRect(0, 0, getWidth(), getHeight());
			Ui.hatch(g, getWidth(), getHeight());
			g.dispose();
		}
	};
	private final JPanel root = new Backdrop();
	private final Map<String, Page> pages = new LinkedHashMap<>();
	private final Map<String, RailItem> railItems = new LinkedHashMap<>();
	/** Where each page was scrolled to, so a theme change does not throw the player back to the top. */
	private final Map<String, Integer> scrolls = new LinkedHashMap<>();
	private final Ui.Progress progress = new Ui.Progress();
	private final JPanel progressHolder = Ui.row(new BorderLayout());
	// Recreated on every rebuild, because a label keeps the colour it was constructed with.
	private JLabel statusLabel = Ui.dim("Ready");
	private JLabel infoLabel = Ui.dim("");
	private String current = "home";
	private boolean wasRunning;

	public LauncherFrame(LauncherConfig config, String initialPage) {
		super("Shrd Launcher");
		this.app = new App(config);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setUndecorated(true);
		setIconImage(windowIcon());
		setMinimumSize(new Dimension(1000, 640));
		setSize(1180, 740);
		setLocationRelativeTo(null);

		// A theme change rebuilds the whole interface: custom painting picks the new colours up by
		// itself, but labels, buttons and switches captured theirs when they were created.
		app.onThemeChange(this::rebuild);
		build();
		showPage(initialPage == null || !pages.containsKey(initialPage) ? "home" : initialPage);
		new Timer(700, event -> poll()).start();
	}

	/** Builds the chrome and every page against the current palette. */
	private void build() {
		rememberScrolls();
		app.resetListeners();
		app.onShowPage(this::showPage);
		app.onStage((stage, detail) -> onStage(stage));

		statusLabel = Ui.dim("Ready");
		infoLabel = Ui.dim("");
		infoLabel.setText(app.javaDescription() + "  ·  shrd launcher " + dev.shrd.launcher.Version.CURRENT);
		app.setStatusHandler(statusLabel::setText);
		app.setProgressHandler(this::setProgress);

		pages.clear();
		railItems.clear();
		content.removeAll();
		root.removeAll();
		root.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.WINDOW_EDGE, 1));
		root.add(titleBar(), BorderLayout.NORTH);
		root.add(rail(), BorderLayout.WEST);
		root.add(contentArea(), BorderLayout.CENTER);
		root.add(statusBar(), BorderLayout.SOUTH);
		setContentPane(root);

		for (Page page : pages.values()) {
			app.onRefresh(page::reload);
		}

		root.revalidate();
		root.repaint();
		app.refresh();
		javax.swing.SwingUtilities.invokeLater(this::restoreScrolls);
	}

	private void rememberScrolls() {
		for (Map.Entry<String, Page> entry : pages.entrySet()) {
			javax.swing.JScrollPane pane = scrollOf(entry.getValue());

			if (pane != null) {
				scrolls.put(entry.getKey(), pane.getVerticalScrollBar().getValue());
			}
		}
	}

	private void restoreScrolls() {
		for (Map.Entry<String, Page> entry : pages.entrySet()) {
			javax.swing.JScrollPane pane = scrollOf(entry.getValue());
			Integer value = scrolls.get(entry.getKey());

			if (pane != null && value != null) {
				pane.getVerticalScrollBar().setValue(value);
			}
		}
	}

	/** The first scroll pane in a page, which is the one every page wraps its body in. */
	private static javax.swing.JScrollPane scrollOf(java.awt.Component root) {
		if (root instanceof javax.swing.JScrollPane pane) {
			return pane;
		}

		if (root instanceof java.awt.Container container) {
			for (java.awt.Component child : container.getComponents()) {
				javax.swing.JScrollPane found = scrollOf(child);

				if (found != null) {
					return found;
				}
			}
		}

		return null;
	}

	public void rebuild() {
		String page = current;
		build();
		showPage(pages.containsKey(page) ? page : "home");
		repaint();
	}

	/** The window and taskbar icon: the shrd gem, as a jar resource rather than a file path. */
	private static java.awt.Image windowIcon() {
		try (java.io.InputStream stream = LauncherFrame.class.getResourceAsStream("/icon.png")) {
			if (stream != null) {
				return javax.imageio.ImageIO.read(stream);
			}
		} catch (Exception e) {
			Log.warn("could not load the window icon: %s", e.getMessage());
		}

		return null;
	}

	private void onStage(String stage) {
		progressHolder.setVisible(!stage.equals("idle") && !stage.equals("running"));
		repaint();
	}

	private void setProgress(double fraction) {
		progress.setFraction(fraction);
		progressHolder.setVisible(fraction > 0 && fraction < 1);
	}

	/** Keeps the Play button and the status strip honest about what the game is doing. */
	private void poll() {
		boolean running = app.isRunning();

		if (running != wasRunning) {
			wasRunning = running;
			app.refresh();

			if (running) {
				statusLabel.setText("Minecraft is running" + (app.service.session() != null ? ", pid " + app.service.session().pid() : ""));
				progressHolder.setVisible(false);
			} else {
				statusLabel.setText("Minecraft closed");
				progress.setFraction(0);
			}
		}
	}

	// ------------------------------------------------------------------ chrome

	private JComponent titleBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(Theme.TITLE_BAR);
		bar.setBorder(new EmptyBorder(10, 16, 10, 10));

		JPanel brand = Ui.row();
		brand.add(new BrandMark());
		brand.add(Ui.hgap(8));
		brand.add(new Ui.Wordmark("shrd", 20));
		brand.add(Ui.hgap(10));
		brand.add(Ui.caps("launcher"));
		brand.add(Ui.hgap(8));
		brand.add(Ui.dim("v" + dev.shrd.launcher.Version.CURRENT));
		bar.add(brand, BorderLayout.WEST);

		JPanel buttons = Ui.row();
		buttons.add(new ChromeButton(Icons::minus, Theme.TEXT_DIM, () -> setState(java.awt.Frame.ICONIFIED)));
		buttons.add(Ui.hgap(4));
		buttons.add(new ChromeButton(Icons::cross, Theme.BAD, () -> {
			app.config.save();
			System.exit(0);
		}));
		bar.add(buttons, BorderLayout.EAST);

		drag(bar);
		return bar;
	}

	private void drag(JComponent handle) {
		MouseAdapter adapter = new MouseAdapter() {
			private Point pressed;

			@Override
			public void mousePressed(MouseEvent event) {
				pressed = event.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (pressed != null) {
					Point onScreen = event.getLocationOnScreen();
					setLocation(onScreen.x - pressed.x, onScreen.y - pressed.y);
				}
			}
		};
		handle.addMouseListener(adapter);
		handle.addMouseMotionListener(adapter);
	}

	private JComponent rail() {
		JPanel rail = new JPanel();
		rail.setBackground(Theme.RAIL);
		rail.setLayout(new javax.swing.BoxLayout(rail, javax.swing.BoxLayout.Y_AXIS));
		rail.setBorder(new EmptyBorder(14, 10, 12, 10));
		rail.setPreferredSize(new Dimension(100, 10));

		rail.add(railItem("home", "Home", Icons::home, () -> showPage("home")));
		rail.add(railItem("instances", "Instances", Icons::instances, () -> showPage("instances")));
		rail.add(railItem("mods", "Mods", Icons::mods, () -> showPage("mods")));
		rail.add(railItem("servers", "Servers", Icons::server, () -> showPage("servers")));
		rail.add(railItem("console", "Console", Icons::terminal, () -> showPage("console")));
		rail.add(railItem("customise", "Customise", Icons::palette, () -> showPage("customise")));
		rail.add(railItem("settings", "Settings", Icons::sliders, () -> showPage("settings")));
		rail.add(javax.swing.Box.createVerticalGlue());

		rail.add(accountChip());
		return rail;
	}

	private JComponent accountChip() {
		JPanel chip = Ui.column();
		chip.setAlignmentX(LEFT_ALIGNMENT);
		chip.add(new Avatar());
		chip.add(Ui.gap(6));
		JLabel name = Ui.dim("");
		name.setAlignmentX(LEFT_ALIGNMENT);
		chip.add(name);
		app.onRefresh(() -> {
			Account account = app.config.activeAccount();
			name.setText(account.name);
			chip.repaint();
		});
		return chip;
	}

	private RailItem railItem(String id, String label, Icons.Glyph glyph, Runnable onClick) {
		RailItem item = new RailItem(label, glyph, onClick);
		railItems.put(id, item);
		return item;
	}

	private JComponent contentArea() {
		content.setOpaque(true);
		content.setBackground(Theme.WINDOW);
		pages.put("home", new HomePage(app));
		pages.put("instances", new InstancesPage(app));
		pages.put("mods", new ModsPage(app));
		pages.put("servers", new ServersPage(app));
		pages.put("console", new ConsolePage(app));
		pages.put("customise", new CustomisationPage(app));
		pages.put("settings", new SettingsPage(app));

		for (Map.Entry<String, Page> entry : pages.entrySet()) {
			content.add(entry.getValue(), entry.getKey());
		}

		return content;
	}

	private JComponent statusBar() {
		JPanel bar = new JPanel(new BorderLayout(12, 0));
		bar.setBackground(Theme.RAIL);
		bar.setBorder(new EmptyBorder(8, 16, 8, 16));
		bar.add(statusLabel, BorderLayout.WEST);

		progressHolder.add(progress, BorderLayout.CENTER);
		progressHolder.setVisible(false);
		bar.add(progressHolder, BorderLayout.CENTER);
		bar.add(infoLabel, BorderLayout.EAST);
		return bar;
	}

	public void showPage(String id) {
		Page page = pages.get(id);

		if (page == null) {
			return;
		}

		current = id;
		cards.show(content, id);
		railItems.forEach((key, item) -> item.setSelected(key.equals(id)));
		page.reload();
	}

	public String currentPage() {
		return current;
	}

	public App app() {
		return app;
	}

	// ------------------------------------------------------------------ pieces

	/**
	 * The window's own backdrop: the player's image under a heavy scrim, or just the flat theme
	 * colour. It is dimmed this hard on purpose — every page's text is designed for near-black, and a
	 * photo at full brightness would make half the launcher unreadable.
	 */
	private final class Backdrop extends JPanel {
		private String scaledFor = "";
		private java.awt.image.BufferedImage scaled;

		private Backdrop() {
			super(new BorderLayout());
			setBackground(Theme.WINDOW);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			String path = app.config.backgroundImage;
			java.awt.image.BufferedImage image = Backgrounds.of(path);

			if (image == null) {
				return;
			}

			// A deliberately soft backdrop: the photo is shrunk to a thumbnail and blown back up,
			// which blurs it, so it reads as wallpaper behind the pages instead of competing with
			// the text drawn on top. The Home banner still shows the same picture at full sharpness.
			if (!path.equals(scaledFor)) {
				scaledFor = path;
				double factor = Math.min(1.0, 110.0 / Math.max(1, image.getWidth()));
				int smallW = Math.max(20, (int) Math.round(image.getWidth() * factor));
				int smallH = Math.max(20, (int) Math.round(image.getHeight() * factor));
				scaled = new java.awt.image.BufferedImage(smallW, smallH, java.awt.image.BufferedImage.TYPE_INT_RGB);
				Graphics2D small = scaled.createGraphics();
				small.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				small.drawImage(image, 0, 0, smallW, smallH, null);
				small.dispose();
			}

			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			int width = getWidth();
			int height = getHeight();
			double scale = Math.max(width / (double) scaled.getWidth(), height / (double) scaled.getHeight());
			int drawWidth = (int) Math.ceil(scaled.getWidth() * scale);
			int drawHeight = (int) Math.ceil(scaled.getHeight() * scale);
			g.drawImage(scaled, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
			g.setColor(new Color(8, 4, 16, 176));
			g.fillRect(0, 0, width, height);
			g.setPaint(new java.awt.GradientPaint(0, 0, Theme.alpha(Theme.WINDOW, 150), 0, height, Theme.alpha(Theme.WINDOW, 235)));
			g.fillRect(0, 0, width, height);
			g.dispose();
		}
	}

	/** A rail entry: a tile with the glyph in it, the label underneath, and the accent when chosen. */
	private static final class RailItem extends JComponent {
		private static final int TILE_H = 40;
		private final String label;
		private final Icons.Glyph glyph;
		private boolean selected;
		private boolean hover;

		private RailItem(String label, Icons.Glyph glyph, Runnable onClick) {
			this.label = label;
			this.glyph = glyph;
			setLayout(null);
			setPreferredSize(new Dimension(80, 60));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(label);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					onClick.run();
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

			// The tile is the thing that reacts, not the whole row: the rail stays quiet, and the
			// glyph sits in a shape the eye can follow down the column.
			int tileWidth = Math.min(52, getWidth() - 8);
			int tileX = (getWidth() - tileWidth) / 2;

			if (selected) {
				g.setPaint(new java.awt.GradientPaint(0, 2, Theme.ACCENT, 0, 2 + TILE_H, Theme.ACCENT_DEEP));
				Ui.roundRect(g, tileX, 2, tileWidth, TILE_H, 12);
				g.setColor(Theme.alpha(Color.WHITE, 90));
				g.drawRoundRect(tileX, 2, tileWidth - 1, TILE_H - 1, 12, 12);
			} else if (hover) {
				g.setColor(Theme.ROW_HOVER);
				Ui.roundRect(g, tileX, 2, tileWidth, TILE_H, 12);
			}

			Color color = selected ? Theme.ON_ACCENT : (hover ? Theme.TEXT : Theme.TEXT_DIM);
			int size = 21;
			glyph.paint(g, (getWidth() - size) / 2, 2 + (TILE_H - size) / 2, size, color);

			String text = label.toUpperCase(java.util.Locale.ROOT);
			g.setFont(Theme.bold(9));
			g.setColor(selected ? Theme.TEXT_ACCENT : (hover ? Theme.TEXT : Theme.TEXT_DISABLED));
			int width = g.getFontMetrics().stringWidth(text);
			g.drawString(text, (getWidth() - width) / 2, getHeight() - 6);
			g.dispose();
		}
	}

	/** The account badge at the bottom of the rail: a gradient circle with your initial. */
	private final class Avatar extends JComponent {
		private Avatar() {
			setPreferredSize(new Dimension(40, 40));
			setMaximumSize(new Dimension(40, 40));
			setAlignmentX(LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Accounts are managed on the Settings page");
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					showPage("settings");
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setPaint(new java.awt.GradientPaint(0, 0, Theme.ACCENT_ALT, getWidth(), getHeight(), Theme.ACCENT_DEEP));
			g.fillOval(0, 0, getWidth(), getHeight());
			String initial = app.config.activeAccount().name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
			g.setFont(Theme.bold(17));
			g.setColor(Theme.ON_ACCENT);
			int width = g.getFontMetrics().stringWidth(initial);
			g.drawString(initial, (getWidth() - width) / 2, getHeight() / 2 + 6);
			g.dispose();
		}
	}

	/** The shrd gem, next to the name: the same artwork as the window and installer icon. */
	private static final class BrandMark extends JComponent {
		private final java.awt.Image image = windowIcon();

		private BrandMark() {
			setPreferredSize(new Dimension(24, 24));
			setMaximumSize(new Dimension(24, 24));
			setToolTipText("Shrd Launcher");
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			if (image == null) {
				return;
			}

			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, 0, 1, 22, 22, null);
			g.dispose();
		}
	}

	/** Minimise / close buttons for the custom title bar. Drawn, so no font can be missing a glyph. */
	private static final class ChromeButton extends JComponent {
		private final Icons.Glyph glyph;
		private final Color accent;
		private boolean hover;

		private ChromeButton(Icons.Glyph glyph, Color accent, Runnable onClick) {
			this.glyph = glyph;
			this.accent = accent;
			setPreferredSize(new Dimension(30, 24));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					onClick.run();
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

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);

			if (hover) {
				g.setColor(Theme.alpha(accent, 50));
				Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 4);
			}

			int size = 12;
			glyph.paint(g, (getWidth() - size) / 2, (getHeight() - size) / 2, size, hover ? accent : Theme.TEXT_DIM);
			g.dispose();
		}
	}
}
