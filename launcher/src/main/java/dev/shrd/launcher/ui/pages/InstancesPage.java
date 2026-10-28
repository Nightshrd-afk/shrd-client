package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Instance;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;
import dev.shrd.launcher.ui.VersionCatalog;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Create, edit and choose instances — a version, a loader, memory and its own game folder. */
public final class InstancesPage extends Page {

	private final JPanel listRows = Ui.column();
	private final Ui.Field nameField = new Ui.Field("Instance name");
	private final Ui.Combo<String> versionCombo = new Ui.Combo<>(new String[] { "" }, null);
	private final Ui.Combo<String> loaderCombo = new Ui.Combo<>(new String[] { "Fabric", "Vanilla" }, "Fabric");
	private final Ui.Combo<String> loaderVersionCombo = new Ui.Combo<>(new String[] { "latest" }, "latest");
	private final Ui.Slider memorySlider = new Ui.Slider(1, 16, 4);
	private final JLabel memoryLabel = Ui.accent("4 GB");
	private final Ui.Switch resolutionSwitch = new Ui.Switch(false, value -> {
	});
	private final Ui.Field widthField = new Ui.Field("1280");
	private final Ui.Field heightField = new Ui.Field("720");
	private final Ui.Field jvmField = new Ui.Field("-XX:+UseG1GC");
	private final JLabel editorTitle = Ui.heading("Instance");
	private final JLabel editorPath = Ui.dim("");

	public InstancesPage(App app) {
		super(app, "Instances", "Every instance keeps its own mods, worlds and settings");

		body(buildList());
		body().add(buildEditor(), BorderLayout.CENTER);

		memorySlider.addChangeListener(event -> memoryLabel.setText((memorySlider.getValue() * 512) + " MB"));
		app.onRefresh(this::reload);
	}

	private JPanel buildList() {
		JPanel column = Ui.column();
		column.setPreferredSize(new Dimension(300, 10));

		Ui.Card card = new Ui.Card("Instances");
		card.body().add(Ui.fill(Ui.secondary("Create new instance").withGlyph(dev.shrd.launcher.ui.Icons::plus).onClick(button -> create())));
		card.body().add(Ui.gap(12));
		card.body().add(listRows);
		column.add(card);
		column.add(Box.createVerticalGlue());
		return column;
	}

	private JPanel buildEditor() {
		Ui.Card card = new Ui.Card(null);
		JPanel content = card.body();
		content.add(editorTitle);
		content.add(Ui.gap(4));
		content.add(editorPath);
		content.add(Ui.gap(18));

		content.add(Ui.caps("Name"));
		content.add(Ui.gap(6));
		content.add(Ui.fill(nameField));
		content.add(Ui.gap(16));

		content.add(Ui.caps("Minecraft version"));
		content.add(Ui.gap(6));
		content.add(Ui.fill(versionCombo));
		content.add(Ui.gap(16));

		JPanel loaderRow = Ui.row(new BorderLayout(12, 0));
		loaderCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		loaderVersionCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		JPanel loaderColumn = Ui.column();
		loaderColumn.setPreferredSize(new Dimension(220, 54));
		loaderColumn.add(Ui.caps("Mod loader"));
		loaderColumn.add(Ui.gap(6));
		loaderColumn.add(loaderCombo);
		JPanel versionColumn = Ui.column();
		versionColumn.add(Ui.caps("Loader version"));
		versionColumn.add(Ui.gap(6));
		versionColumn.add(loaderVersionCombo);
		loaderRow.add(loaderColumn, BorderLayout.WEST);
		loaderRow.add(versionColumn, BorderLayout.CENTER);
		loaderRow.setAlignmentX(LEFT_ALIGNMENT);
		loaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		content.add(loaderRow);
		content.add(Ui.gap(16));

		JPanel memoryRow = Ui.row(new BorderLayout(12, 0));
		memoryRow.add(Ui.caps("Memory"), BorderLayout.WEST);
		memoryRow.add(memoryLabel, BorderLayout.EAST);
		memoryRow.setAlignmentX(LEFT_ALIGNMENT);
		memoryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		content.add(memoryRow);
		content.add(Ui.gap(4));
		content.add(Ui.fill(memorySlider));
		content.add(Ui.gap(16));

		JPanel resolutionRow = Ui.row(new BorderLayout(12, 0));
		resolutionRow.add(Ui.text("Fixed window size"), BorderLayout.WEST);
		resolutionRow.add(resolutionSwitch, BorderLayout.EAST);
		resolutionRow.setAlignmentX(LEFT_ALIGNMENT);
		resolutionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		content.add(resolutionRow);
		content.add(Ui.gap(8));

		JPanel sizeRow = Ui.row();
		widthField.setPreferredSize(new Dimension(90, 32));
		heightField.setPreferredSize(new Dimension(90, 32));
		sizeRow.add(widthField);
		sizeRow.add(Ui.hgap(8));
		sizeRow.add(Ui.dim("×"));
		sizeRow.add(Ui.hgap(8));
		sizeRow.add(heightField);
		sizeRow.add(Box.createHorizontalGlue());
		sizeRow.setAlignmentX(LEFT_ALIGNMENT);
		sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		content.add(sizeRow);
		content.add(Ui.gap(16));

		content.add(Ui.caps("Extra JVM arguments"));
		content.add(Ui.gap(6));
		content.add(Ui.fill(jvmField));
		content.add(Ui.gap(20));

		JPanel buttons = Ui.row();
		buttons.add(Ui.primary("Save").size(120, 38).onClick(button -> save()));
		buttons.add(Ui.hgap(10));
		buttons.add(Ui.secondary("Use this instance").size(170, 38).onClick(button -> {
			save();
			app.config.activeInstance = app.config.active().name;
			app.config.save();
			app.refresh();
			app.status("Active instance is now " + app.config.active().name);
		}));
		buttons.add(Ui.hgap(10));
		buttons.add(new Ui.Button("Delete", Ui.Variant.DANGER).size(110, 38).onClick(button -> delete()));
		buttons.add(Box.createHorizontalGlue());
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		content.add(buttons);
		content.add(Box.createVerticalGlue());
		return card;
	}

	private void create() {
		String name = app.config.uniqueInstanceName();
		Instance instance = new Instance(name);
		instance.memoryMb = app.config.defaultMemoryMb;
		instance.minecraftVersion = VersionCatalog.latestRelease().isBlank() ? app.config.active().minecraftVersion : VersionCatalog.latestRelease();
		app.config.instances.add(instance);
		app.config.activeInstance = name;
		app.config.save();
		app.status("Created " + name);
		app.refresh();
	}

	private void save() {
		Instance instance = app.config.active();
		String name = nameField.getText().trim();

		if (name.isEmpty()) {
			JOptionPane.showMessageDialog(this, "An instance needs a name.", "Shrd Launcher", JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (!name.equals(instance.name) && app.config.findInstance(name) != null) {
			JOptionPane.showMessageDialog(this, "There is already an instance called " + name + ".", "Shrd Launcher", JOptionPane.WARNING_MESSAGE);
			return;
		}

		instance.name = name;
		instance.minecraftVersion = String.valueOf(versionCombo.getSelectedItem());
		instance.loader = "Vanilla".equals(loaderCombo.getSelectedItem()) ? "vanilla" : "fabric";
		Object loaderVersion = loaderVersionCombo.getSelectedItem();
		instance.loaderVersion = loaderVersion == null || "latest".equals(loaderVersion) ? "" : String.valueOf(loaderVersion);
		instance.memoryMb = memorySlider.getValue() * 512;
		instance.customResolution = resolutionSwitch.isOn();
		instance.width = parseInt(widthField.getText(), 1280);
		instance.height = parseInt(heightField.getText(), 720);
		instance.extraJvmArgs = jvmField.getText().trim();
		app.config.activeInstance = instance.name;
		app.config.save();
		app.status("Saved " + instance.name);
		app.refresh();
	}

	private void delete() {
		Instance instance = app.config.active();

		if (app.config.instances.size() <= 1) {
			JOptionPane.showMessageDialog(this, "At least one instance has to stay.", "Shrd Launcher", JOptionPane.WARNING_MESSAGE);
			return;
		}

		int choice = JOptionPane.showConfirmDialog(this,
			"Remove the instance \"" + instance.name + "\"?\nIts folder is left on disk — delete it yourself if you want the space back:\n" + ShrdPaths.pretty(instance.dir()),
			"Shrd Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

		if (choice != JOptionPane.OK_OPTION) {
			return;
		}

		app.config.instances.remove(instance);
		app.config.activeInstance = app.config.instances.get(0).name;
		app.config.save();
		app.refresh();
	}

	private static int parseInt(String text, int fallback) {
		try {
			return Math.max(320, Integer.parseInt(text.trim()));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void reload() {
		Instance active = app.config.active();

		listRows.removeAll();

		for (Instance instance : app.config.instances) {
			Ui.Row row = new Ui.Row();
			row.add(Ui.text(instance.name), BorderLayout.WEST);
			row.add(Ui.dim(instance.minecraftVersion + (instance.isFabric() ? " · Fabric" : "")), BorderLayout.EAST);
			row.setSelected(instance == active);
			row.selectable(clicked -> {
				app.config.activeInstance = instance.name;
				app.config.save();
				app.refresh();
			});
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
			listRows.add(row);
			listRows.add(Ui.gap(6));
		}

		editorTitle.setText(active.name);
		editorPath.setText(ShrdPaths.pretty(active.dir()));
		nameField.setText(active.name);
		loaderCombo.setSelectedItem(active.isFabric() ? "Fabric" : "Vanilla");
		loaderVersionCombo.setSelectedItem(active.loaderVersion.isBlank() ? "latest" : active.loaderVersion);
		memorySlider.setValue(Math.max(1, Math.min(16, active.memoryMb / 512)));
		memoryLabel.setText((memorySlider.getValue() * 512) + " MB");
		resolutionSwitch.set(active.customResolution, false);
		widthField.setText(String.valueOf(active.width));
		heightField.setText(String.valueOf(active.height));
		jvmField.setText(active.extraJvmArgs == null ? "" : active.extraJvmArgs);

		VersionCatalog.releases(list -> {
			if (list.isEmpty()) {
				return;
			}

			List<String> items = new ArrayList<>(list);
			versionCombo.removeAllItems();

			for (String id : items) {
				versionCombo.addItem(id);
			}

			versionCombo.setSelectedItem(active.minecraftVersion);
		});

		VersionCatalog.fabricLoaders(active.minecraftVersion, list -> {
			loaderVersionCombo.removeAllItems();
			loaderVersionCombo.addItem("latest");

			for (String version : list) {
				loaderVersionCombo.addItem(version);
			}

			loaderVersionCombo.setSelectedItem(active.loaderVersion.isBlank() ? "latest" : active.loaderVersion);
		});

		loaderCombo.setForeground(Theme.TEXT);
		repaint();
	}
}
