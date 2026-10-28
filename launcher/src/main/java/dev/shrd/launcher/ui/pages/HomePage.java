package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Instance;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.PlayerSkin;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The home screen: the player's own skin over a banner you can change, the account chip, and the
 * profiles you last played — each with its own Play button, so launching never means hunting for a
 * selector first.
 */
public final class HomePage extends Page {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
	private static final BufferedImage GEM = gem();

	// Declared before the hero, which puts them on itself while it is being constructed.
	private final AccountChip accountChip = new AccountChip();
	private final Ui.Button updatePill = Ui.primary("Update available").size(170, 34);
	private final Hero hero = new Hero();
	private final JPanel profiles = Ui.column();
	private String skinKey = "";

	public HomePage(App app) {
		super(app, "Home", "Play Minecraft, with Shrd Client installed for you");

		JPanel column = Ui.column();
		column.add(hero);
		column.add(Ui.gap(18));
		column.add(sectionHeader());
		column.add(Ui.gap(10));
		column.add(profiles);
		column.add(Box.createVerticalGlue());

		body(column);
		body().add(bottomBar(), BorderLayout.SOUTH);

		updatePill.onClick(button -> app.updateNow());
		app.onStage((stage, detail) -> refreshStage());
		app.onRefresh(this::reload);
	}

	private JComponent sectionHeader() {
		JPanel row = Ui.row(new BorderLayout(12, 0));
		row.add(Ui.heading("Last played profiles"), BorderLayout.WEST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return row;
	}

	private JPanel bottomBar() {
		JPanel bar = Ui.row();
		bar.setBorder(new javax.swing.border.EmptyBorder(14, 0, 0, 0));
		Ui.Button manage = Ui.primary("Manage all profiles").size(320, 42).withGlyph(Icons::instances);
		manage.onClick(button -> app.showPage("instances"));
		bar.add(Ui.fill(manage));
		bar.add(Ui.hgap(12));
		Ui.Button mods = Ui.secondary("Add mods").size(220, 42).withGlyph(Icons::mods);
		mods.onClick(button -> app.showPage("mods"));
		bar.add(mods);
		return bar;
	}

	private JPanel profileRow(Instance instance) {
		boolean active = instance.name.equalsIgnoreCase(app.config.activeInstance);
		Ui.Row row = new Ui.Row();
		row.hoverable();
		row.setSelected(active);
		row.setLayout(new BorderLayout(14, 0));

		row.add(new IconTile(), BorderLayout.WEST);

		JPanel titles = Ui.column();
		titles.add(Ui.text(instance.name));
		titles.add(Ui.gap(3));
		String loader = instance.isFabric()
			? "Fabric " + instance.minecraftVersion + (instance.loaderVersion.isBlank() ? "" : " · loader " + instance.loaderVersion)
			: "Vanilla " + instance.minecraftVersion;
		titles.add(Ui.dim(loader + "  ·  " + (instance.lastPlayed == 0 ? "never played" : WHEN.format(Instant.ofEpochMilli(instance.lastPlayed)))));
		row.add(titles, BorderLayout.CENTER);

		JPanel controls = Ui.row();
		boolean running = app.isRunning() && active;
		Ui.Button play = Ui.primary(running ? "Stop" : "Play").size(104, 36);
		play.withGlyph(running ? Icons::square : Icons::play);
		play.onClick(button -> {
			if (!instance.name.equalsIgnoreCase(app.config.activeInstance)) {
				app.config.activeInstance = instance.name;
				app.config.save();
				app.refresh();
			}

			app.togglePlay();
		});
		controls.add(play);
		controls.add(Ui.hgap(8));
		Ui.Button gear = Ui.secondary("").size(36, 36);
		gear.withGlyph(Icons::gear);
		gear.setToolTipText("Instance settings");
		gear.onClick(button -> {
			app.config.activeInstance = instance.name;
			app.config.save();
			app.showPage("instances");
		});
		controls.add(gear);
		row.add(controls, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
		return row;
	}

	/** The shrd gem on a rounded tile, so a profile row reads as a profile and not a text line. */
	private static final class IconTile extends JComponent {
		private IconTile() {
			setPreferredSize(new Dimension(52, 52));
			setMaximumSize(new Dimension(52, 52));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setPaint(new GradientPaint(0, 0, Theme.PANEL_HEADER, getWidth(), getHeight(), Theme.TRACK));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 10);
			g.setColor(Theme.alpha(Theme.ACCENT, 90));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

			if (GEM != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(GEM, 9, 9, getWidth() - 18, getHeight() - 18, null);
			}

			g.dispose();
		}
	}

	@Override
	public void reload() {
		Account account = app.config.activeAccount();
		setSubtitle("Playing as " + account.name + (account.online() ? " (Microsoft)" : " (offline)"));
		accountChip.setAccount(account);

		List<Instance> instances = app.config.instances;
		profiles.removeAll();

		for (Instance instance : instances) {
			profiles.add(profileRow(instance));
			profiles.add(Ui.gap(10));
		}

		profiles.add(Ui.gap(0));

		boolean offer = app.updates.offerable() && !app.updates.busy();
		updatePill.setVisible(offer);

		if (offer) {
			updatePill.setText(app.updates.state() == dev.shrd.launcher.update.UpdateService.State.READY
				? "Restart to update"
				: "Update available");
		}

		loadSkin(account);
		hero.revalidate();
		hero.repaint();
		refreshStage();
	}

	/** Reads the skin off the event thread and repaints the hero once it is there. */
	private void loadSkin(Account account) {
		String key = account.name + "|" + account.uuid();

		if (key.equals(skinKey)) {
			return;
		}

		skinKey = key;
		BufferedImage cached = PlayerSkin.cached(account);
		hero.setSkin(cached);

		Thread loader = new Thread(() -> {
			BufferedImage skin = PlayerSkin.load(account);
			SwingUtilities.invokeLater(() -> {
				if (key.equals(skinKey)) {
					hero.setSkin(skin);
				}
			});
		}, "shrd-skin");
		loader.setDaemon(true);
		loader.start();
	}

	private void refreshStage() {
		hero.repaint();
	}

	// ------------------------------------------------------------------ hero

	/** The banner: background image or gradient, the player's figure, and the account chip. */
	private final class Hero extends JPanel {
		private BufferedImage skin;

		private Hero() {
			setLayout(null);
			setAlignmentX(LEFT_ALIGNMENT);
			setPreferredSize(new Dimension(10, 296));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 296));
			add(accountChip);
			add(updatePill);
			updatePill.setVisible(false);
		}

		private void setSkin(BufferedImage image) {
			skin = image;
			repaint();
		}

		@Override
		public void doLayout() {
			accountChip.setBounds(22, getHeight() - 58, 250, 42);
			updatePill.setBounds(getWidth() - 190, 18, 170, 34);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight();
			Shape shape = new RoundRectangle2D.Double(0, 0, width, height, 16, 16);
			g.clip(shape);

			BufferedImage background = background();

			if (background != null) {
				// Cover: fill the banner, cropping the excess rather than squashing it.
				double scale = Math.max(width / (double) background.getWidth(), height / (double) background.getHeight());
				int drawWidth = (int) Math.ceil(background.getWidth() * scale);
				int drawHeight = (int) Math.ceil(background.getHeight() * scale);
				g.drawImage(background, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
				// Darker on the left, where the name and the chip sit, so any wallpaper stays readable.
				g.setPaint(new java.awt.LinearGradientPaint(0, 0, width, 0,
					new float[] { 0f, 0.45f, 1f },
					new Color[] { new Color(8, 4, 16, 225), new Color(8, 4, 16, 140), new Color(8, 4, 16, 40) }));
				g.fillRect(0, 0, width, height);
			} else {
				// Dark violet glass rather than a bright wash: the figure has to stay the bright thing.
				g.setPaint(new GradientPaint(0, 0, new Color(0x2A1040), width, height, Theme.WINDOW));
				g.fillRect(0, 0, width, height);
				g.setPaint(new java.awt.RadialGradientPaint(width * 0.82f, height * 0.42f, Math.max(width, height) * 0.48f,
					new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_DEEP, 120), new Color(0, 0, 0, 0) }));
				g.fillRect(0, 0, width, height);
			}

			// A dark foot, so the chip and text stay readable on any background image.
			g.setPaint(new GradientPaint(0, height * 0.45f, new Color(0, 0, 0, 0), 0, height, new Color(6, 3, 12, 220)));
			g.fillRect(0, (int) (height * 0.45f), width, height);

			// Faint speckle, the same texture the client's menus use.
			g.setColor(new Color(255, 255, 255, 12));

			for (int x = 18; x < width; x += 34) {
				for (int y = 14; y < height; y += 34) {
					g.fillRect(x, y, 2, 2);
				}
			}

			int figureHeight = (int) (height * 0.78);
			double unit = figureHeight / 32.0;
			int figureWidth = (int) Math.round(16 * unit);
			int figureX = width - figureWidth - 74;
			int figureY = height - figureHeight - 18;

			g.setPaint(new java.awt.RadialGradientPaint(figureX + figureWidth / 2f, figureY + figureHeight * 0.55f, figureWidth,
				new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_ALT, 105), new Color(0, 0, 0, 0) }));
			g.fillOval(figureX - figureWidth / 2, figureY - 10, figureWidth * 2, figureHeight);

			PlayerSkin.paint(g, skin, figureX, figureY, figureHeight);

			// The name sits next to the figure, the way a profile banner does.
			g.setFont(Theme.bold(26));
			FontMetrics metrics = g.getFontMetrics();
			String name = app.config.activeAccount().name;
			int nameWidth = metrics.stringWidth(name);
			int nameX = Math.max(22, figureX - nameWidth - 26);
			g.setColor(new Color(0, 0, 0, 120));
			g.drawString(name, nameX + 2, figureY + 40);
			g.setColor(Theme.TEXT);
			g.drawString(name, nameX, figureY + 38);

			g.setFont(Theme.font(12));
			g.setColor(Theme.TEXT_DIM);
			String status = app.isRunning() ? "in game" : (app.isBusy() ? "starting…" : "ready");
			g.drawString(app.config.active().minecraftVersion + "  ·  " + status, nameX, figureY + 58);

			g.setColor(Theme.alpha(Theme.ACCENT_ALT, 110));
			g.draw(shape);
			g.dispose();
		}
	}

	private BufferedImage background() {
		String configured = app.config.backgroundImage;

		if (configured == null || configured.isBlank()) {
			return null;
		}

		Path file = Path.of(configured);

		if (!Files.isRegularFile(file)) {
			return null;
		}

		try (InputStream stream = Files.newInputStream(file)) {
			return ImageIO.read(stream);
		} catch (Exception e) {
			return null;
		}
	}

	/** The account pill: face, name, kind — and a menu of the other accounts on click. */
	private final class AccountChip extends JComponent {
		private Account account;
		private boolean hover;

		private AccountChip() {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Switch account, or sign in with Microsoft in Settings");
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					openMenu(event);
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

		private void setAccount(Account value) {
			account = value;
			repaint();
		}

		private void openMenu(MouseEvent event) {
			JPopupMenu menu = new JPopupMenu();
			menu.setBackground(Theme.PANEL_HEADER);
			menu.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.PANEL_OUTLINE));

			for (Account candidate : app.config.accounts) {
				JMenuItem item = new JMenuItem(candidate.name + (candidate.online() ? "  ·  Microsoft" : ""));
				item.setFont(Theme.font(13));
				item.setOpaque(true);
				item.setBackground(Theme.PANEL_HEADER);
				item.setForeground(candidate.name.equalsIgnoreCase(app.config.activeAccount) ? Theme.ACCENT_ALT : Theme.TEXT);
				item.addActionListener(action -> {
					app.config.activeAccount = candidate.name;
					app.config.save();
					skinKey = "";
					app.status("Playing as " + candidate.name);
					app.refresh();
				});
				menu.add(item);
			}

			menu.addSeparator();
			JMenuItem manage = new JMenuItem("Add account or sign in…");
			manage.setFont(Theme.font(13));
			manage.setOpaque(true);
			manage.setBackground(Theme.PANEL_HEADER);
			manage.setForeground(Theme.TEXT_ACCENT);
			manage.addActionListener(action -> app.showPage("settings"));
			menu.add(manage);
			menu.show(this, 0, -menu.getPreferredSize().height - 4);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setColor(new Color(12, 7, 22, hover ? 235 : 205));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 21);
			g.setColor(Theme.alpha(Theme.ACCENT_ALT, hover ? 200 : 120));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 21, 21);

			int face = 28;
			PlayerSkin.paintFace(g, account == null ? null : PlayerSkin.peek(account), 7, 7, face);

			g.setFont(Theme.bold(13));
			g.setColor(Theme.TEXT);
			String name = account == null ? "Player" : account.name;
			g.drawString(name, 7 + face + 10, 24);

			g.setFont(Theme.font(10));
			g.setColor(Theme.TEXT_DIM);
			g.drawString(account == null ? "" : account.kind(), 7 + face + 10, 35);

			// Chevron, drawn rather than an icon font.
			int size = 7;
			int x = getWidth() - 20;
			int y = getHeight() / 2 - 3;
			g.setColor(Theme.TEXT_ACCENT);
			g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
			g.drawLine(x, y, x + size / 2, y + size / 2);
			g.drawLine(x + size / 2, y + size / 2, x + size, y);
			g.dispose();
		}
	}

	private static BufferedImage gem() {
		try (InputStream stream = HomePage.class.getResourceAsStream("/icon.png")) {
			if (stream != null) {
				return ImageIO.read(stream);
			}
		} catch (Exception ignored) {
			// Drawn fallback below.
		}

		return null;
	}
}
