package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Instance;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ModsManager;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Ui;

import javax.swing.Box;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;

/**
 * The mods folder, as a list. Shrd Client itself is installed from this page: the launcher knows
 * where this project's build output is, so "install" means "copy what gradle just built".
 */
public final class ModsPage extends Page {

	private final JPanel rows = Ui.column();
	private final javax.swing.JLabel folderLine = Ui.dim("");
	private final javax.swing.JLabel nameLabel = Ui.dim("");
	private final javax.swing.JLabel warning = Ui.dim("");

	public ModsPage(App app) {
		super(app, "Mods", "Add Shrd Client and other Fabric mods to the active instance");

		actions().add(Ui.primary("Install Shrd Client").size(180, 34).onClick(button -> installClient()));
		actions().add(Ui.hgap(10));
		actions().add(Ui.secondary("Get Fabric API").size(150, 34).onClick(button -> installFabricApi()));
		actions().add(Ui.hgap(10));
		actions().add(Ui.secondary("Refresh").size(100, 34).onClick(button -> reload()));
		actions().add(Ui.hgap(10));
		actions().add(Ui.secondary("Open folder").size(120, 34).withGlyph(Icons::folder).onClick(button -> app.openFolder(app.config.active().modsDir())));

		Ui.Card card = new Ui.Card("Installed mods", nameLabel);
		card.body().add(rows);
		card.body().add(Box.createVerticalGlue());
		body(card);

		JPanel footer = Ui.column();
		footer.add(folderLine);
		footer.add(Ui.gap(6));
		footer.add(warning);
		footer.add(Ui.gap(6));
		footer.add(Ui.dim("Fabric mods need the Fabric loader — switch it on the Instances page if this instance is Vanilla."));
		footer.setAlignmentX(LEFT_ALIGNMENT);
		body().add(footer, BorderLayout.SOUTH);

		app.onRefresh(this::reload);
	}

	private void installClient() {
		Instance instance = app.config.active();
		Path jar = ModsManager.clientBuildJar();

		if (jar == null) {
			JOptionPane.showMessageDialog(this,
				"No client jar to install.\n\nThis launcher carries none in its mods folder, and there"
					+ " is no build output next to it either.\n\nUse a release build, or run `gradlew build`"
					+ " in the project root first.",
				"Shrd Launcher", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		runInBackground("Installing " + jar.getFileName(), () -> {
			ModsManager.installClientBuild(instance);
			return "Installed " + jar.getFileName() + " into " + instance.name;
		});
	}

	private void installFabricApi() {
		Instance instance = app.config.active();
		runInBackground("Fetching Fabric API for " + instance.minecraftVersion, () -> {
			Path installed = ModsManager.installFabricApi(instance);
			return "Installed " + installed.getFileName();
		});
	}

	private void runInBackground(String status, java.util.concurrent.Callable<String> work) {
		app.status(status);
		Thread thread = new Thread(() -> {
			try {
				String message = work.call();
				javax.swing.SwingUtilities.invokeLater(() -> {
					app.status(message);
					reload();
				});
			} catch (Exception e) {
				Log.error("%s", e.getMessage());
				javax.swing.SwingUtilities.invokeLater(() -> {
					app.status("Failed: " + e.getMessage());
					JOptionPane.showMessageDialog(this, e.getMessage(), "Shrd Launcher", JOptionPane.ERROR_MESSAGE);
				});
			}
		}, "shrd-mod-install");

		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void reload() {
		Instance instance = app.config.active();
		setSubtitle("Mods for " + instance.name + " — Minecraft " + instance.minecraftVersion + (instance.isFabric() ? " · Fabric" : " · Vanilla"));
		List<ModsManager.Mod> mods = ModsManager.list(instance);
		nameLabel.setText(mods.size() + (mods.size() == 1 ? " mod" : " mods"));
		folderLine.setText("Folder: " + ShrdPaths.pretty(instance.modsDir()));
		warning.setText(instance.isFabric()
			? "Shrd Client needs Fabric API in this folder — one press of \"Get Fabric API\" installs it."
			: "This instance is Vanilla, so no Fabric mod here will load.");
		rows.removeAll();

		if (mods.isEmpty()) {
			rows.add(Ui.dim("Nothing here yet. Use \"Install Shrd Client\" to copy this project's build straight in."));
			rows.add(Ui.gap(10));
		}

		for (ModsManager.Mod mod : mods) {
			rows.add(modRow(mod));
			rows.add(Ui.gap(8));
		}

		revalidate();
		repaint();
	}

	private JPanel modRow(ModsManager.Mod mod) {
		Ui.Row row = new Ui.Row();
		row.hoverable();

		JPanel titles = Ui.column();
		titles.add(Ui.text(mod.displayName()));
		titles.add(Ui.gap(2));
		titles.add(Ui.dim(mod.fileName() + "  ·  " + mod.sizeText() + (mod.enabled() ? "" : "  ·  disabled")));
		row.add(titles, BorderLayout.CENTER);

		JPanel controls = Ui.row();
		controls.add(new Ui.Switch(mod.enabled(), value -> toggle(mod, value)));
		controls.add(Ui.hgap(10));
		controls.add(new Ui.Button("Remove", Ui.Variant.DANGER).size(92, 30).onClick(button -> remove(mod)));
		row.add(controls, BorderLayout.EAST);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		return row;
	}

	private void toggle(ModsManager.Mod mod, boolean enabled) {
		try {
			ModsManager.toggle(mod);
			app.status(mod.displayName() + (enabled ? " enabled" : " disabled"));
		} catch (Exception e) {
			Log.error("could not toggle %s: %s", mod.fileName(), e.getMessage());
			app.status("Could not change " + mod.fileName());
		}

		reload();
	}

	private void remove(ModsManager.Mod mod) {
		int choice = JOptionPane.showConfirmDialog(this, "Delete " + mod.fileName() + "?", "Shrd Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

		if (choice != JOptionPane.OK_OPTION) {
			return;
		}

		try {
			ModsManager.delete(mod);
			app.status("Deleted " + mod.fileName());
		} catch (Exception e) {
			Log.error("could not delete %s: %s", mod.fileName(), e.getMessage());
		}

		reload();
	}
}
