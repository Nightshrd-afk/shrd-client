package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Instance;
import dev.shrd.launcher.ModsManager;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Play, in one screen: what will launch, as whom, and the button that does it. */
public final class HomePage extends Page {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

	private final Ui.Combo<String> instanceCombo = new Ui.Combo<>(new String[] { "" }, null);
	private final Ui.Combo<String> accountCombo = new Ui.Combo<>(new String[] { "" }, null);
	private final Ui.Button playButton = Ui.primary("PLAY").withGlyph(Icons::play).size(220, 52);
	private final JLabel versionLine = Ui.text("");
	private final JLabel detailLine = Ui.dim("");
	private final Ui.Progress progress = new Ui.Progress();
	private final JPanel progressHolder = Ui.row(new BorderLayout());
	private final JLabel gameDirValue = Ui.dim("");
	private final JLabel memoryValue = Ui.text("");
	private final JLabel modsValue = Ui.text("");
	private final JLabel lastPlayedValue = Ui.text("");
	private final JLabel javaValue = Ui.text("");
	private final JPanel updateAlert = Ui.row(new BorderLayout(10, 0));
	private final JLabel updateLabel = Ui.accent("");
	private final Ui.Button updateButton = Ui.primary("Update now").size(150, 30);
	private boolean populating;

	public HomePage(App app) {
		super(app, "Home", "Play Minecraft, with Shrd Client installed for you");

		body(buildHero());
		body().add(buildSide(), BorderLayout.EAST);

		playButton.onClick(button -> app.togglePlay());
		app.onStage((stage, detail) -> refreshStage());
		app.onRefresh(this::reload);
	}

	private JPanel buildHero() {
		Ui.Card card = new Ui.Card("Ready to play");
		JPanel content = card.body();

		// Only shown when an update check has found something: an offer, not a nag.
		updateAlert.add(updateLabel, BorderLayout.CENTER);
		updateAlert.add(updateButton, BorderLayout.EAST);
		updateAlert.setAlignmentX(LEFT_ALIGNMENT);
		updateAlert.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		updateAlert.setVisible(false);
		updateButton.onClick(button -> app.updateNow());
		content.add(updateAlert);
		content.add(Ui.gap(14));

		content.add(Ui.caps("Instance"));
		content.add(Ui.gap(6));
		instanceCombo.setAlignmentX(LEFT_ALIGNMENT);
		instanceCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		instanceCombo.addActionListener(event -> selectInstance());
		content.add(instanceCombo);
		content.add(Ui.gap(14));

		content.add(Ui.caps("Account"));
		content.add(Ui.gap(6));
		accountCombo.setAlignmentX(LEFT_ALIGNMENT);
		accountCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		accountCombo.addActionListener(event -> selectAccount());
		content.add(accountCombo);
		content.add(Ui.gap(18));

		versionLine.setFont(Theme.bold(15));
		versionLine.setAlignmentX(LEFT_ALIGNMENT);
		content.add(versionLine);
		content.add(Ui.gap(4));
		detailLine.setAlignmentX(LEFT_ALIGNMENT);
		content.add(detailLine);
		content.add(Ui.gap(20));

		content.add(Ui.fill(playButton));
		content.add(Ui.gap(14));

		progressHolder.add(progress, BorderLayout.CENTER);
		progressHolder.setAlignmentX(LEFT_ALIGNMENT);
		progressHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		content.add(progressHolder);
		content.add(Ui.gap(20));
		content.add(Ui.caps("Installed with the launcher"));
		content.add(Ui.gap(6));
		content.add(Ui.dim("Shrd Client — HUD overlays, Performance Mode, zoom, freelook, fullbright, waypoints"));
		content.add(Ui.gap(3));
		content.add(Ui.dim("and the Feather-style mod menu on Right Shift. Modules are configured in game."));
		content.add(Ui.gap(3));
		content.add(Ui.dim("The Mods page installs the built jar and Fabric API, which it needs to load."));
		content.add(Box.createVerticalGlue());
		return card;
	}

	private void selectInstance() {
		Object selected = instanceCombo.getSelectedItem();

		if (populating || !(selected instanceof String name)) {
			return;
		}

		if (app.config.findInstance(name) != null && !name.equals(app.config.activeInstance)) {
			app.config.activeInstance = name;
			app.config.save();
			app.refresh();
		}
	}

	private void selectAccount() {
		Object selected = accountCombo.getSelectedItem();

		if (populating || !(selected instanceof String name)) {
			return;
		}

		if (app.config.findAccount(name) != null && !name.equals(app.config.activeAccount)) {
			app.config.activeAccount = name;
			app.config.save();
			app.refresh();
		}
	}

	private JPanel buildSide() {
		JPanel side = Ui.column();
		side.setPreferredSize(new Dimension(340, 10));

		Ui.Card details = new Ui.Card("This instance");
		details.body().add(Ui.caps("Game folder"));
		details.body().add(Ui.gap(4));
		details.body().add(gameDirValue);
		details.body().add(Ui.gap(14));
		addLine(details.body(), "Memory", memoryValue);
		addLine(details.body(), "Mods", modsValue);
		addLine(details.body(), "Last played", lastPlayedValue);
		addLine(details.body(), "Java", javaValue);
		details.body().add(Box.createVerticalGlue());
		side.add(details);
		side.add(Ui.gap(16));

		Ui.Card actions = new Ui.Card("Shortcuts");
		actions.body().add(Ui.fill(Ui.secondary("Open game folder").onClick(button -> app.openFolder(app.config.active().dir()))));
		actions.body().add(Ui.fill(Ui.secondary("Open mods folder").onClick(button -> app.openFolder(app.config.active().modsDir()))));
		actions.body().add(Ui.fill(Ui.secondary("Open launcher logs").onClick(button -> app.openFolder(ShrdPaths.logs()))));
		actions.body().add(Ui.gap(6));
		actions.body().add(Ui.dim("<html>Shrd Client's overlays, HUD editor and mod menu are configured in game — press <b>Right Shift</b>.</html>"));
		side.add(actions);
		return side;
	}

	private void addLine(JPanel parent, String label, JLabel value) {
		JPanel row = Ui.row(new BorderLayout(10, 0));
		row.add(Ui.dim(label), BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		parent.add(row);
		parent.add(Ui.gap(6));
	}

	@Override
	public void reload() {
		populating = true;

		List<Instance> instances = app.config.instances;
		instanceCombo.removeAllItems();

		for (Instance instance : instances) {
			instanceCombo.addItem(instance.name);
		}

		instanceCombo.setSelectedItem(app.config.active().name);
		accountCombo.removeAllItems();

		for (Account account : app.config.accounts) {
			accountCombo.addItem(account.name);
		}

		accountCombo.setSelectedItem(app.config.activeAccount().name);
		populating = false;

		Instance instance = app.config.active();
		Account account = app.config.activeAccount();
		versionLine.setText("Minecraft " + instance.minecraftVersion + (instance.isFabric() ? "  ·  Fabric" : "  ·  Vanilla"));
		detailLine.setText(instance.isFabric()
			? "loader " + (instance.loaderVersion.isBlank() ? "latest" : instance.loaderVersion) + "  ·  playing as " + account.name
			: "no mod loader  ·  playing as " + account.name);
		gameDirValue.setText(ShrdPaths.pretty(instance.dir()));
		memoryValue.setText(instance.memoryMb + " MB");
		modsValue.setText(ModsManager.list(instance).size() + " installed");
		lastPlayedValue.setText(instance.lastPlayed == 0 ? "never" : WHEN.format(Instant.ofEpochMilli(instance.lastPlayed)));
		javaValue.setText(app.javaDescription());
		refreshUpdateAlert();
		refreshStage();
	}

	/** The offer at the top of the hero card, shown only when there is an update to act on. */
	private void refreshUpdateAlert() {
		var updates = app.updates;
		boolean offer = updates.offerable() && !updates.busy();
		updateAlert.setVisible(offer);

		if (!offer) {
			return;
		}

		boolean ready = updates.state() == dev.shrd.launcher.update.UpdateService.State.READY;
		updateLabel.setText("Shrd Launcher " + (updates.manifest() == null ? "?" : updates.manifest().version)
			+ (ready ? " is downloaded — restart to finish" : " is available"));
		updateButton.setText(ready ? (updates.canSelfInstall() ? "Restart and update" : "Open downloads") : "Update now");
	}

	private void refreshStage() {
		boolean running = app.isRunning();
		boolean busy = app.isBusy();
		playButton.setText(running ? "STOP" : (busy ? "LAUNCHING…" : "PLAY"));
		playButton.setEnabled(!busy || running);
		progress.setFraction(app.lastProgress());
		progressHolder.setVisible(busy || running);
		repaint();
	}
}
