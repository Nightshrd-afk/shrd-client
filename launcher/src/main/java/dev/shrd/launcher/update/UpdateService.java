package dev.shrd.launcher.update;

import dev.shrd.launcher.Log;

import java.nio.file.Path;

/**
 * The launcher's update state, shared by every page that cares: whether a check is running, what
 * the published version is, and where a downloaded update is waiting. The work happens off the
 * event thread; {@code onChange} is what the pages use to redraw.
 */
public final class UpdateService {

	public enum State {
		/** Nothing has been asked yet. */
		IDLE,
		CHECKING,
		/** The published version is the one that is running. */
		CURRENT,
		/** A newer version exists and can be downloaded. */
		AVAILABLE,
		DOWNLOADING,
		/** Downloaded and unpacked; the next restart swaps it in. */
		READY,
		FAILED
	}

	public interface Listener {
		void changed();
	}

	private static final Listener NONE = () -> {
	};

	private Listener listener = NONE;
	private volatile State state = State.IDLE;
	private volatile UpdateManifest manifest;
	private volatile String message = "";
	private volatile Path prepared;
	private volatile boolean running;

	private String manifestUrl = "";

	public void setListener(Listener listener) {
		this.listener = listener == null ? NONE : listener;
	}

	public void setManifestUrl(String url) {
		this.manifestUrl = url == null ? "" : url.trim();
	}

	public State state() {
		return state;
	}

	public UpdateManifest manifest() {
		return manifest;
	}

	public String message() {
		return message;
	}

	public boolean busy() {
		return running;
	}

	/** The running version, for display. */
	public String currentVersion() {
		return dev.shrd.launcher.Version.CURRENT;
	}

	public Path prepared() {
		return prepared;
	}

	public String describe() {
		switch (state) {
			case IDLE:
				return "Shrd Launcher " + currentVersion() + " — no check run yet";
			case CHECKING:
				return "Checking for updates…";
			case CURRENT:
				return "Shrd Launcher " + currentVersion() + " is the newest version";
			case AVAILABLE:
				return "Shrd Launcher " + (manifest == null ? "?" : manifest.version) + " is available";
			case DOWNLOADING:
				return message;
			case READY:
				return "Shrd Launcher " + (manifest == null ? "?" : manifest.version) + " is downloaded and ready";
			default:
				return message.isBlank() ? "the update check failed" : message;
		}
	}

	/** True when there is something for the user to act on. */
	public boolean offerable() {
		return state == State.AVAILABLE || state == State.READY;
	}

	/** Checks quietly on startup: a failure here is a log line, not something to interrupt for. */
	public void checkQuietly() {
		if (manifestUrl.isBlank() || running) {
			return;
		}

		run(() -> {
			try {
				UpdateManifest found = Updater.check(manifestUrl, stage -> set(State.CHECKING, stage, null));

				if (found == null) {
					set(State.CURRENT, "", null);
				} else {
					set(State.AVAILABLE, found.notes, found);
					Log.title("Shrd Launcher %s is available (you have %s)", found.version, currentVersion());
				}
			} catch (Exception e) {
				Log.info("update check skipped: %s", reason(e));
				set(State.IDLE, "", null);
			}
		});
	}

	/** The button press: same check, but the result is reported either way. */
	public void check() {
		if (running) {
			return;
		}

		if (manifestUrl.isBlank()) {
			set(State.FAILED, "set a manifest URL first", null);
			return;
		}

		run(() -> {
			try {
				UpdateManifest found = Updater.check(manifestUrl, stage -> set(State.CHECKING, stage, null));

				if (found == null) {
					set(State.CURRENT, "", null);
					Log.info("no update available; %s is the newest version", currentVersion());
				} else {
					set(State.AVAILABLE, found.notes, found);
					Log.title("Shrd Launcher %s is available (you have %s)", found.version, currentVersion());
				}
			} catch (Exception e) {
				Log.error("update check failed: %s", reason(e));
				set(State.FAILED, "could not reach the update manifest — " + reason(e), null);
			}
		});
	}

	/** Downloads and unpacks the available update. */
	public void download() {
		if (running || manifest == null) {
			return;
		}

		UpdateManifest target = manifest;
		run(() -> {
			try {
				Path root = Updater.prepare(manifestUrl, target, stage -> set(State.DOWNLOADING, stage, target));
				prepared = root;
				set(State.READY, "", target);
				Log.title("update %s downloaded to %s", target.version, root);
			} catch (Exception e) {
				Log.error("update download failed: %s", reason(e));
				set(State.FAILED, reason(e), target);
			}
		});
	}

	/**
	 * Swaps the downloaded update in. Returns false when this build is not an installed copy, in
	 * which case the caller should say so rather than pretend the update was applied.
	 */
	public boolean apply() {
		Path install = Updater.installDir();

		if (prepared == null || install == null) {
			return false;
		}

		try {
			Updater.apply(prepared, install);
			return true;
		} catch (Exception e) {
			Log.error("could not start the update: %s", reason(e));
			set(State.FAILED, reason(e), manifest);
			return false;
		}
	}

	public boolean canSelfInstall() {
		return Updater.canSelfInstall();
	}

	/** A connection failure often has no message, only a type, and "null" in the log helps nobody. */
	private static String reason(Exception e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}

	private void run(Runnable work) {
		running = true;
		Thread worker = new Thread(work, "shrd-update");
		worker.setDaemon(true);
		worker.start();
	}

	private void set(State newState, String newMessage, UpdateManifest newManifest) {
		state = newState;
		message = newMessage == null ? "" : newMessage;

		if (newManifest != null) {
			manifest = newManifest;
		}

		if (newState != State.CHECKING && newState != State.DOWNLOADING) {
			running = false;
		}

		// Pages redraw from a Swing timer as well, so a null listener is survivable here.
		try {
			javax.swing.SwingUtilities.invokeLater(listener::changed);
		} catch (Exception ignored) {
			// The launcher is shutting down; nothing to redraw.
		}
	}
}
