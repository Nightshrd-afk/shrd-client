package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Instance;
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
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The home screen, laid out like a profile page rather than a settings form: one wide banner with
 * the player's own skin on it, the account chip along the bottom, then the profiles you last played,
 * each with its own Play button.
 * <p>
 * The banner carries three small pieces of chrome of its own — the name plate that opens the Instances
 * page, a card for the bundled client, and the button that jumps to Customisation — so the things a
 * player reaches for most are one click from the page they land on.
 */
public final class HomePage extends Page {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
	private static final BufferedImage GEM = gem();

	// Declared before the hero, which puts them on itself while it is being constructed.
	private final AccountChip accountChip = new AccountChip();
	private final Ui.Button updatePill = Ui.primary("Update available").size(178, 36);
	private final NamePlate namePlate = new NamePlate();
	private final ClientCard clientCard = new ClientCard();
	private final Ui.Button customise = Ui.secondary("").size(40, 40);
	private final Hero hero = new Hero();
	private final JPanel profiles = Ui.column();
	private String skinKey = "";

	public HomePage(App app) {
		super(app, "Home", "Play Minecraft, with Shrd Client installed for you");

		JPanel column = Ui.column();
		column.add(hero);
		column.add(Ui.gap(20));
		column.add(sectionHeader());
		column.add(Ui.gap(12));
		column.add(profiles);
		column.add(Box.createVerticalGlue());

		body(column);
		body().add(bottomBar(), BorderLayout.SOUTH);

		updatePill.onClick(button -> app.updateNow());
		customise.withGlyph(Icons::palette);
		customise.setToolTipText("Change the launcher's colour and background");
		customise.onClick(button -> app.showPage("customise"));

		app.onStage((stage, detail) -> refreshStage());
		app.onRefresh(this::reload);
	}

	private JComponent sectionHeader() {
		JPanel row = Ui.row(new BorderLayout(12, 0));
		row.add(Ui.heading("Last played profiles"), BorderLayout.WEST);
		row.add(new EditionToggle(), BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return row;
	}

	private JPanel bottomBar() {
		JPanel bar = Ui.row();
		bar.setBorder(new EmptyBorder(16, 0, 0, 0));

		Ui.Button manage = Ui.primary("Manage all profiles").size(360, 46).withGlyph(Icons::instances);
		manage.onClick(button -> app.showPage("instances"));
		bar.add(Ui.fill(manage));
		bar.add(Ui.hgap(12));

		Ui.Button mods = Ui.secondary("Add mods").size(260, 46).withGlyph(Icons::mods);
		mods.onClick(button -> app.showPage("mods"));
		bar.add(mods);
		return bar;
	}

	private JPanel profileRow(Instance instance) {
		boolean active = instance.name.equalsIgnoreCase(app.config.activeInstance);
		Ui.Row row = new Ui.Row();
		row.hoverable();
		row.setSelected(active);
		row.setLayout(new BorderLayout(16, 0));

		row.add(new IconTile(58), BorderLayout.WEST);

		JPanel titles = Ui.column();
		JLabel name = new JLabel(instance.name);
		name.setFont(Theme.bold(16));
		name.setForeground(Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		titles.add(name);
		titles.add(Ui.gap(4));

		String loader = instance.isFabric()
			? "Fabric " + instance.minecraftVersion + (instance.loaderVersion.isBlank() ? "" : "  ·  loader " + instance.loaderVersion)
			: "Vanilla " + instance.minecraftVersion;
		titles.add(Ui.dim(loader + "  ·  "
			+ (instance.lastPlayed == 0 ? "never played" : WHEN.format(Instant.ofEpochMilli(instance.lastPlayed)))));
		row.add(titles, BorderLayout.CENTER);

		JPanel controls = Ui.row();
		boolean running = app.isRunning() && active;
		Ui.Button play = Ui.primary(running ? "Stop" : "Play").size(112, 40);
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

		Ui.Button gear = Ui.secondary("").size(40, 40);
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
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
		return row;
	}

	/** Java now, Bedrock never: shown the way the launcher's neighbours do, with the half it does not
	 * support greyed rather than hidden, so the limit is visible instead of surprising. */
	private final class EditionToggle extends JComponent {
		private EditionToggle() {
			setPreferredSize(new Dimension(206, 30));
			setMaximumSize(new Dimension(206, 30));
			setToolTipText("Java Edition only — Shrd Launcher does not run Bedrock Edition");
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setColor(Theme.TRACK);
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 15);
			g.setColor(Theme.PANEL_OUTLINE);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);

			int half = getWidth() / 2;

			g.setColor(Theme.ACCENT);
			Ui.roundRect(g, 3, 3, half - 4, getHeight() - 6, 12);
			paintSegment(g, "Java", half - 4, Theme.ON_ACCENT, Theme.bold(12));
			paintSegment(g, "Bedrock", getWidth() - half - 4, Theme.TEXT_DISABLED, Theme.font(12));
			g.dispose();
		}

		private void paintSegment(Graphics2D g, String text, int width, Color color, java.awt.Font font) {
			g.setFont(font);
			g.setColor(color);
			int offset = text.equals("Java") ? 3 : getWidth() / 2 + 2;
			int x = offset + (width - g.getFontMetrics().stringWidth(text)) / 2;
			g.drawString(text, x, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
		}
	}

	/** A rounded tile with the shrd gem on it, so a profile row reads as a profile. */
	private static final class IconTile extends JComponent {
		private IconTile(int size) {
			setPreferredSize(new Dimension(size, size));
			setMaximumSize(new Dimension(size, size));
			setMinimumSize(new Dimension(size, size));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setPaint(new GradientPaint(0, 0, Theme.PANEL_HEADER, getWidth(), getHeight(), Theme.TRACK));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 12);
			g.setColor(Theme.alpha(Theme.ACCENT, 110));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

			if (GEM != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(GEM, 10, 10, getWidth() - 20, getHeight() - 20, null);
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

	// ------------------------------------------------------------------ the banner

	/** The banner: background image or gradient, the player's figure, and the page's own chrome. */
	private final class Hero extends JPanel {
		private BufferedImage skin;

		private Hero() {
			setLayout(null);
			setAlignmentX(LEFT_ALIGNMENT);
			setPreferredSize(new Dimension(10, 320));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
			add(namePlate);
			add(clientCard);
			add(accountChip);
			add(updatePill);
			add(customise);
			updatePill.setVisible(false);
		}

		private void setSkin(BufferedImage image) {
			skin = image;
			repaint();
		}

		/** Where the figure stands: right of centre, clear of the corners the chrome sits in. */
		private Rectangle figureBox() {
			int height = (int) (getHeight() * 0.76);
			double unit = height / 32.0;
			int width = (int) Math.round(16 * unit);
			return new Rectangle(getWidth() - width - 92, getHeight() - height - 16, width, height);
		}

		@Override
		public void doLayout() {
			Rectangle figure = figureBox();
			namePlate.setBounds(Math.max(24, figure.x - 220), 34, 200, 54);
			clientCard.setBounds(24, getHeight() - 158, 216, 128);
			accountChip.setBounds(Math.max(268, (getWidth() - 254) / 2), getHeight() - 58, 254, 42);
			updatePill.setBounds(getWidth() - 202, 24, 178, 36);
			customise.setBounds(getWidth() - 60, getHeight() - 54, 40, 40);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight();
			Shape shape = new RoundRectangle2D.Double(0, 0, width, height, 18, 18);
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
				// Violet glass rather than a flat wash: the accent is mixed into the gradient, so a
				// crimson or emerald theme gets its own banner instead of the purple one.
				g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.ACCENT_DEEP, new Color(0x1B0A2E), 0.42f), width, height, Theme.WINDOW));
				g.fillRect(0, 0, width, height);
				// A broad highlight from the left, so the side the chrome sits on is not a dead corner.
				g.setPaint(new java.awt.RadialGradientPaint(width * 0.3f, height * 0.3f, width * 0.6f,
					new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT, 78), new Color(0, 0, 0, 0) }));
				g.fillRect(0, 0, width, height);
				g.setPaint(new java.awt.RadialGradientPaint(width * 0.78f, height * 0.45f, Math.max(width, height) * 0.45f,
					new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_ALT, 95), new Color(0, 0, 0, 0) }));
				g.fillRect(0, 0, width, height);
			}

			// The same plus-sign texture the pages sit on, at a lower strength: it ties the banner to
			// the window without competing with the artwork.
			Ui.hatch(g, 0, 0, width, height, 0.05f);

			// A dark foot, so the chip and the card stay readable on any background image.
			g.setPaint(new GradientPaint(0, height * 0.45f, new Color(0, 0, 0, 0), 0, height, new Color(6, 3, 12, 225)));
			g.fillRect(0, (int) (height * 0.45f), width, height);

			Rectangle figure = figureBox();
			g.setPaint(new java.awt.RadialGradientPaint(figure.x + figure.width / 2f, figure.y + figure.height * 0.55f, figure.width,
				new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_ALT, 110), new Color(0, 0, 0, 0) }));
			g.fillOval(figure.x - figure.width / 2, figure.y - 10, figure.width * 2, figure.height);

			PlayerSkin.paint(g, skin, figure.x, figure.y, figure.height);

			g.setColor(Theme.alpha(Theme.ACCENT_ALT, 110));
			g.draw(shape);
			g.dispose();
		}
	}

	/** The shared cache, so a photo is decoded once rather than on every repaint. */
	private BufferedImage background() {
		return dev.shrd.launcher.ui.Backgrounds.of(app.config.backgroundImage);
	}

	/** The player's name over the banner; clicking it opens the profiles it belongs to. */
	private final class NamePlate extends JComponent {
		private boolean hover;

		private NamePlate() {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Open the Instances page");
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					app.showPage("instances");
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
			g.setColor(new Color(9, 4, 18, hover ? 215 : 175));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 12);
			g.setColor(Theme.alpha(Theme.ACCENT_ALT, hover ? 210 : 130));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

			g.setFont(Theme.bold(19));
			g.setColor(Theme.TEXT);
			g.drawString(Ui.elide(app.config.activeAccount().name, 15), 14, 26);

			g.setFont(Theme.font(11));
			g.setColor(hover ? Theme.TEXT : Theme.TEXT_ACCENT);
			g.drawString("Go to profile", 14, 44);
			g.drawString("→", 14 + g.getFontMetrics().stringWidth("Go to profile") + 5, 44);
			g.dispose();
		}
	}

	/** What the client is: the launcher ships it in every profile, and this card is the way there. */
	private final class ClientCard extends JComponent {
		private boolean hover;

		private ClientCard() {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Shrd Client is installed into every profile you launch");
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					app.showPage("mods");
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
			g.setColor(new Color(11, 5, 22, 225));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 14);
			g.setColor(Theme.alpha(Theme.ACCENT, hover ? 220 : 130));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

			if (GEM != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(GEM, 14, 14, 36, 36, null);
			}

			g.setFont(Theme.bold(15));
			g.setColor(Theme.TEXT);
			g.drawString("Shrd Client", 60, 30);

			g.setFont(Theme.font(11));
			g.setColor(Theme.TEXT_DIM);
			g.drawString("v" + dev.shrd.launcher.Version.CURRENT + "  ·  in every profile", 60, 46);

			g.setColor(Theme.alpha(Theme.ACCENT, 70));
			g.drawLine(14, getHeight() - 34, getWidth() - 14, getHeight() - 34);

			g.setFont(Theme.bold(10));
			g.setColor(hover ? Theme.TEXT : Theme.TEXT_ACCENT);
			g.drawString("MANAGE MODS", 14, getHeight() - 13);
			g.drawString("→", 14 + g.getFontMetrics().stringWidth("MANAGE MODS") + 6, getHeight() - 13);
			g.dispose();
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

			if (account == null || !account.online()) {
				JMenuItem signIn = new JMenuItem("Sign in with Microsoft…");
				signIn.setFont(Theme.bold(13));
				signIn.setOpaque(true);
				signIn.setBackground(Theme.PANEL_HEADER);
				signIn.setForeground(Theme.ACCENT_ALT);
				signIn.addActionListener(action -> dev.shrd.launcher.ui.SignInDialog.show(this, app));
				menu.add(signIn);
			}

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
			g.setColor(new Color(12, 7, 22, hover ? 240 : 215));
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 21);
			g.setColor(Theme.alpha(Theme.ACCENT_ALT, hover ? 210 : 130));
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
