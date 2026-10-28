package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.launch.JavaRuntime;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.swing.Box;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;

/** Java, defaults, accounts and where the launcher keeps its files. */
public final class SettingsPage extends Page {

	private final Ui.Field javaField = new Ui.Field("auto-detect");
	private final JLabel javaStatus = Ui.dim("");
	private final Ui.Slider memorySlider = new Ui.Slider(1, 32, 8);
	private final JLabel memoryLabel = Ui.accent("4096 MB");
	private final Ui.Field jvmField = new Ui.Field("-XX:+UseG1GC");
	private final Ui.Switch streamLog = new Ui.Switch(true, value -> {
	});
	private final Ui.Switch closeOnLaunch = new Ui.Switch(false, value -> {
	});
	private final Ui.Field accountField = new Ui.Field("new account name");
	private final JPanel accountRows = Ui.column();
	private final Ui.Field manifestField = new Ui.Field("https://…/shrd-launcher-latest.json");
	private final Ui.Switch checkOnStart = new Ui.Switch(true, value -> {
		app.config.checkForUpdatesOnStart = value;
		app.config.save();
		app.status(value ? "Shrd Launcher will check for updates on start" : "Startup update checks are off");
	});
	private final JLabel versionValue = Ui.accent(dev.shrd.launcher.Version.CURRENT);
	private final JLabel updateStatus = Ui.dim("");
	private final Ui.Button updateButton = Ui.primary("Check for updates").size(210, 34);

	public SettingsPage(App app) {
		super(app, "Settings", "Launcher-wide defaults; per-instance settings live on the Instances page");

		JPanel column = Ui.column();
		column.add(javaCard());
		column.add(Ui.gap(16));
		column.add(defaultsCard());
		column.add(Ui.gap(16));
		column.add(accountsCard());
		column.add(Ui.gap(16));
		column.add(dataCard());
		column.add(Ui.gap(16));
		column.add(updateCard());
		column.add(Ui.gap(20));

		javax.swing.JPanel holder = Ui.column();
		holder.add(column);
		holder.add(Box.createVerticalGlue());
		body(Ui.scroll(holder));

		memorySlider.addChangeListener(event -> memoryLabel.setText((memorySlider.getValue() * 512) + " MB"));
		app.onRefresh(this::reload);
	}

	private JPanel javaCard() {
		Ui.Card card = new Ui.Card("Java");
		card.body().add(Ui.dim("Minecraft 26.2 needs Java 25 or newer. Blank means the launcher picks one: the bundled .toolchain JDK, then JAVA_HOME, then PATH."));
		card.body().add(Ui.gap(12));
		card.body().add(Ui.fill(javaField));
		card.body().add(Ui.gap(10));

		JPanel buttons = Ui.row();
		buttons.add(Ui.secondary("Detect automatically").size(190, 34).onClick(button -> {
			app.config.javaPath = "";
			app.config.save();
			reload();
		}));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Browse…").size(120, 34).onClick(button -> browseForJava()));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Use this path").size(140, 34).onClick(button -> {
			app.config.javaPath = javaField.getText().trim();
			app.config.save();
			reload();
		}));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(buttons);
		card.body().add(Ui.gap(10));
		javaStatus.setAlignmentX(LEFT_ALIGNMENT);
		card.body().add(javaStatus);
		return card;
	}

	private void browseForJava() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Pick the java executable");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			javaField.setText(chooser.getSelectedFile().getAbsolutePath());
			app.config.javaPath = javaField.getText();
			app.config.save();
			reload();
		}
	}

	private JPanel defaultsCard() {
		Ui.Card card = new Ui.Card("Defaults");
		JPanel memoryRow = Ui.row(new BorderLayout(12, 0));
		memoryRow.add(Ui.caps("Memory for new instances"), BorderLayout.WEST);
		memoryRow.add(memoryLabel, BorderLayout.EAST);
		memoryRow.setAlignmentX(LEFT_ALIGNMENT);
		memoryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		card.body().add(memoryRow);
		card.body().add(Ui.gap(4));
		card.body().add(Ui.fill(memorySlider));
		card.body().add(Ui.gap(12));

		card.body().add(Ui.caps("Extra JVM arguments for every instance"));
		card.body().add(Ui.gap(6));
		card.body().add(Ui.fill(jvmField));
		card.body().add(Ui.gap(12));

		card.body().add(toggleRow("Stream the game log into the Console page", streamLog));
		card.body().add(Ui.gap(8));
		card.body().add(toggleRow("Close the launcher once the game has started", closeOnLaunch));
		card.body().add(Ui.gap(14));
		card.body().add(Ui.fill(Ui.primary("Save settings").size(160, 36).onClick(button -> {
			app.config.defaultMemoryMb = memorySlider.getValue() * 512;
			app.config.extraJvmArgs = jvmField.getText().trim();
			app.config.streamGameLog = streamLog.isOn();
			app.config.closeOnLaunch = closeOnLaunch.isOn();
			app.config.save();
			app.status("Settings saved");
		})));
		return card;
	}

	private JPanel toggleRow(String label, Ui.Switch toggle) {
		JPanel row = Ui.row(new BorderLayout(12, 0));
		row.add(Ui.text(label), BorderLayout.WEST);
		row.add(toggle, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return row;
	}

	private JPanel accountsCard() {
		Ui.Card card = new Ui.Card("Accounts");
		card.body().add(Ui.dim("Shrd Launcher plays offline accounts: pick a name and the launcher derives the same offline UUID Minecraft's own launcher uses. Online Microsoft sign-in is not wired up."));
		card.body().add(Ui.gap(12));
		card.body().add(accountRows);
		card.body().add(Ui.gap(6));

		JPanel addRow = Ui.row();
		accountField.setPreferredSize(new Dimension(220, 34));
		addRow.add(accountField);
		addRow.add(Ui.hgap(10));
		addRow.add(Ui.primary("Add account").size(150, 34).withGlyph(Icons::plus).onClick(button -> addAccount()));
		addRow.add(Box.createHorizontalGlue());
		addRow.setAlignmentX(LEFT_ALIGNMENT);
		addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(addRow);
		return card;
	}

	private void addAccount() {
		String name = accountField.getText().trim();

		if (!Account.validName(name)) {
			app.status("Names are 3-16 characters, letters, numbers and underscores");
			return;
		}

		if (app.config.findAccount(name) != null) {
			app.status("There is already an account called " + name);
			return;
		}

		app.config.accounts.add(new Account(name));
		app.config.activeAccount = name;
		app.config.save();
		accountField.setText("");
		app.status("Added account " + name);
		app.refresh();
	}

	private JPanel dataCard() {
		Ui.Card card = new Ui.Card("Launcher data");
		card.body().add(rowLine("Data folder", Ui.elided(ShrdPaths.pretty(ShrdPaths.home()), 58)));
		card.body().add(Ui.gap(6));
		card.body().add(rowLine("Config file", Ui.elided(ShrdPaths.pretty(ShrdPaths.configFile().toPath()), 58)));
		card.body().add(Ui.gap(6));
		card.body().add(rowLine("Instances", Ui.elided(ShrdPaths.pretty(ShrdPaths.instances()), 58)));
		card.body().add(Ui.gap(14));

		JPanel buttons = Ui.row();
		buttons.add(Ui.secondary("Open data folder").size(170, 34).withGlyph(Icons::folder).onClick(button -> app.openFolder(ShrdPaths.home())));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Save config now").size(160, 34).onClick(button -> {
			app.config.save();
			app.status("Config saved");
		}));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(buttons);
		return card;
	}

	private JPanel updateCard() {
		Ui.Card card = new Ui.Card("Updates");
		card.body().add(Ui.dim("Shrd Launcher compares its own version with a manifest published next to the download. "
			+ "When a newer build is there, it fetches it, checks the SHA-256 and swaps it in on restart."));
		card.body().add(Ui.gap(12));
		card.body().add(rowLine("This build", versionValue));
		card.body().add(Ui.gap(10));
		card.body().add(Ui.caps("Update manifest URL"));
		card.body().add(Ui.gap(6));
		card.body().add(Ui.fill(manifestField));
		card.body().add(Ui.gap(8));

		JPanel urlRow = Ui.row();
		urlRow.add(Ui.secondary("Use this URL").size(140, 32).onClick(button -> {
			app.config.updateManifestUrl = manifestField.getText().trim();
			app.config.save();
			app.updates.setManifestUrl(app.config.updateManifestUrl);
			app.status(app.config.updateManifestUrl.isBlank() ? "Update checks turned off" : "Update manifest saved");
			reload();
		}));
		urlRow.add(Ui.hgap(10));
		urlRow.add(Ui.secondary("Open downloads").size(160, 32).withGlyph(Icons::folder)
			.onClick(button -> app.openFolder(dev.shrd.launcher.ShrdPaths.updates())));
		urlRow.add(Box.createHorizontalGlue());
		urlRow.setAlignmentX(LEFT_ALIGNMENT);
		urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		card.body().add(urlRow);
		card.body().add(Ui.gap(10));
		card.body().add(toggleRow("Check for updates when the launcher starts", checkOnStart));
		card.body().add(Ui.gap(14));

		JPanel actions = Ui.row();
		updateButton.onClick(button -> {
			if (app.updates.state() == dev.shrd.launcher.update.UpdateService.State.IDLE) {
				app.updates.check();
			} else {
				app.updateNow();
			}
		});
		actions.add(updateButton);
		actions.add(Box.createHorizontalGlue());
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		card.body().add(actions);
		card.body().add(Ui.gap(10));
		updateStatus.setAlignmentX(LEFT_ALIGNMENT);
		card.body().add(updateStatus);
		return card;
	}

	/** The state line under the update buttons, plus what the button should say next. */
	private void reloadUpdateCard() {
		var updates = app.updates;
		versionValue.setText(dev.shrd.launcher.Version.CURRENT);
		checkOnStart.set(app.config.checkForUpdatesOnStart, false);

		if (!manifestField.hasFocus()) {
			// Not while it is being typed into, or a half-typed URL would be wiped every refresh.
			manifestField.setText(app.config.updateManifestUrl == null ? "" : app.config.updateManifestUrl);
		}

		boolean installed = updates.canSelfInstall();
		updateButton.setEnabled(!updates.busy());
		updateStatus.setForeground(Theme.TEXT_DIM);

		switch (updates.state()) {
			case IDLE -> {
				updateButton.setText("Check for updates");
				updateStatus.setText(installed ? updates.describe() : updates.describe() + "  ·  this copy is not installed, so updates are saved rather than swapped in");
			}
			case CHECKING -> updateButton.setText("Checking…");
			case CURRENT -> {
				updateButton.setText("Check again");
				updateStatus.setForeground(Theme.GOOD);
				updateStatus.setText(updates.describe());
			}
			case AVAILABLE -> {
				updateButton.setText("Download " + version(updates));
				updateStatus.setForeground(Theme.TEXT_ACCENT);
				updateStatus.setText(updates.describe() + (updates.message().isBlank() ? "" : " — " + updates.message().replace('\n', ' ')));
			}
			case DOWNLOADING -> updateButton.setText("Downloading…");
			case READY -> {
				updateButton.setText(installed ? "Restart and update" : "Open downloads folder");
				updateStatus.setForeground(Theme.GOOD);
				updateStatus.setText(installed
					? updates.describe() + " — the launcher restarts to finish"
					: updates.describe() + " — unzip it over this folder to finish");
			}
			case FAILED -> {
				updateButton.setText("Try again");
				updateStatus.setForeground(Theme.BAD);
				updateStatus.setText(updates.describe());
			}
		}

		if (updates.busy() && !updates.message().isBlank()) {
			updateStatus.setText(updates.message());
		}
	}

	private String version(dev.shrd.launcher.update.UpdateService updates) {
		return updates.manifest() == null ? "update" : updates.manifest().version;
	}

	private JPanel rowLine(String label, JLabel value) {
		JPanel row = Ui.row(new BorderLayout(10, 0));
		row.add(Ui.dim(label), BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		return row;
	}

	@Override
	public void reload() {
		javaField.setText(app.config.javaPath == null ? "" : app.config.javaPath);
		memorySlider.setValue(Math.max(1, Math.min(32, app.config.defaultMemoryMb / 512)));
		memoryLabel.setText((memorySlider.getValue() * 512) + " MB");
		jvmField.setText(app.config.extraJvmArgs == null ? "" : app.config.extraJvmArgs);
		streamLog.set(app.config.streamGameLog, false);
		closeOnLaunch.set(app.config.closeOnLaunch, false);
		reloadUpdateCard();

		Path java = app.java();

		if (java == null) {
			javaStatus.setForeground(Theme.BAD);
			javaStatus.setText("No Java found — the game cannot start until one is set.");
		} else {
			String version = JavaRuntime.version(java);
			int major = JavaRuntime.major(version);
			javaStatus.setForeground(major >= 25 ? Theme.GOOD : Theme.WARN);
			// The detected path can be very long; keeping the tail identifies the JDK without
			// letting the label push the whole card wider than the window.
			javaStatus.setText("Using Java " + version + " — " + Ui.elide(ShrdPaths.pretty(java), 46)
				+ (major >= 25 ? "" : "\nThis Minecraft version wants Java 25 or newer."));
			javaStatus.setToolTipText(ShrdPaths.pretty(java));
		}

		accountRows.removeAll();

		for (Account account : app.config.accounts) {
			accountRows.add(accountRow(account));
			accountRows.add(Ui.gap(8));
		}

		Log.info("settings reloaded (%d account(s))", app.config.accounts.size());
		revalidate();
		repaint();
	}

	private JPanel accountRow(Account account) {
		boolean active = account.name.equalsIgnoreCase(app.config.activeAccount);
		Ui.Row row = new Ui.Row();
		row.hoverable();
		row.setSelected(active);

		JPanel titles = Ui.column();
		titles.add(Ui.text(account.name));
		titles.add(Ui.gap(2));
		titles.add(Ui.dim("offline  ·  " + account.uuid()));
		row.add(titles, BorderLayout.CENTER);

		JPanel controls = Ui.row();

		if (!active) {
			controls.add(Ui.secondary("Use").size(80, 30).onClick(button -> {
				app.config.activeAccount = account.name;
				app.config.save();
				app.status("Playing as " + account.name);
				app.refresh();
			}));
			controls.add(Ui.hgap(8));
		} else {
			controls.add(Ui.accent("active"));
			controls.add(Ui.hgap(8));
		}

		controls.add(new Ui.Button("Remove", Ui.Variant.DANGER).size(92, 30).onClick(button -> {
			if (app.config.accounts.size() <= 1) {
				app.status("At least one account has to stay");
				return;
			}

			app.config.accounts.remove(account);
			app.config.activeAccount = app.config.accounts.get(0).name;
			app.config.save();
			app.refresh();
		}));
		row.add(controls, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		return row;
	}
}
