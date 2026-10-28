package dev.shrd.launcher;

import dev.shrd.launcher.launch.Assets;
import dev.shrd.launcher.launch.Downloader;
import dev.shrd.launcher.launch.GameLauncher;
import dev.shrd.launcher.launch.Http;
import dev.shrd.launcher.launch.JavaRuntime;
import dev.shrd.launcher.launch.LaunchContext;
import dev.shrd.launcher.launch.LaunchPlan;
import dev.shrd.launcher.launch.MinecraftMeta;
import dev.shrd.launcher.launch.Resolver;
import dev.shrd.launcher.update.UpdateManifest;
import dev.shrd.launcher.update.Updater;

import java.nio.file.Path;
import java.util.List;

/**
 * The launcher driven from a terminal. The GUI uses exactly the same {@link LaunchService}, so
 * {@code --launch} is not a separate implementation to keep in step: it is the same launch, with
 * the status updates printed as lines a script can check.
 */
public final class Cli {

	private Cli() {
	}

	public static int versions() {
		try {
			System.out.println("latest release: " + MinecraftMeta.latestRelease());

			for (MinecraftMeta.VersionEntry entry : MinecraftMeta.releases()) {
				System.out.println(entry.id() + "  " + entry.type());
			}

			return 0;
		} catch (Exception e) {
			System.err.println("could not read Mojang's version list: " + e.getMessage());
			return 1;
		}
	}

	/** Compare this build against the published manifest, without changing anything. */
	public static int checkUpdate(LauncherConfig config) {
		if (config.updateManifestUrl == null || config.updateManifestUrl.isBlank()) {
			System.err.println("no update manifest URL is set");
			return 1;
		}

		try {
			UpdateManifest manifest = Updater.check(config.updateManifestUrl, null);
			System.out.println("SHRD-UPDATE current=" + Version.CURRENT + " manifest=" + config.updateManifestUrl);

			if (manifest == null) {
				System.out.println("SHRD-UPDATE-UP-TO-DATE");
				return 0;
			}

			System.out.println("SHRD-UPDATE-AVAILABLE version=" + manifest.version + " url=" + Updater.downloadUrl(config.updateManifestUrl, manifest)
				+ " sha256=" + (manifest.sha256.isBlank() ? "none" : manifest.sha256) + " bytes=" + manifest.size);
			return 0;
		} catch (Exception e) {
			System.err.println("SHRD-UPDATE-FAILED " + Http.describe(e));
			return 1;
		}
	}

	/** Download an update, and with {@code apply} swap it in and restart the launcher. */
	public static int update(LauncherConfig config, boolean apply) {
		if (config.updateManifestUrl == null || config.updateManifestUrl.isBlank()) {
			System.err.println("no update manifest URL is set");
			return 1;
		}

		try {
			UpdateManifest manifest = Updater.check(config.updateManifestUrl, null);

			if (manifest == null) {
				System.out.println("SHRD-UPDATE-UP-TO-DATE current=" + Version.CURRENT);
				return 0;
			}

			Path root = Updater.prepare(config.updateManifestUrl, manifest, null);
			System.out.println("SHRD-UPDATE-PREPARED version=" + manifest.version + " path=" + root);

			if (!apply) {
				return 0;
			}

			Path install = Updater.installDir();

			if (install == null) {
				System.err.println("SHRD-UPDATE-FAILED this copy is not an installed build, so it cannot swap itself in");
				return 1;
			}

			Updater.apply(root, install);
			System.out.println("SHRD-UPDATE-APPLYING install=" + install);
			return 0;
		} catch (Exception e) {
			System.err.println("SHRD-UPDATE-FAILED " + Http.describe(e));
			return 1;
		}
	}

	public static int installMods(LauncherConfig config, String instanceName) {
		Instance instance = select(config, instanceName);
		System.out.println("instance: " + instance.name + " (" + ShrdPaths.pretty(instance.dir()) + ")");

		try {
			Path client = ModsManager.installClientBuild(instance);
			System.out.println("SHRD-MOD client=" + client.getFileName() + " bytes=" + java.nio.file.Files.size(client));
		} catch (Exception e) {
			System.err.println("SHRD-MOD-FAILED client: " + e.getMessage());
			return 1;
		}

		try {
			Path fabricApi = ModsManager.installFabricApi(instance);
			System.out.println("SHRD-MOD fabric-api=" + fabricApi.getFileName() + " bytes=" + java.nio.file.Files.size(fabricApi));
		} catch (Exception e) {
			System.err.println("SHRD-MOD-FAILED fabric-api: " + e.getMessage());
			return 1;
		}

		return 0;
	}

	/** Resolve and download everything a launch needs, without starting the game. */
	public static int prepare(LauncherConfig config, String instanceName) {
		Instance instance = select(config, instanceName);
		Path javaExe = JavaRuntime.detect(config.javaPath);

		if (javaExe == null) {
			System.err.println("no Java found");
			return 1;
		}

		try {
			LaunchContext context = new LaunchContext(instance, config.activeAccount(), config, javaExe);
			LaunchPlan plan = Resolver.resolve(context);
			System.out.println("SHRD-PLAN version=" + plan.versionId);
			System.out.println("SHRD-PLAN main=" + plan.mainClass);
			System.out.println("SHRD-PLAN libraries=" + plan.libraries.size() + " classpath=" + plan.classpath.size());
			System.out.println("SHRD-PLAN asset-index=" + plan.assetIndexId + " java=" + plan.requiredJavaMajor + "+ heap=" + plan.memoryMb + "MB");
			System.out.println("SHRD-PLAN game-args=" + String.join(" ", plan.gameArgs));
			System.out.println("SHRD-PLAN downloads=" + plan.allDownloads().size() + " bytes=" + plan.totalBytes());

			Downloader.Result libraries = Downloader.fetch(plan.allDownloads(), progress());
			System.out.println("SHRD-DOWNLOAD files=" + libraries.total() + " downloaded=" + libraries.downloaded()
				+ " cached=" + libraries.cached() + " bytes=" + libraries.bytes() + " failed=" + libraries.failures().size());

			if (!libraries.failures().isEmpty()) {
				libraries.failures().forEach(failure -> System.err.println("SHRD-DOWNLOAD-FAILED " + failure));
				return 1;
			}

			if (plan.assetIndex != null) {
				Downloader.fetch(List.of(plan.assetIndex), progress());
				List<LaunchPlan.Download> objects = Assets.objects(plan);
				Downloader.Result assets = Downloader.fetch(objects, progress());
				System.out.println("SHRD-DOWNLOAD assets=" + objects.size() + " downloaded=" + assets.downloaded()
					+ " cached=" + assets.cached() + " bytes=" + assets.bytes() + " failed=" + assets.failures().size());

				if (!assets.failures().isEmpty()) {
					assets.failures().forEach(failure -> System.err.println("SHRD-DOWNLOAD-FAILED " + failure));
					return 1;
				}
			}

			System.out.println("SHRD-PREPARE-OK");
			return 0;
		} catch (Exception e) {
			System.err.println("SHRD-PREPARE-FAILED " + e);
			return 1;
		}
	}

	/** Prepare, then start the game and report what happened. */
	public static int launch(LauncherConfig config, String instanceName, long waitSeconds) {
		Instance instance = select(config, instanceName);
		Path javaExe = JavaRuntime.detect(config.javaPath);

		if (javaExe == null) {
			System.err.println("no Java found");
			return 1;
		}

		try {
			LaunchContext context = new LaunchContext(instance, config.activeAccount(), config, javaExe);
			LaunchPlan plan = Resolver.resolve(context);
			System.out.println("SHRD-PLAN " + plan.summary());

			Downloader.Result libraries = Downloader.fetch(plan.allDownloads(), null);
			System.out.println("SHRD-DOWNLOAD files=" + libraries.total() + " cached=" + libraries.cached() + " failed=" + libraries.failures().size());

			if (!libraries.failures().isEmpty()) {
				libraries.failures().forEach(failure -> System.err.println("SHRD-DOWNLOAD-FAILED " + failure));
				return 1;
			}

			if (plan.assetIndex != null) {
				Downloader.fetch(List.of(plan.assetIndex), null);
				Downloader.Result assets = Downloader.fetch(Assets.objects(plan), null);
				System.out.println("SHRD-DOWNLOAD assets-cached=" + assets.cached() + " downloaded=" + assets.downloaded());
			}

			java.nio.file.Files.createDirectories(instance.modsDir());
			GameLauncher.Session session = GameLauncher.start(context, plan, Log::game);
			System.out.println("SHRD-LAUNCH started pid=" + session.pid() + " log=" + session.logFile().toAbsolutePath());

			if (waitSeconds <= 0) {
				return 0;
			}

			boolean exited = session.process().waitFor(waitSeconds, java.util.concurrent.TimeUnit.SECONDS);

			if (exited) {
				System.out.println("SHRD-LAUNCH exited code=" + session.process().exitValue());
				return session.process().exitValue() == 0 ? 0 : 1;
			}

			System.out.println("SHRD-LAUNCH still-running after " + waitSeconds + "s");
			// A scripted launch must not leave a game running on someone's desktop.
			session.stop();
			return 2;
		} catch (Exception e) {
			System.err.println("SHRD-LAUNCH-FAILED " + e);
			return 1;
		}
	}

	private static Instance select(LauncherConfig config, String instanceName) {
		if (instanceName != null && config.findInstance(instanceName) != null) {
			config.activeInstance = instanceName;
		}

		return config.active();
	}

	private static Downloader.Progress progress() {
		return new Downloader.Progress() {
			@Override
			public void update(String label, int done, int total, long bytesDone, long bytesTotal) {
				System.out.printf("SHRD-PROGRESS %d/%d %s / %s MB  %s%n", done, total,
					Assets.megabytes(bytesDone), Assets.megabytes(bytesTotal), label);
			}

			@Override
			public void note(String message) {
				System.out.println("SHRD-NOTE " + message);
			}
		};
	}
}
