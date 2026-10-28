package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unzips older versions' native libraries (the ones declared with a {@code natives} map) into the
 * directory the JVM is pointed at with {@code -Djava.library.path}. Minecraft 26.2 does not need
 * this — it loads its natives straight from the classpath — but the same launcher also runs older
 * versions, so the path is kept working.
 */
public final class Natives {

	private Natives() {
	}

	public static int extractAll(LaunchPlan plan) {
		int extracted = 0;

		for (LaunchPlan.Extract extract : plan.extractions) {
			extracted += extract(plan.nativesDir, extract);
		}

		if (extracted > 0) {
			Log.info("extracted %d native file(s) into %s", extracted, ShrdPaths.pretty(plan.nativesDir));
		}

		return extracted;
	}

	private static int extract(Path nativesDir, LaunchPlan.Extract extract) {
		Path jar = extract.download().target();
		int written = 0;

		try {
			Files.createDirectories(nativesDir);

			try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
				ZipEntry entry;

				while ((entry = zip.getNextEntry()) != null) {
					String name = entry.getName();

					if (entry.isDirectory() || name.startsWith("META-INF/") || isExcluded(name, extract.excludes())) {
						continue;
					}

					Path target = nativesDir.resolve(name);

					// A zip entry can name a path outside the natives folder; keep it inside.
					if (!target.normalize().startsWith(nativesDir.normalize())) {
						continue;
					}

					Files.createDirectories(target.getParent());

					try (OutputStream out = Files.newOutputStream(target)) {
						zip.transferTo(out);
					}

					written++;
				}
			}
		} catch (Exception e) {
			Log.warn("could not extract %s: %s", jar.getFileName(), e.getMessage());
		}

		return written;
	}

	private static boolean isExcluded(String name, List<String> excludes) {
		String fileName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;

		for (String exclude : excludes) {
			String pattern = exclude.endsWith("/") ? exclude.substring(0, exclude.length() - 1) : exclude;

			if (fileName.startsWith(pattern)) {
				return true;
			}
		}

		return false;
	}
}
