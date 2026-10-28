package dev.shrd.launcher.ui.pages;

import dev.shrd.launcher.Instance;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.Modrinth;
import dev.shrd.launcher.ModsManager;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.ui.App;
import dev.shrd.launcher.ui.Icons;
import dev.shrd.launcher.ui.Page;
import dev.shrd.launcher.ui.Theme;
import dev.shrd.launcher.ui.Ui;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * The instance's mods, and Modrinth beside them.
 * <p>
 * The page has two views: what is already in the folder, and a browser that talks to Modrinth. The
 * browser keeps the instance in mind — it filters by its Minecraft version and loader by default, and
 * an Install writes the jar into that instance's own folder, so nothing has to be downloaded by hand
 * and dropped in. Resource packs and shader packs use the same API and land in the folders the game
 * reads them from.
 */
public final class ModsPage extends Page {

	private final JPanel installedRows = Ui.column();
	private final JLabel installedCount = Ui.dim("");
	private final JLabel folderLine = Ui.dim("");
	private final JLabel warning = Ui.dim("");

	private final CardLayout views = new CardLayout();
	private final JPanel viewHolder = new JPanel(views);
	private final Tabs mainTabs = new Tabs("Installed", "Browse Modrinth");

	private final JPanel results = Ui.column();
	private final JTextField search = new Ui.Field("Search Modrinth…");
	private final JLabel countLabel = Ui.dim("");
	private final JLabel browseStatus = Ui.dim("");
	private final Ui.Combo<String> sort = new Ui.Combo<>(new String[] { "Relevance", "Downloads", "Follows", "Newest", "Updated" }, "Relevance");
	private final Ui.Combo<Integer> pageSize = new Ui.Combo<>(new Integer[] { 20, 40 }, 20);
	private final Ui.Combo<String> versionFilter;
	private final Ui.Combo<String> loaderFilter;
	private final Ui.Combo<String> categoryFilter;
	private final Ui.Button previous = Ui.secondary("Previous").size(104, 32);
	private final Ui.Button next = Ui.secondary("Next").size(104, 32);
	private final Ui.Button searchButton = Ui.primary("Search").size(110, 36);

	private String type = Modrinth.MODS;
	private int offset;
	private int total;
	/** Bumped on every search, so a slow answer from an older query cannot overwrite a newer one. */
	private int requestId;

	public ModsPage(App app) {
		super(app, "Mods", "Install mods, resource packs and shaders straight into this instance");

		Instance instance = app.config.active();
		String[] versions = { Modrinth.ANY, instance.minecraftVersion, "1.21.8", "1.21.1", "1.20.1", "1.19.2", "1.16.5" };
		versionFilter = new Ui.Combo<>(versions, instance.minecraftVersion);
		loaderFilter = new Ui.Combo<>(new String[] { Modrinth.ANY, "fabric", "quilt", "forge", "neoforge" },
			instance.isFabric() ? "fabric" : instance.loader);
		categoryFilter = new Ui.Combo<>(new String[] { Modrinth.ANY, "adventure", "decoration", "equipment", "food",
			"magic", "optimization", "technology", "utility", "worldgen", "library" }, Modrinth.ANY);

		actions().add(fixed(Ui.primary("Install Shrd Client").onClick(button -> installClient()), 180, 34));
		actions().add(Ui.hgap(10));
		actions().add(fixed(Ui.secondary("Get Fabric API").onClick(button -> installFabricApi()), 150, 34));
		actions().add(Ui.hgap(10));
		actions().add(fixed(Ui.secondary("Open mods folder").withGlyph(Icons::folder)
			.onClick(button -> app.openFolder(app.config.active().modsDir())), 170, 34));

		// Without this the CardLayout panel keeps the look-and-feel's light grey, which shows
		// through the gap between the two cards as a bright stripe.
		viewHolder.setOpaque(false);
		viewHolder.add(installedView(), "installed");
		viewHolder.add(browseView(), "browse");

		JPanel page = new JPanel(new BorderLayout(0, 14));
		page.setOpaque(false);
		page.add(mainTabs, BorderLayout.NORTH);
		page.add(viewHolder, BorderLayout.CENTER);
		body(page);

		mainTabs.onSelect(index -> {
			views.show(viewHolder, index == 0 ? "installed" : "browse");

			if (index == 1 && results.getComponentCount() == 0) {
				search(0);
			}
		});

		// Browsing is what a player opens this page for; the installed list is one tab away.
		mainTabs.select(1);
		views.show(viewHolder, "browse");

		search.addActionListener(event -> search(0));
		searchButton.onClick(button -> search(0));
		sort.addActionListener(event -> search(0));
		pageSize.addActionListener(event -> search(0));
		versionFilter.addActionListener(event -> search(0));
		loaderFilter.addActionListener(event -> search(0));
		categoryFilter.addActionListener(event -> search(0));
		previous.onClick(button -> search(Math.max(0, offset - pageSize())));
		next.onClick(button -> {
			if (offset + pageSize() < total) {
				search(offset + pageSize());
			}
		});

		app.onRefresh(this::reload);
		SwingUtilities.invokeLater(() -> search(0));
	}

	/** Button.size() lets a button grow, which is right in a grid and wrong in a header row. */
	private static Ui.Button fixed(Ui.Button button, int width, int height) {
		button.setPreferredSize(new Dimension(width, height));
		button.setMinimumSize(new Dimension(width, height));
		button.setMaximumSize(new Dimension(width, height));
		return button;
	}

	// ------------------------------------------------------------------ installed

	private JPanel installedView() {
		JPanel column = Ui.column();
		column.add(installedCount);
		column.add(Ui.gap(10));
		column.add(installedRows);
		column.add(Box.createVerticalGlue());

		Ui.Card card = new Ui.Card("Installed mods");
		card.body().add(Ui.scroll(column));

		JPanel footer = Ui.column();
		footer.add(folderLine);
		footer.add(Ui.gap(6));
		footer.add(warning);
		footer.add(Ui.gap(6));
		footer.add(Ui.dim("Fabric mods need the Fabric loader — switch it on the Instances page if this instance is Vanilla."));
		footer.setAlignmentX(LEFT_ALIGNMENT);

		JPanel view = new JPanel(new BorderLayout(0, 10));
		view.setOpaque(false);
		view.add(card, BorderLayout.CENTER);
		view.add(footer, BorderLayout.SOUTH);
		return view;
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
				SwingUtilities.invokeLater(() -> {
					app.status(message);
					reload();
				});
			} catch (Exception e) {
				Log.error("%s", e.getMessage());
				SwingUtilities.invokeLater(() -> {
					app.status("Failed: " + e.getMessage());
					JOptionPane.showMessageDialog(this, e.getMessage(), "Shrd Launcher", JOptionPane.ERROR_MESSAGE);
				});
			}
		}, "shrd-mod-install");

		thread.setDaemon(true);
		thread.start();
	}

	// ------------------------------------------------------------------ the browser

	private JPanel browseView() {
		JPanel browse = new JPanel(new BorderLayout(14, 12));
		browse.setOpaque(false);

		Ui.Card listCard = new Ui.Card("Modrinth");
		JPanel top = Ui.row(new BorderLayout(10, 0));
		top.add(Ui.fill(search), BorderLayout.CENTER);
		JPanel topRight = Ui.row();
		topRight.add(searchButton);
		top.add(topRight, BorderLayout.EAST);
		// A BoxLayout gives spare height to whatever will take it, and a text field will: without
		// this cap the search box grows into the whole card when the result list is empty.
		top.setAlignmentX(LEFT_ALIGNMENT);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		top.setPreferredSize(new Dimension(10, 40));
		listCard.body().add(top);
		listCard.body().add(Ui.gap(10));

		JPanel pager = Ui.row(new BorderLayout(10, 0));
		pager.add(countLabel, BorderLayout.WEST);
		JPanel arrows = Ui.row();
		arrows.add(sort);
		arrows.add(Ui.hgap(8));
		arrows.add(pageSize);
		arrows.add(Ui.hgap(12));
		arrows.add(previous);
		arrows.add(Ui.hgap(6));
		arrows.add(next);
		pager.add(arrows, BorderLayout.EAST);
		pager.setAlignmentX(LEFT_ALIGNMENT);
		pager.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		listCard.body().add(pager);
		listCard.body().add(Ui.gap(10));
		browseStatus.setAlignmentX(LEFT_ALIGNMENT);
		browseStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		listCard.body().add(browseStatus);
		listCard.body().add(Ui.gap(8));

		JPanel list = Ui.column();
		list.add(results);
		list.add(Box.createVerticalGlue());
		JScrollPane scroller = Ui.scroll(list);
		// Its own preference, not the summed height of whatever rows happen to exist; the scroll
		// pane then takes the rest of the card instead of the search row claiming it.
		scroller.setPreferredSize(new Dimension(10, 320));
		listCard.body().add(scroller);

		browse.add(listCard, BorderLayout.CENTER);
		browse.add(filtersCard(), BorderLayout.WEST);
		return browse;
	}

	private JComponent filtersCard() {
		Ui.Card card = new Ui.Card("Browse");
		card.setPreferredSize(new Dimension(248, 10));

		// The three project types the launcher can actually place: mods, resource packs, shaders.
		Tabs types = new Tabs("Mods", "Packs", "Shaders");
		types.setToolTipText("Mods install to mods/, packs to resourcepacks/, shaders to shaderpacks/");
		types.onSelect(index -> {
			type = switch (index) {
				case 1 -> Modrinth.RESOURCE_PACKS;
				case 2 -> Modrinth.SHADER_PACKS;
				default -> Modrinth.MODS;
			};
			// Packs are not loaded by a mod loader, so the loader facet would filter them all out.
			loaderFilter.setEnabled(Modrinth.MODS.equals(type));
			search(0);
		});
		card.body().add(types);
		card.body().add(Ui.gap(14));

		card.body().add(Ui.caps("Game version"));
		card.body().add(Ui.gap(6));
		card.body().add(Ui.fill(versionFilter));
		card.body().add(Ui.gap(12));

		card.body().add(Ui.caps("Mod loader"));
		card.body().add(Ui.gap(6));
		card.body().add(Ui.fill(loaderFilter));
		card.body().add(Ui.gap(12));

		card.body().add(Ui.caps("Category"));
		card.body().add(Ui.gap(6));
		card.body().add(Ui.fill(categoryFilter));
		card.body().add(Ui.gap(14));
		card.body().add(Ui.dim("Filters use the active instance's version by default."));
		return card;
	}

	private int pageSize() {
		Object value = pageSize.getSelectedItem();
		return value instanceof Integer number ? number : 20;
	}

	/** Runs a search off the event thread and drops the answer if a newer search has started. */
	private void search(int newOffset) {
		String query = search.getText().trim();
		String version = (String) versionFilter.getSelectedItem();
		String loader = (String) loaderFilter.getSelectedItem();
		String category = (String) categoryFilter.getSelectedItem();
		String index = switch (sort.getSelectedIndex()) {
			case 1 -> "downloads";
			case 2 -> "follows";
			case 3 -> "newest";
			case 4 -> "updated";
			default -> "relevance";
		};

		offset = Math.max(0, newOffset);
		int limit = pageSize();
		int mine = ++requestId;
		previous.setEnabled(false);
		next.setEnabled(false);
		browseStatus.setForeground(Theme.TEXT_DIM);
		browseStatus.setText("Searching Modrinth …");
		countLabel.setText("");

		Thread worker = new Thread(() -> {
			try {
				Modrinth.Page page = Modrinth.search(query, type, loader, version, category, index, limit, offset);
				SwingUtilities.invokeLater(() -> {
					if (mine == requestId) {
						showPage(page, limit);
					}
				});
			} catch (Exception e) {
				Log.warn("Modrinth search failed: %s", e.getMessage());
				SwingUtilities.invokeLater(() -> {
					if (mine == requestId) {
						browseStatus.setForeground(Theme.BAD);
						browseStatus.setText("Modrinth could not be reached — " + e.getMessage());
					}
				});
			}
		}, "shrd-modrinth-search");
		worker.setDaemon(true);
		worker.start();
	}

	private void showPage(Modrinth.Page page, int limit) {
		total = page.total();
		results.removeAll();
		browseStatus.setForeground(Theme.TEXT_DIM);
		browseStatus.setText(page.hits().isEmpty() ? "Nothing matched. Try a wider game version or category." : "");
		countLabel.setText(page.hits().isEmpty()
			? ""
			: (offset + 1) + "–" + (offset + page.hits().size()) + " of " + String.format("%,d", total) + " results");
		previous.setEnabled(offset > 0);
		next.setEnabled(offset + limit < total);

		for (Modrinth.Hit hit : page.hits()) {
			results.add(resultRow(hit));
			results.add(Ui.gap(9));
		}

		results.add(Ui.gap(0));
		results.revalidate();
		results.repaint();
	}

	/** One search result: icon, name and author, the description, the numbers, and Install. */
	private JPanel resultRow(Modrinth.Hit hit) {
		Ui.Row row = new Ui.Row();
		row.hoverable();
		row.setLayout(new BorderLayout(14, 0));

		IconView icon = new IconView(hit);
		row.add(icon, BorderLayout.WEST);

		JPanel titles = Ui.column();
		JLabel title = new JLabel(hit.title());
		title.setFont(Theme.bold(16));
		title.setForeground(Theme.TEXT);
		titles.add(title);
		titles.add(Ui.gap(3));
		titles.add(Ui.dim(hit.description().length() > 110 ? hit.description().substring(0, 107) + "…" : hit.description()));
		titles.add(Ui.gap(4));
		titles.add(Ui.dim("by " + hit.author() + "  ·  " + hit.downloadsText() + " downloads  ·  "
			+ hit.followersText() + " followers  ·  " + hit.updatedText()));
		row.add(titles, BorderLayout.CENTER);

		Ui.Button install = Ui.primary("Install").size(108, 36).withGlyph(Icons::plus);
		install.onClick(button -> install(hit, install));
		JPanel controls = Ui.row();
		controls.add(install);
		row.add(controls, BorderLayout.EAST);

		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
		return row;
	}

	private void install(Modrinth.Hit hit, Ui.Button button) {
		Instance instance = app.config.active();
		String version = (String) (versionFilter.getSelectedItem());
		String loader = (String) loaderFilter.getSelectedItem();
		String kind = type;
		button.setEnabled(false);
		button.setText("Installing…");
		app.status("Installing " + hit.title() + " …");

		Thread worker = new Thread(() -> {
			try {
				List<Path> files = Modrinth.install(instance, hit, kind, loader, version);
				Path main = files.get(0);
				int dependencies = files.size() - 1;
				SwingUtilities.invokeLater(() -> {
					button.setText("Installed");
					app.status("Installed " + main.getFileName()
						+ (dependencies > 0 ? " and " + dependencies + (dependencies == 1 ? " dependency" : " dependencies") : "")
						+ " into the " + Modrinth.label(kind) + " folder");
					reload();
				});
			} catch (Exception e) {
				Log.warn("could not install %s: %s", hit.title(), e.getMessage());
				SwingUtilities.invokeLater(() -> {
					button.setText("Install");
					button.setEnabled(true);
					app.status("Could not install " + hit.title());
					JOptionPane.showMessageDialog(this, e.getMessage(), "Shrd Launcher", JOptionPane.ERROR_MESSAGE);
				});
			}
		}, "shrd-modrinth-install");
		worker.setDaemon(true);
		worker.start();
	}

	/** The project icon, fetched once and cached; a plain tile until it arrives. */
	private static final class IconView extends JComponent {
		private final Modrinth.Hit hit;
		private BufferedImage image;

		private IconView(Modrinth.Hit hit) {
			this.hit = hit;
			setPreferredSize(new Dimension(48, 48));
			setMaximumSize(new Dimension(48, 48));
			setMinimumSize(new Dimension(48, 48));

			Thread loader = new Thread(() -> {
				try {
					Path file = Modrinth.icon(hit);

					if (file != null) {
						BufferedImage read = ImageIO.read(file.toFile());
						SwingUtilities.invokeLater(() -> {
							image = read;
							repaint();
						});
					}
				} catch (Exception ignored) {
					// A missing icon is a blank tile, never an error the player has to read.
				}
			}, "shrd-modrinth-icon");
			loader.setDaemon(true);
			loader.start();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setColor(Theme.TRACK);
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 10);
			g.setColor(Theme.alpha(Theme.ACCENT, 70));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

			if (image != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
				g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
			} else {
				g.setColor(Theme.TEXT_ACCENT);
				g.setFont(Theme.bold(18));
				String initial = hit.title().isEmpty() ? "?" : hit.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
				int width = g.getFontMetrics().stringWidth(initial);
				g.drawString(initial, (getWidth() - width) / 2, getHeight() / 2 + 7);
			}

			g.dispose();
		}
	}

	// ------------------------------------------------------------------ tabs

	/** A row of equal-width tabs; the selected one is the accent, like the reference launchers. */
	private static final class Tabs extends JComponent {
		interface Listener {
			void selected(int index);
		}

		private final String[] labels;
		private int selected;
		private int hovered = -1;
		private Listener listener;

		private Tabs(String... labels) {
			this.labels = labels;
			setPreferredSize(new Dimension(10, 40));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
			setMinimumSize(new Dimension(10, 40));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent event) {
					int index = indexAt(event.getX());

					if (index >= 0 && index != selected) {
						selected = index;
						repaint();

						if (listener != null) {
							listener.selected(index);
						}
					}
				}

				@Override
				public void mouseExited(MouseEvent event) {
					hovered = -1;
					repaint();
				}
			});
			addMouseMotionListener(new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent event) {
					int index = indexAt(event.getX());

					if (index != hovered) {
						hovered = index;
						repaint();
					}
				}
			});
		}

		private void onSelect(Listener value) {
			this.listener = value;
		}

		/** Sets the tab without notifying anyone; used for the view the page opens on. */
		private void select(int index) {
			selected = Math.max(0, Math.min(labels.length - 1, index));
			repaint();
		}

		private int indexAt(int x) {
			int width = Math.max(1, getWidth()) / labels.length;
			int index = Math.max(0, Math.min(labels.length - 1, x / Math.max(1, width)));
			return x < 2 ? -1 : index;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			Ui.hints(g);
			g.setColor(Theme.TRACK);
			Ui.roundRect(g, 0, 0, getWidth(), getHeight(), 10);
			g.setColor(Theme.alpha(Theme.ACCENT, 60));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

			int slot = getWidth() / labels.length;

			for (int i = 0; i < labels.length; i++) {
				boolean active = i == selected;
				int x = i * slot;

				if (active) {
					g.setPaint(new java.awt.GradientPaint(0, 4, Theme.ACCENT, 0, getHeight() - 4, Theme.ACCENT_DEEP));
					Ui.roundRect(g, x + 3, 4, slot - 6, getHeight() - 8, 7);
				} else if (i == hovered) {
					g.setColor(Theme.ROW_HOVER);
					Ui.roundRect(g, x + 3, 4, slot - 6, getHeight() - 8, 7);
				}

				g.setFont(active ? Theme.bold(13) : Theme.font(13));
				g.setColor(active ? Theme.ON_ACCENT : Theme.TEXT_DIM);
				int textWidth = g.getFontMetrics().stringWidth(labels[i]);
				g.drawString(labels[i], x + (slot - textWidth) / 2, getHeight() / 2 + 5);
			}

			g.dispose();
		}
	}

	// ------------------------------------------------------------------ installed rows

	@Override
	public void reload() {
		Instance instance = app.config.active();
		setSubtitle("Mods for " + instance.name + " — Minecraft " + instance.minecraftVersion + (instance.isFabric() ? " · Fabric" : " · Vanilla"));
		List<ModsManager.Mod> mods = ModsManager.list(instance);
		installedCount.setText(mods.size() + (mods.size() == 1 ? " mod" : " mods"));
		folderLine.setText("Folder: " + ShrdPaths.pretty(instance.modsDir()));
		warning.setText(instance.isFabric()
			? "Shrd Client needs Fabric API in this folder — one press of \"Get Fabric API\" installs it."
			: "This instance is Vanilla, so no Fabric mod here will load.");
		installedRows.removeAll();

		if (mods.isEmpty()) {
			installedRows.add(Ui.dim("Nothing here yet. Use \"Install Shrd Client\", or browse Modrinth."));
			installedRows.add(Ui.gap(10));
		}

		for (ModsManager.Mod mod : mods) {
			installedRows.add(modRow(mod));
			installedRows.add(Ui.gap(8));
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
