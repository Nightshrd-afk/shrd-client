package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.auth.MicrosoftAuth;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The sign-in window, laid out like a launcher's front door rather than a settings dialog: the
 * product's name sits over the top edge of one dark card, the card holds a single large button, and
 * everything else — the code, the waiting, the account — happens inside that same card.
 * <p>
 * Device code rather than an embedded browser on purpose: Microsoft's login page is the only place a
 * password is ever typed, and it is a page the player already trusts. Once Microsoft answers, the
 * same chain the official launcher walks runs here (Xbox Live → XSTS → Minecraft services →
 * profile), so what comes back is the real Java Edition account: Minecraft name, Mojang UUID, skin
 * and an access token. That account is then made active, which is what lets it join online servers.
 */
public final class SignInDialog {

	/** Window and card geometry. Fixed, so every state lines up on the same grid. */
	private static final int WINDOW_W = 900;
	private static final int WINDOW_H = 620;
	private static final int CARD_W = 620;
	private static final int PAD = 48;
	private static final int CONTENT_W = CARD_W - PAD * 2;
	/** Where the card's top edge sits, and where its contents start, in card coordinates. */
	private static final int EDGE = 44;
	private static final int TOP = 96;
	private static final int BOTTOM = 32;

	private static final String SIGN_UP = "https://www.minecraft.net/en-us";
	private static final BufferedImage GEM = gem();

	private SignInDialog() {
	}

	/** Shows the window, modal to {@code owner}. */
	public static void show(Component owner, App app) {
		Window parent = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		JDialog dialog = new JDialog(parent, "Sign in to Minecraft", JDialog.ModalityType.APPLICATION_MODAL);
		dialog.setUndecorated(true);

		Frame frame = new Frame(app, dialog);
		dialog.setContentPane(frame);
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.getRootPane().registerKeyboardAction(
			event -> frame.close(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		frame.start();
		dialog.setVisible(true);
	}

	/** The window's contents on their own, so {@code --shot login} can render it without a window. */
	public static JComponent panel(App app) {
		Frame frame = new Frame(app, null);
		frame.start();
		return frame;
	}

	// ------------------------------------------------------------------ the window

	private static final class Frame extends JPanel {
		private final App app;
		private final JDialog dialog;
		private final JPanel content = Ui.column();
		private final Sheet sheet = new Sheet();
		private final JLabel footerNote = Ui.dim("");
		private Hero hero;

		private Frame(App app, JDialog dialog) {
			this.app = app;
			this.dialog = dialog;
			setLayout(new BorderLayout());
			setOpaque(true);
			setBackground(Theme.WINDOW);
			setBorder(javax.swing.BorderFactory.createLineBorder(Theme.WINDOW_EDGE));
			setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));

			content.setOpaque(false);
			content.setAlignmentX(CENTER_ALIGNMENT);
			sheet.add(content, BorderLayout.CENTER);

			JPanel stage = new JPanel(new GridBagLayout());
			stage.setOpaque(false);
			stage.add(sheet);

			add(chrome(), BorderLayout.NORTH);
			add(stage, BorderLayout.CENTER);
			add(footer(), BorderLayout.SOUTH);

			drag(chrome());
			drag(stage);
			drag(sheet);
			drag(content);
			drag(this);
		}

		// -------------------------------------------------------------- chrome

		private JPanel chrome() {
			JPanel bar = new JPanel(new BorderLayout(12, 0));
			bar.setOpaque(false);
			bar.setBorder(new EmptyBorder(14, 18, 6, 14));

			// Same corner chip the official launcher uses for its language picker: here it carries
			// the product and the build, which is the one thing this window can state as fact.
			JPanel chip = new JPanel(new BorderLayout(10, 0));
			chip.setOpaque(false);
			chip.setBorder(javax.swing.BorderFactory.createCompoundBorder(
				new javax.swing.border.LineBorder(Theme.alpha(Theme.ACCENT, 70), 1, true),
				new EmptyBorder(6, 9, 6, 12)));

			if (GEM != null) {
				JComponent mark = new JComponent() {
					@Override
					protected void paintComponent(Graphics graphics) {
						Graphics2D g = (Graphics2D) graphics.create();
						Ui.hints(g);
						g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
							java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						g.drawImage(GEM, 0, 0, getWidth(), getHeight(), null);
						g.dispose();
					}
				};
				mark.setPreferredSize(new Dimension(18, 18));
				chip.add(mark, BorderLayout.WEST);
			}

			JPanel words = Ui.column();
			JLabel name = Ui.caps("shrd launcher");
			name.setForeground(Theme.TEXT_ACCENT);
			words.add(name);
			words.add(Ui.gap(1));
			words.add(Ui.dim("v" + dev.shrd.launcher.Version.CURRENT + "  ·  windows x64"));
			chip.add(words, BorderLayout.CENTER);

			bar.add(chip, BorderLayout.WEST);
			bar.add(new Close(), BorderLayout.EAST);
			return bar;
		}

		private JPanel footer() {
			JPanel bar = new JPanel(new BorderLayout(12, 0));
			bar.setOpaque(false);
			bar.setBorder(new EmptyBorder(4, 22, 16, 22));

			footerNote.setForeground(Theme.TEXT_DISABLED);
			bar.add(footerNote, BorderLayout.WEST);
			JLabel right = Ui.dim("Minecraft: Java Edition");
			right.setForeground(Theme.TEXT_DISABLED);
			bar.add(right, BorderLayout.EAST);
			return bar;
		}

		/** Which state the card opens in: needs an app id, ready, or already signed in. */
		private void start() {
			content.removeAll();
			Account account = app.config.activeAccount();

			if (account != null && account.online()) {
				signedIn(account);
				return;
			}

			if (app.config.microsoftClientId == null || app.config.microsoftClientId.isBlank()) {
				needsClientId();
			} else {
				ready();
			}
		}

		// -------------------------------------------------------------- states

		private void reset(String heading, String subtitle) {
			content.removeAll();
			content.add(centered(heading, Theme.bold(20), Theme.TEXT));
			content.add(Ui.gap(6));
			content.add(centered(subtitle, Theme.font(13), Theme.TEXT_DIM));
		}

		private void needsClientId() {
			reset("One-time setup", "Microsoft needs an app id before it can sign anyone in");
			content.add(Ui.gap(20));

			content.add(step("1", "portal.azure.com — App registrations → New registration"));
			content.add(Ui.gap(7));
			content.add(step("2", "Authentication → Allow public client flows → Yes"));
			content.add(Ui.gap(7));
			content.add(step("3", "Copy the Application (client) ID below"));
			content.add(Ui.gap(18));

			Ui.Field field = new Ui.Field("Application (client) ID");
			field.setMaximumSize(new Dimension(CONTENT_W, 36));
			field.setPreferredSize(new Dimension(CONTENT_W, 36));
			field.setAlignmentX(CENTER_ALIGNMENT);
			field.setText(app.config.microsoftClientId == null ? "" : app.config.microsoftClientId);
			content.add(field);
			content.add(Ui.gap(14));

			JPanel buttons = Ui.row();
			Ui.Button save = Ui.primary("Save and continue").size(206, 40);
			save.onClick(button -> {
				String id = field.getText().trim();

				if (id.isBlank()) {
					app.status("Paste the client id first");
					return;
				}

				app.config.microsoftClientId = id;
				app.config.save();
				ready();
			});
			buttons.add(save);
			buttons.add(Ui.hgap(10));
			buttons.add(Ui.secondary("Open Azure").size(150, 40).onClick(button -> open(MicrosoftAuth.CLIENT_HELP)));
			buttons.add(Box.createHorizontalGlue());
			buttons.setAlignmentX(CENTER_ALIGNMENT);
			content.add(buttons);
			content.add(Ui.gap(14));
			content.add(centered("It is public, not a secret — it is sent to Microsoft in the address bar.",
				Theme.font(11), Theme.TEXT_DISABLED));
			content.add(Box.createVerticalGlue());
			footerNote.setText("Stored in the launcher's config file on this PC");
			finish();
		}

		private void ready() {
			reset("Minecraft: Java Edition", "Sign in once — the launcher keeps the account after that");
			content.add(Ui.gap(26));
			content.add(new SignIn());
			content.add(Ui.gap(14));
			content.add(new SignUp());
			content.add(Ui.gap(22));
			content.add(centered("Your Java Edition name, UUID and skin load automatically when Microsoft",
				Theme.font(11), Theme.TEXT_DISABLED));
			content.add(Ui.gap(2));
			content.add(centered("answers, and the account is set as the one Play uses.",
				Theme.font(11), Theme.TEXT_DISABLED));
			content.add(Box.createVerticalGlue());
			footerNote.setText("Microsoft sign-in  ·  no password ever reaches this app");
			finish();
		}

		private void waiting(MicrosoftAuth.Device device) {
			reset("Approve the sign-in", "Microsoft's page is open in your browser, with the code ready");
			content.add(Ui.gap(22));

			JLabel code = centered(device.userCode(), Theme.bold(44), Theme.ACCENT_ALT);
			content.add(code);
			content.add(Ui.gap(6));
			content.add(centered(device.verificationUri(), Theme.font(12), Theme.TEXT_DIM));
			content.add(Ui.gap(20));

			JPanel buttons = Ui.row();
			Ui.Button open = Ui.primary("Open Microsoft's page").size(230, 40);
			open.onClick(button -> open(device.verificationUri()));
			buttons.add(open);
			buttons.add(Ui.hgap(10));
			Ui.Button copy = Ui.secondary("Copy code").size(150, 40);
			copy.onClick(button -> {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(device.userCode()), null);
				app.status("Code copied");
			});
			buttons.add(copy);
			buttons.add(Box.createHorizontalGlue());
			buttons.setAlignmentX(CENTER_ALIGNMENT);
			content.add(buttons);
			content.add(Ui.gap(18));
			content.add(centered("Waiting for Microsoft — this window finishes by itself.", Theme.font(12), Theme.TEXT_DIM));
			content.add(Box.createVerticalGlue());
			footerNote.setText("The code expires in " + (device.expiresIn() / 60) + " minutes  ·  press Sign in again for a new one");
			finish();
		}

		private void signedIn(Account account) {
			reset("You're signed in", "Microsoft confirmed, and the Java Edition account answered");
			content.add(Ui.gap(18));

			hero = new Hero(PlayerSkin.cached(account));
			JPanel row = Ui.row(new BorderLayout(22, 0));
			row.add(hero, BorderLayout.WEST);

			JPanel details = Ui.column();
			JLabel name = Ui.text(account.name);
			name.setFont(Theme.bold(30));
			name.setForeground(Theme.TEXT);
			name.setAlignmentX(LEFT_ALIGNMENT);
			details.add(name);
			details.add(Ui.gap(6));
			details.add(Ui.accent("Java Edition  ·  active account"));
			details.add(Ui.gap(8));
			JLabel uuid = Ui.dim(account.uuid());
			uuid.setFont(Theme.mono(11));
			uuid.setAlignmentX(LEFT_ALIGNMENT);
			details.add(uuid);
			details.add(Ui.gap(10));
			JLabel note = Ui.dim("<html><body style='width:250px'>Servers that verify accounts — Donut SMP and most public ones — take this name now.</body></html>");
			note.setAlignmentX(LEFT_ALIGNMENT);
			details.add(note);

			// Centred against the figure rather than pinned to the top of the row.
			JPanel middle = new JPanel(new GridBagLayout());
			middle.setOpaque(false);
			middle.add(details);
			row.add(middle, BorderLayout.CENTER);
			row.setAlignmentX(CENTER_ALIGNMENT);
			content.add(row);
			content.add(Ui.gap(22));

			JPanel buttons = Ui.row();
			Ui.Button done = Ui.primary("Done").size(180, 40);
			done.onClick(button -> close());
			buttons.add(done);
			buttons.add(Ui.hgap(10));
			Ui.Button play = Ui.secondary("Play now").size(170, 40);
			play.onClick(button -> {
				close();
				app.play();
			});
			buttons.add(play);
			buttons.add(Box.createHorizontalGlue());
			buttons.setAlignmentX(CENTER_ALIGNMENT);
			content.add(buttons);
			content.add(Box.createVerticalGlue());
			footerNote.setText("Signed in as " + account.name + "  ·  Play uses this account");
			finish();
			loadSkin(account);
		}

		private void failed(String message) {
			reset("That did not work", "Nothing changed — you can try again");
			footerNote.setText("No account was added or changed");
			content.add(Ui.gap(18));

			JLabel text = Ui.dim("<html><body style='width:" + (CONTENT_W - 40) + "px;text-align:center'>"
				+ escape(message) + "</body></html>");
			text.setHorizontalAlignment(JLabel.CENTER);
			text.setAlignmentX(CENTER_ALIGNMENT);
			content.add(text);
			content.add(Ui.gap(20));

			JPanel buttons = Ui.row();
			Ui.Button again = Ui.primary("Try again").size(170, 40);
			again.onClick(button -> ready());
			buttons.add(again);
			buttons.add(Ui.hgap(10));
			Ui.Button close = Ui.secondary("Close").size(130, 40);
			close.onClick(button -> close());
			buttons.add(close);
			buttons.add(Box.createHorizontalGlue());
			buttons.setAlignmentX(CENTER_ALIGNMENT);
			content.add(buttons);
			content.add(Box.createVerticalGlue());
			finish();
		}

		// -------------------------------------------------------------- the flow

		private void startFlow() {
			String clientId = app.config.microsoftClientId.trim();

			Thread worker = new Thread(() -> {
				try {
					MicrosoftAuth.Device device = MicrosoftAuth.begin(clientId);

					SwingUtilities.invokeLater(() -> {
						Toolkit.getDefaultToolkit().getSystemClipboard()
							.setContents(new StringSelection(device.userCode()), null);
						waiting(device);
						open(device.verificationUri());
					});

					Account account = MicrosoftAuth.complete(
						MicrosoftAuth.await(clientId, device, message -> {
						}),
						message -> {
						});

					SwingUtilities.invokeLater(() -> {
						PlayerSkin.forget(account);
						app.installAccount(account);
						signedIn(account);
					});
				} catch (Exception e) {
					String message = e.getMessage() == null ? e.toString() : e.getMessage();
					SwingUtilities.invokeLater(() -> failed(message));
				}
			}, "shrd-signin-ui");

			worker.setDaemon(true);
			worker.start();
		}

		/** Reads the skin off the event thread, so the welcome card shows the real character. */
		private void loadSkin(Account account) {
			Thread loader = new Thread(() -> {
				BufferedImage skin = PlayerSkin.load(account);
				SwingUtilities.invokeLater(() -> {
					if (hero != null) {
						hero.setSkin(skin);
					}
				});
			}, "shrd-signin-skin");
			loader.setDaemon(true);
			loader.start();
		}

		private void open(String url) {
			try {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
			} catch (Exception e) {
				app.status("Could not open a browser — copy the address instead");
			}
		}

		private void close() {
			if (dialog != null) {
				dialog.dispose();
			}
		}

		// -------------------------------------------------------------- pieces

		/** Gives the card the height its current state needs, then re-packs the window. */
		private void finish() {
			content.setPreferredSize(new Dimension(CONTENT_W, content.getPreferredSize().height));
			content.setMaximumSize(new Dimension(CONTENT_W, Integer.MAX_VALUE));
			sheet.setPreferredSize(new Dimension(CARD_W, TOP + content.getPreferredSize().height + BOTTOM));
			content.revalidate();
			content.repaint();
			sheet.revalidate();
			sheet.repaint();
			revalidate();
			repaint();
		}

		private JLabel centered(String text, Font font, Color color) {
			JLabel label = new JLabel(text);
			label.setFont(font);
			label.setForeground(color);
			label.setAlignmentX(CENTER_ALIGNMENT);
			label.setHorizontalAlignment(JLabel.CENTER);
			return label;
		}

		private JComponent step(String number, String text) {
			JPanel row = Ui.row(new BorderLayout(12, 0));
			JLabel badge = new JLabel(number);
			badge.setFont(Theme.bold(11));
			badge.setForeground(Theme.ON_ACCENT);
			badge.setHorizontalAlignment(JLabel.CENTER);
			JComponent disc = new JComponent() {
				@Override
				protected void paintComponent(Graphics graphics) {
					Graphics2D g = (Graphics2D) graphics.create();
					Ui.hints(g);
					g.setColor(Theme.ACCENT);
					g.fillOval(0, (getHeight() - 19) / 2, 19, 19);
					g.setColor(Theme.ON_ACCENT);
					g.setFont(Theme.bold(11));
					g.drawString(number, 7, getHeight() / 2 + 4);
					g.dispose();
				}
			};
			disc.setPreferredSize(new Dimension(19, 19));
			row.add(disc, BorderLayout.WEST);
			row.add(Ui.dim(text), BorderLayout.CENTER);
			row.setAlignmentX(CENTER_ALIGNMENT);
			row.setMaximumSize(new Dimension(CONTENT_W, 26));
			row.setPreferredSize(new Dimension(CONTENT_W, 26));
			return row;
		}

		/** The one big button: the whole point of this screen, so it is drawn large. */
		private final class SignIn extends JComponent {
			private boolean hover;
			private boolean pressed;

			private SignIn() {
				setPreferredSize(new Dimension(CONTENT_W, 54));
				setMaximumSize(new Dimension(CONTENT_W, 54));
				setAlignmentX(CENTER_ALIGNMENT);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setToolTipText("Sign in with the Microsoft account that owns Minecraft");
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent event) {
						hover = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent event) {
						hover = false;
						pressed = false;
						repaint();
					}

					@Override
					public void mousePressed(MouseEvent event) {
						pressed = true;
						repaint();
					}

					@Override
					public void mouseReleased(MouseEvent event) {
						boolean wasPressed = pressed;
						pressed = false;
						repaint();

						if (wasPressed && contains(event.getPoint())) {
							startFlow();
						}
					}
				});
			}

			@Override
			protected void paintComponent(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				Ui.hints(g);
				int width = getWidth();
				int height = getHeight();

				// A soft drop shadow in the accent, the way a primary action sits above the card.
				g.setColor(Theme.alpha(Theme.ACCENT_DEEP, 120));
				g.fillRoundRect(2, 5, width - 4, height - 6, 14, 14);

				Color top = pressed ? Theme.ACCENT : (hover ? Theme.ACCENT_ALT : Theme.ACCENT);
				Color bottom = pressed ? Theme.ACCENT_DEEP : (hover ? Theme.ACCENT : Theme.ACCENT_DEEP);
				g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
				g.fillRoundRect(0, 0, width, height - 1, 12, 12);

				g.setColor(Theme.alpha(Color.WHITE, hover ? 90 : 55));
				g.drawRoundRect(0, 0, width - 1, height - 2, 12, 12);

				int glyph = 17;
				int gap = 12;
				Font font = Theme.bold(15);
				FontMetrics metrics = g.getFontMetrics(font);
				float tracking = 2.6f;
				String label = "SIGN IN";
				int textWidth = trackedWidth(metrics, label, tracking);
				int start = (width - (glyph + gap + textWidth)) / 2;

				// Microsoft's four squares, so the button says who is asking.
				int cell = (glyph - 3) / 2;
				int gy = (height - glyph) / 2;
				g.setColor(new Color(255, 255, 255, 235));
				g.fillRect(start, gy, cell, cell);
				g.fillRect(start + cell + 3, gy, cell, cell);
				g.fillRect(start, gy + cell + 3, cell, cell);
				g.fillRect(start + cell + 3, gy + cell + 3, cell, cell);

				g.setFont(font);
				g.setColor(Color.WHITE);
				tracked(g, label, start + glyph + gap, (height - metrics.getHeight()) / 2 + metrics.getAscent() - 1, tracking);
				g.dispose();
			}
		}

		/** "Sign Up", in the place the official launcher puts it, opening Minecraft's own page. */
		private final class SignUp extends JComponent {
			private boolean hover;

			private SignUp() {
				setPreferredSize(new Dimension(120, 20));
				setMaximumSize(new Dimension(120, 20));
				setAlignmentX(CENTER_ALIGNMENT);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setToolTipText("Open minecraft.net");
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent event) {
						open(SIGN_UP);
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
				Font font = Theme.bold(12);
				FontMetrics metrics = g.getFontMetrics(font);
				String label = "Sign Up";
				int glyph = 9;
				int total = metrics.stringWidth(label) + 6 + glyph;
				int x = (getWidth() - total) / 2;
				int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

				g.setFont(font);
				g.setColor(hover ? Theme.ACCENT_ALT : Theme.TEXT_ACCENT);
				g.drawString(label, x, baseline);

				// The little "opens outside" arrow the official launcher draws beside it.
				int gx = x + metrics.stringWidth(label) + 6;
				int gy = baseline - glyph + 1;
				g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawRect(gx, gy + 3, glyph - 3, glyph - 3);
				g.drawLine(gx + 3, gy + 1, gx + glyph, gy + 1);
				g.drawLine(gx + glyph, gy + 1, gx + glyph, gy + 1 + (glyph - 4));
				g.dispose();
			}
		}

		/** The player's character, drawn with the real skin, standing beside the Java account details. */
		private final class Hero extends JComponent {
			private BufferedImage skin;

			private Hero(BufferedImage skin) {
				this.skin = skin;
				setOpaque(false);
				setPreferredSize(new Dimension(148, 196));
				setMaximumSize(new Dimension(148, 196));
				setMinimumSize(new Dimension(148, 196));
			}

			private void setSkin(BufferedImage image) {
				if (image != null) {
					skin = image;
					repaint();
				}
			}

			@Override
			protected void paintComponent(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				Ui.hints(g);
				int width = getWidth();
				int height = getHeight();
				g.setPaint(new RadialGradientPaint(width / 2f, height * 0.62f, width * 0.75f,
					new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT, 80), new Color(0, 0, 0, 0) }));
				g.fillRect(0, 0, width, height);

				int figureHeight = height - 10;
				int figureWidth = (int) Math.round(figureHeight * 16 / 32.0);
				PlayerSkin.paint(g, skin, (width - figureWidth) / 2, 6, figureHeight);
				g.dispose();
			}
		}

		private final class Close extends JComponent {
			private boolean hover;

			private Close() {
				setPreferredSize(new Dimension(24, 24));
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setToolTipText("Close (Esc)");
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent event) {
						close();
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
					g.setColor(Theme.alpha(Theme.BAD, 45));
					Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 5);
				}

				g.setColor(hover ? Theme.BAD : Theme.TEXT_DIM);
				g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				int pad = 8;
				g.drawLine(pad, pad, getWidth() - pad, getHeight() - pad);
				g.drawLine(getWidth() - pad, pad, pad, getHeight() - pad);
				g.dispose();
			}
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);

			// A cold, dark backdrop with one purple bloom behind the card: the same lighting the
			// in-game menus use, so the login does not look like a different product.
			g.setPaint(new GradientPaint(0, 0, new Color(0x0B0716), getWidth(), getHeight(), new Color(0x05030A)));
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setPaint(new RadialGradientPaint(getWidth() / 2f, getHeight() * 0.56f, Math.max(getWidth(), getHeight()) * 0.42f,
				new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_DEEP, 70), new Color(0, 0, 0, 0) }));
			g.fillRect(0, 0, getWidth(), getHeight());

			g.setColor(new Color(255, 255, 255, 9));

			for (int x = 24; x < getWidth(); x += 36) {
				for (int y = 24; y < getHeight(); y += 36) {
					g.fillRect(x, y, 2, 2);
				}
			}

			g.dispose();
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
					if (pressed != null && dialog != null) {
						Point onScreen = event.getLocationOnScreen();
						dialog.setLocation(onScreen.x - pressed.x, onScreen.y - pressed.y);
					}
				}
			};
			handle.addMouseListener(adapter);
			handle.addMouseMotionListener(adapter);
		}

		private static String escape(String text) {
			return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}
	}

	// ------------------------------------------------------------------ the card

	/**
	 * The card the whole screen is built around, plus the wordmark straddling its top edge — the one
	 * layout cue from the official launcher this window copies outright, because it instantly reads
	 * as "this is where you log in".
	 */
	private static final class Sheet extends JPanel {
		private Sheet() {
			setOpaque(false);
			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(TOP, PAD, BOTTOM, PAD));
			setPreferredSize(new Dimension(CARD_W, TOP + 200 + BOTTOM));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			int width = getWidth();
			int height = getHeight() - 6;

			// Glow under the card.
			g.setPaint(new RadialGradientPaint(width / 2f, height * 0.55f, width * 0.7f,
				new float[] { 0f, 1f }, new Color[] { Theme.alpha(Theme.ACCENT_DEEP, 60), new Color(0, 0, 0, 0) }));
			g.fillRect(0, 0, getWidth(), getHeight());

			g.setPaint(new GradientPaint(0, EDGE, new Color(0x1A0F2C), 0, height, new Color(0x0B0616)));
			g.fillRoundRect(0, EDGE, width, height - EDGE, 14, 14);

			g.setColor(Theme.alpha(Theme.ACCENT, 190));
			g.setStroke(new BasicStroke(1.6f));
			g.drawRoundRect(1, EDGE + 1, width - 2, height - EDGE - 2, 14, 14);

			// A hairline of light along the top edge, so the card has thickness.
			g.setColor(new Color(255, 255, 255, 16));
			g.drawLine(18, EDGE + 2, width - 18, EDGE + 2);

			wordmark(g, width);
			g.dispose();
		}

		/** "shrd" in the accent gradient, with the product line set against its baseline. */
		private void wordmark(Graphics2D g, int width) {
			Font big = Theme.bold(44);
			Font small = Theme.bold(11);
			FontMetrics bigMetrics = g.getFontMetrics(big);
			FontMetrics smallMetrics = g.getFontMetrics(small);
			String name = "shrd";
			String line = "LAUNCHER";
			float tracking = 3.4f;
			int nameWidth = bigMetrics.stringWidth(name);
			int lineWidth = trackedWidth(smallMetrics, line, tracking);
			int gap = 13;
			int start = (width - (nameWidth + gap + lineWidth)) / 2;
			int baseline = EDGE + 20;

			// Shadow, then the gradient itself.
			g.setFont(big);
			g.setColor(new Color(0, 0, 0, 120));
			g.drawString(name, start + 1, baseline + 2);
			g.setPaint(new GradientPaint(start, baseline - 34, Color.WHITE, start + nameWidth, baseline, Theme.ACCENT_ALT));
			g.drawString(name, start, baseline);

			g.setFont(small);
			g.setColor(Theme.TEXT_ACCENT);
			tracked(g, line, start + nameWidth + gap, baseline - 6, tracking);
		}
	}

	// ------------------------------------------------------------------ text helpers

	private static int trackedWidth(FontMetrics metrics, String text, float tracking) {
		float width = 0;

		for (int i = 0; i < text.length(); i++) {
			width += metrics.charWidth(text.charAt(i)) + tracking;
		}

		return Math.round(width - tracking);
	}

	private static void tracked(Graphics2D g, String text, int x, int baseline, float tracking) {
		float cursor = x;

		for (int i = 0; i < text.length(); i++) {
			String letter = String.valueOf(text.charAt(i));
			g.drawString(letter, Math.round(cursor), baseline);
			cursor += g.getFontMetrics().charWidth(text.charAt(i)) + tracking;
		}
	}

	private static BufferedImage gem() {
		try (java.io.InputStream stream = SignInDialog.class.getResourceAsStream("/icon.png")) {
			if (stream != null) {
				return ImageIO.read(stream);
			}
		} catch (Exception ignored) {
			// A missing icon just means the chip has no gem; the window still opens.
		}

		return null;
	}
}
