package dev.shrd.launcher.ui;

import dev.shrd.launcher.Account;
import dev.shrd.launcher.Instance;
import dev.shrd.launcher.LaunchService;
import dev.shrd.launcher.LauncherConfig;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;
import dev.shrd.launcher.launch.JavaRuntime;
import dev.shrd.launcher.update.UpdateService;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The state every page shares, plus the one place a launch is started from. */
public final class App {

	public interface StageListener {
		void changed(String stage, String detail);
	}

	private final List<Runnable> refreshers = new ArrayList<>();
	private final List<StageListener> stageListeners = new ArrayList<>();
	private Consumer<String> statusHandler = text -> {
	};
	private Consumer<Double> progressHandler = fraction -> {
	};

	public final LauncherConfig config;
	public final LaunchService service = new LaunchService();
	/** Shared by the pages that show an update: Home's alert row and the Settings card. */
	public final UpdateService updates = new UpdateService();
	private String stage = "idle";
	private String detail = "Ready";
	private double lastProgress;

	public App(LauncherConfig config) {
		this.config = config;
		updates.setManifestUrl(config.updateManifestUrl);
		// Any change to the update state redraws every page; the service hops to the event thread
		// before calling this, so it is safe to touch Swing from here.
		updates.setListener(this::refresh);
	}

	/**
	 * One check per run, off the event thread, and only when the user has left it on. Started from
	 * the GUI entry point rather than the frame, so {@code --shot} never reaches the network.
	 */
	public void startUpdateCheck() {
		if (config.checkForUpdatesOnStart) {
			updates.checkQuietly();
		}
	}

	/**
	 * The one place the update button's action lives, so Home and Settings behave identically: the
	 * first press fetches the update, the second swaps it in and restarts.
	 */
	public void updateNow() {
		switch (updates.state()) {
			case READY -> {
				if (!updates.canSelfInstall()) {
					// A development or unpacked copy: the download is real, but nothing may swap it in.
					status("Unzip the downloaded build over this folder to finish — opened for you");
					openFolder(updates.prepared().getParent());
					return;
				}

				config.save();

				if (updates.apply()) {
					System.exit(0);
				}

				status("Could not start the update — see the Console page");
			}
			case AVAILABLE -> updates.download();
			default -> updates.check();
		}
	}

	public void onRefresh(Runnable refresher) {
		refreshers.add(refresher);
	}

	public void onStage(StageListener listener) {
		stageListeners.add(listener);
	}

	public void refresh() {
		for (Runnable refresher : new ArrayList<>(refreshers)) {
			refresher.run();
		}
	}

	public void setStatusHandler(Consumer<String> handler) {
		this.statusHandler = handler;
	}

	public void setProgressHandler(Consumer<Double> handler) {
		this.progressHandler = handler;
	}

	public String stage() {
		return stage;
	}

	public String detail() {
		return detail;
	}

	private void setStage(String stage, String detail) {
		this.stage = stage;
		this.detail = detail;
		statusHandler.accept(detail);

		for (StageListener listener : new ArrayList<>(stageListeners)) {
			listener.changed(stage, detail);
		}
	}

	public void status(String text) {
		setStage(stage, text);
	}

	public void progress(double fraction) {
		lastProgress = fraction;
		progressHandler.accept(fraction);
	}

	/** The fraction last reported, so a page can draw the same bar the status strip does. */
	public double lastProgress() {
		return lastProgress;
	}

	public boolean isBusy() {
		return service.isPreparing();
	}

	public boolean isRunning() {
		return service.isRunning();
	}

	// ------------------------------------------------------------------ java

	public Path java() {
		return JavaRuntime.detect(config.javaPath);
	}

	public String javaDescription() {
		Path java = java();

		if (java == null) {
			return "no Java found";
		}

		String version = JavaRuntime.version(java);
		return version == null ? "unreadable" : "Java " + version;
	}

	// ------------------------------------------------------------------ launching

	public void play() {
		if (service.isPreparing() || service.isRunning()) {
			return;
		}

		Instance instance = config.active();
		Account account = config.activeAccount();
		setStage("resolving", "Resolving " + instance.minecraftVersion + " …");
		progress(0.03);

		Thread worker = new Thread(() -> service.play(config, instance, account, new LaunchService.Listener() {
			@Override
			public void stage(String newStage, String newDetail) {
				SwingUtilities.invokeLater(() -> setStage(newStage, newDetail));
			}

			@Override
			public void progress(int done, int total, long bytesDone, long bytesTotal) {
				double fraction = 0.1 + (total <= 0 ? 0.5 : (double) done / total) * 0.8;
				SwingUtilities.invokeLater(() -> App.this.progress(fraction));
			}

			@Override
			public void note(String message) {
				SwingUtilities.invokeLater(() -> status(message));
			}

			@Override
			public void done(boolean success, String message) {
				SwingUtilities.invokeLater(() -> {
					setStage(success ? "running" : "idle", message);
					App.this.progress(success ? 1.0 : 0.0);
					refresh();

					if (success && config.closeOnLaunch) {
						// The game owns the screen now; the launcher steps out of the way.
						System.exit(0);
					}
				});
			}
		}), "shrd-launch");

		worker.setDaemon(true);
		worker.start();
	}

	public void stopGame() {
		service.stop();
		progress(0);
		setStage("idle", "Minecraft stopped");
		refresh();
	}

	public void togglePlay() {
		if (service.isRunning()) {
			stopGame();
		} else {
			play();
		}
	}

	/** Opens a folder in the OS file manager. */
	public void openFolder(Path folder) {
		try {
			java.nio.file.Files.createDirectories(folder);
			String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
			ProcessBuilder builder;

			if (os.contains("win")) {
				builder = new ProcessBuilder("explorer", folder.toAbsolutePath().toString());
			} else if (os.contains("mac")) {
				builder = new ProcessBuilder("open", folder.toAbsolutePath().toString());
			} else {
				builder = new ProcessBuilder("xdg-open", folder.toAbsolutePath().toString());
			}

			builder.start();
		} catch (Exception e) {
			Log.warn("could not open %s: %s", ShrdPaths.pretty(folder), e.getMessage());
		}
	}
}
