package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Instance;
import dev.shrd.launcher.ServerManager;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/**
 * A Minecraft server on this machine, for free. No account, no queue, no service that can close it
 * — it runs while this computer does. The page is explicit about that trade so nobody expects a
 * hosted 24/7 server from a button.
 */
public final class ServersPage extends Page {

	private final JLabel status = Ui.accent("");
	private final JLabel address = Ui.text("");
	private final JLabel lan = Ui.text("");
	private final JLabel mode = Ui.text("");
	private final JLabel folder = Ui.dim("");
	private final JLabel version = Ui.text("");
	private final Ui.Button startStop = Ui.primary("Start server").size(200, 38);
	private final Ui.Area console = new Ui.Area(12, 60);
	private final JLabel shareAddress = Ui.pageTitle("");
	private final JLabel shareNote = Ui.dim("");
	private final Ui.Button copyAddress = Ui.primary("Copy address").size(170, 34);
	private int renderedLines = -1;

	public ServersPage(App app) {
		super(app, "Servers", "A free Minecraft server that runs on this PC, for you and your friends");

		JPanel column = Ui.column();
		column.add(serverCard());
		column.add(Ui.gap(16));
		column.add(consoleCard());
		column.add(Ui.gap(16));
		column.add(shareCard());
		column.add(Box.createVerticalGlue());

		body(Ui.scroll(column));
		app.onRefresh(this::reload);

		// The server writes to its own console; this keeps the page in step without blocking it.
		new Timer(800, event -> refreshLive()).start();
	}

	private JPanel serverCard() {
		Ui.Card card = new Ui.Card("This server");
		card.body().add(Ui.dim("Built from the same Minecraft version the active instance plays, so"));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("your client and the server can never drift apart."));
		card.body().add(Ui.gap(12));

		card.body().add(line("Minecraft", version));
		card.body().add(line("Join from this PC", address));
		card.body().add(line("Join from your network", lan));
		card.body().add(line("Accounts", mode));
		card.body().add(line("Folder", folder));
		card.body().add(Ui.gap(14));

		JPanel row = Ui.row();
		startStop.onClick(button -> toggle());
		row.add(startStop);
		row.add(Ui.hgap(10));
		row.add(Ui.secondary("Open server folder").size(190, 38).withGlyph(Icons::folder)
			.onClick(button -> app.openFolder(app.server.directory(app.config.active()))));
		row.add(Box.createHorizontalGlue());
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		card.body().add(row);
		card.body().add(Ui.gap(10));
		status.setAlignmentX(LEFT_ALIGNMENT);
		status.setMaximumSize(new Dimension(760, 24));
		card.body().add(status);
		card.body().add(Ui.gap(10));
		card.body().add(Ui.dim("Starting it accepts Minecraft's EULA (aka.ms/MinecraftEULA)."));
		return card;
	}

	private JPanel consoleCard() {
		Ui.Card card = new Ui.Card("Server console");
		console.setRows(12);
		JScrollPane pane = new JScrollPane(console);
		pane.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.PANEL_OUTLINE));
		pane.getVerticalScrollBar().setUI(new Ui.ScrollBar(javax.swing.JScrollBar.VERTICAL).getUI());
		pane.setPreferredSize(new Dimension(100, 230));
		card.body().add(pane);
		card.body().add(Ui.gap(8));
		card.body().add(Ui.dim("The first start generates the world; that takes a minute on a fresh folder."));
		return card;
	}

	/** The one thing a friend needs: an address they can type into Minecraft's Add Server box. */
	private JPanel shareCard() {
		Ui.Card card = new Ui.Card("Give this to your friends");

		shareAddress.setAlignmentX(LEFT_ALIGNMENT);
		card.body().add(shareAddress);
		card.body().add(Ui.gap(6));
		shareNote.setAlignmentX(LEFT_ALIGNMENT);
		shareNote.setMaximumSize(new Dimension(760, 46));
		card.body().add(shareNote);
		card.body().add(Ui.gap(12));

		JPanel row = Ui.row();
		copyAddress.onClick(button -> copyPublicAddress());
		row.add(copyAddress);
		row.add(Ui.hgap(10));
		row.add(Ui.secondary("Open playit.gg").size(170, 34).onClick(button -> open("https://playit.gg/")));
		row.add(Box.createHorizontalGlue());
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(row);
		card.body().add(Ui.gap(12));

		card.body().add(Ui.dim("In Minecraft: Multiplayer → Add Server → paste the address → Join."));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("The launcher opens the port on your router when the server starts,"));
		card.body().add(Ui.gap(3));
		card.body().add(Ui.dim("and closes it again when you stop, so nothing is left open."));
		return card;
	}

	private void copyPublicAddress() {
		String address = app.server.publicAddress();

		if (address.isBlank()) {
			address = app.server.lanAddress();
		}

		try {
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(address), null);
			app.status("Copied " + address);
		} catch (Exception e) {
			app.status("Could not copy: " + e.getMessage());
		}
	}

	private void open(String url) {
		try {
			java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
		} catch (Exception e) {
			app.status("Could not open a browser: " + e.getMessage());
		}
	}

	private JPanel line(String label, JLabel value) {
		JPanel row = Ui.row(new BorderLayout(12, 0));
		row.add(Ui.dim(label), BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return row;
	}

	private void toggle() {
		ServerManager server = app.server;

		if (server.isRunning()) {
			status.setForeground(Theme.TEXT_DIM);
			status.setText("Stopping — the server saves the world first…");
			startStop.setEnabled(false);
			Thread stopper = new Thread(() -> {
				server.stop(60000);
				SwingUtilities.invokeLater(() -> {
					startStop.setEnabled(true);
					reload();
				});
			}, "shrd-server-stop");
			stopper.setDaemon(true);
			stopper.start();
			return;
		}

		Instance instance = app.config.active();
		boolean onlineMode = app.config.activeAccount().online();
		startStop.setEnabled(false);
		status.setForeground(Theme.TEXT_ACCENT);
		status.setText("Getting the " + instance.minecraftVersion + " server jar…");

		Thread starter = new Thread(() -> {
			try {
				server.start(instance, onlineMode, app.config.javaPath);
				SwingUtilities.invokeLater(this::reload);
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> {
					status.setForeground(Theme.BAD);
					status.setText("Could not start: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
				});
			} finally {
				SwingUtilities.invokeLater(() -> startStop.setEnabled(true));
			}
		}, "shrd-server-start");
		starter.setDaemon(true);
		starter.start();
	}

	@Override
	public void reload() {
		Instance instance = app.config.active();
		ServerManager server = app.server;
		version.setText("Minecraft " + instance.minecraftVersion);
		folder.setText(Ui.elide(ShrdPaths.pretty(server.directory(instance)), 48));
		folder.setToolTipText(ShrdPaths.pretty(server.directory(instance)));
		address.setText(server.address());
		lan.setText(server.lanAddress());
		mode.setText(server.isRunning()
			? (server.onlineMode() ? "verified (online-mode)" : "offline (online-mode=false)")
			: (app.config.activeAccount().online() ? "will verify players" : "offline, no verification"));

		if (server.isRunning()) {
			startStop.setText("Stop server");
			status.setForeground(Theme.GOOD);
			status.setText(server.state() == ServerManager.State.STARTING
				? "Starting — generating the world the first time takes a minute…"
				: "Running. Friends on this network can join at " + server.lanAddress());
		} else {
			startStop.setText("Start server");
			status.setForeground(Theme.TEXT_DIM);
			status.setText("Not running. It costs nothing to start, and nothing stays on when you stop.");
		}

		refreshShareCard(server);

		refreshLive();
		revalidate();
		repaint();
	}

	private void refreshShareCard(ServerManager server) {
		String publicAddress = server.publicAddress();

		if (!publicAddress.isBlank()) {
			shareAddress.setText(publicAddress);
			shareAddress.setForeground(Theme.GOOD);
			shareNote.setForeground(Theme.TEXT_DIM);
			shareNote.setText("Anyone, anywhere. " + server.mapping().message());
			copyAddress.setEnabled(true);
			return;
		}

		shareAddress.setText(server.isRunning() ? "Only on your network for now" : "Start the server first");
		shareAddress.setForeground(server.isRunning() ? Theme.WARN : Theme.TEXT_DIM);
		shareNote.setForeground(Theme.TEXT_DIM);
		shareNote.setText(server.isRunning()
			? "The router did not open the port: " + server.mapping().message()
				+ " Friends on the same Wi-Fi can still use " + server.lanAddress() + "."
			: "Friends on the same Wi-Fi use " + server.lanAddress() + " once it is running.");
		copyAddress.setEnabled(true);
	}

	/** Redraws the console only when the server actually said something new. */
	private void refreshLive() {
		List<String> lines = app.server.log();

		if (lines.size() == renderedLines) {
			return;
		}

		renderedLines = lines.size();
		int from = Math.max(0, lines.size() - 200);
		StringBuilder text = new StringBuilder();

		for (String line : lines.subList(from, lines.size())) {
			text.append(line).append('\n');
		}

		console.setText(text.toString());
		console.setCaretPosition(console.getDocument().getLength());

		if (app.server.isRunning()) {
			startStop.setText("Stop server");
		}
	}
}
