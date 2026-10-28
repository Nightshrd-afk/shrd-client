package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.LauncherConfig;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ui.pages.ConsolePage;
import dev.shrd.launcher.ui.pages.HomePage;
import dev.shrd.launcher.ui.pages.InstancesPage;
import dev.shrd.launcher.ui.pages.ModsPage;
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

	public static final String[] PAGE_IDS = { "home", "instances", "mods", "console", "settings" };

	private final App app;
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final Map<String, Page> pages = new LinkedHashMap<>();
	private final Map<String, RailItem> railItems = new LinkedHashMap<>();
	private final JLabel statusLabel = Ui.dim("Ready");
	private final JLabel infoLabel = Ui.dim("");
	private final Ui.Progress progress = new Ui.Progress();
	private final JPanel progressHolder = Ui.row(new BorderLayout());
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

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.WINDOW);
		root.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.WINDOW_EDGE, 1));

		root.add(titleBar(), BorderLayout.NORTH);
		root.add(rail(), BorderLayout.WEST);
		root.add(contentArea(), BorderLayout.CENTER);
		root.add(statusBar(), BorderLayout.SOUTH);
		setContentPane(root);

		for (Page page : pages.values()) {
			app.onRefresh(page::reload);
		}

		showPage(initialPage == null || !pages.containsKey(initialPage) ? "home" : initialPage);
		app.onStage((stage, detail) -> onStage(stage));
		app.setStatusHandler(statusLabel::setText);
		app.setProgressHandler(this::setProgress);
		infoLabel.setText(app.javaDescription() + "  ·  shrd launcher " + dev.shrd.launcher.Version.CURRENT);
		app.refresh();

		new Timer(700, event -> poll()).start();
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
		rail.setPreferredSize(new Dimension(96, 10));

		rail.add(railItem("home", "Home", Icons::home, () -> showPage("home")));
		rail.add(railItem("instances", "Instances", Icons::instances, () -> showPage("instances")));
		rail.add(railItem("mods", "Mods", Icons::mods, () -> showPage("mods")));
		rail.add(railItem("console", "Console", Icons::terminal, () -> showPage("console")));
		rail.add(railItem("settings", "Settings", Icons::gear, () -> showPage("settings")));
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
		content.setOpaque(false);
		pages.put("home", new HomePage(app));
		pages.put("instances", new InstancesPage(app));
		pages.put("mods", new ModsPage(app));
		pages.put("console", new ConsolePage(app));
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

	/** A rail entry: glyph above a small label, with the accent bar when selected. */
	private static final class RailItem extends JComponent {
		private final String label;
		private final Icons.Glyph glyph;
		private boolean selected;
		private boolean hover;

		private RailItem(String label, Icons.Glyph glyph, Runnable onClick) {
			this.label = label;
			this.glyph = glyph;
			setLayout(null);
			setPreferredSize(new Dimension(76, 62));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
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

			if (selected || hover) {
				g.setColor(selected ? Theme.alpha(Theme.ACCENT_DEEP, 200) : Theme.ROW);
				Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 8);
			}

			if (selected) {
				g.setColor(Theme.ACCENT);
				Ui.roundRect(g, 0, 10, 3, getHeight() - 20, 3);
			}

			Color color = selected ? Theme.ACCENT_ALT : (hover ? Theme.TEXT : Theme.TEXT_DIM);
			int size = 20;
			glyph.paint(g, (getWidth() - size) / 2, 12, size, color);
			g.setFont(Theme.bold(10));
			g.setColor(color);
			String text = label.toUpperCase(java.util.Locale.ROOT);
			int width = g.getFontMetrics().stringWidth(text);
			g.drawString(text, (getWidth() - width) / 2, getHeight() - 10);
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
