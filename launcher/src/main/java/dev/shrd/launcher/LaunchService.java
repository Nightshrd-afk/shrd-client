package dev.shrd.launcher;

import dev.shrd.launcher.launch.Assets;
import dev.shrd.launcher.launch.Downloader;
import dev.shrd.launcher.launch.GameLauncher;
import dev.shrd.launcher.launch.JavaRuntime;
import dev.shrd.launcher.launch.LaunchContext;
import dev.shrd.launcher.launch.LaunchPlan;
import dev.shrd.launcher.launch.Natives;
import dev.shrd.launcher.launch.Resolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The one path from "Play was pressed" to "the game is running": resolve the version, make sure
 * every file is on disk, then start the process. The CLI and the Play button both go through here,
 * so what the launcher's own test exercises is exactly what a user runs.
 */
public final class LaunchService {

	public interface Listener {
		/** {@code resolving}, {@code downloading}, {@code starting}, {@code running}, {@code idle}. */
		void stage(String stage, String detail);

		void progress(int done, int total, long bytesDone, long bytesTotal);

		default void note(String message) {
		}

		default void done(boolean success, String message) {
		}
	}

	private volatile GameLauncher.Session session;
	private volatile boolean preparing;

	public boolean isPreparing() {
		return preparing;
	}

	public boolean isRunning() {
		GameLauncher.Session current = session;
		return current != null && current.isAlive();
	}

	public GameLauncher.Session session() {
		return session;
	}

	/** Blocks until the game has started (or the attempt failed). Call it from a worker thread. */
	public GameLauncher.Session play(LauncherConfig config, Instance instance, Account account, Listener listener) {
		Path java = JavaRuntime.detect(config.javaPath);

		if (java == null) {
			Log.error("no Java found — set one on the Settings page");
			listener.done(false, "No Java found. Set a Java path in Settings.");
			return null;
		}

		String javaVersion = JavaRuntime.version(java);
		Log.info("using Java %s (%s)", javaVersion == null ? "?" : javaVersion, ShrdPaths.pretty(java));
		preparing = true;

		try {
			LaunchContext context = new LaunchContext(instance, account, config, java);
			listener.stage("resolving", "Reading version metadata");

			LaunchPlan plan = Resolver.resolve(context);
			Log.title("Resolved %s", plan.summary());

			int required = plan.requiredJavaMajor;
			int actual = JavaRuntime.major(javaVersion);

			if (required > 0 && actual > 0 && actual < required) {
				Log.warn("Minecraft %s wants Java %d, this launcher found Java %d", instance.minecraftVersion, required, actual);
			}

			listener.stage("downloading", "Checking libraries");
			Downloader.Progress progress = new Downloader.Progress() {
				@Override
				public void update(String label, int done, int total, long bytesDone, long bytesTotal) {
					listener.progress(done, total, bytesDone, bytesTotal);
				}

				@Override
				public void note(String message) {
					Log.info("%s", message);
					listener.note(message);
				}
			};

			Downloader.Result libraries = Downloader.fetch(plan.allDownloads(), progress);

			if (!libraries.failures().isEmpty()) {
				String message = libraries.failures().size() + " file(s) could not be downloaded, first: " + libraries.failures().get(0);
				Log.error("%s", message);
				listener.done(false, message);
				return null;
			}

			if (plan.assetIndex != null) {
				listener.stage("downloading", "Checking assets");
				Downloader.fetch(List.of(plan.assetIndex), progress);
				List<LaunchPlan.Download> objects = Assets.objects(plan);
				Downloader.Result assets = Downloader.fetch(objects, progress);
				Log.info("assets: %d files (%d already cached)", objects.size(), assets.cached());
			}

			if (!plan.extractions.isEmpty()) {
				Natives.extractAll(plan);
			}

			Files.createDirectories(context.gameDir());
			Files.createDirectories(context.modsDir());
			listener.stage("starting", "Starting Minecraft");
			session = GameLauncher.start(context, plan, config.streamGameLog ? Log::game : null);

			if (session.isAlive()) {
				instance.lastPlayed = System.currentTimeMillis();
				config.save();
				listener.stage("running", "Running, pid " + session.pid());
				listener.done(true, "Minecraft is running");
			} else {
				listener.stage("idle", "The game did not start");
				listener.done(false, "The game exited immediately — check the Console page");
			}

			return session;
		} catch (Exception e) {
			Log.error("launch failed: %s", e);
			listener.done(false, "Launch failed: " + e.getMessage());
			return null;
		} finally {
			preparing = false;
		}
	}


	public void stop() {
		GameLauncher.Session current = session;

		if (current != null && current.isAlive()) {
			Log.title("Stopping Minecraft (pid %d)", current.pid());
			current.stop();
		}

		session = null;
	}
}
