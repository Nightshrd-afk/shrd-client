package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fetches everything a launch needs, in parallel, and skips anything already on disk whose SHA-1
 * matches — which is what makes the second launch fast. Files are verified after download; a jar
 * that arrives corrupted is deleted rather than left to fail later inside the game.
 */
public final class Downloader {

	private static final int THREADS = 8;

	public interface Progress {
		void update(String label, int done, int total, long bytesDone, long bytesTotal);

		void note(String message);
	}

	public record Result(int cached, int downloaded, long bytes, List<String> failures) {
		public int total() {
			return cached + downloaded;
		}
	}

	private Downloader() {
	}

	public static List<LaunchPlan.Download> missing(List<LaunchPlan.Download> downloads) {
		List<LaunchPlan.Download> missing = new ArrayList<>();

		for (LaunchPlan.Download download : downloads) {
			if (!isPresent(download)) {
				missing.add(download);
			}
		}

		return missing;
	}

	public static boolean isPresent(LaunchPlan.Download download) {
		Path target = download.target();

		if (!Files.isRegularFile(target)) {
			return false;
		}

		try {
			if (download.sha1() == null || download.sha1().isBlank()) {
				// No hash to check against: size is the only thing left to compare.
				return download.size() <= 0 || Files.size(target) == download.size();
			}

			return download.sha1().equalsIgnoreCase(sha1(target));
		} catch (IOException e) {
			return false;
		}
	}

	public static Result fetch(List<LaunchPlan.Download> downloads, Progress progress) {
		List<LaunchPlan.Download> needed = missing(downloads);
		int cached = downloads.size() - needed.size();
		long total = 0;

		for (LaunchPlan.Download download : needed) {
			total += Math.max(0, download.size());
		}

		final long bytesTotal = total;

		if (progress != null) {
			progress.note(needed.isEmpty()
				? "all " + downloads.size() + " files already cached"
				: needed.size() + " to download, " + cached + " already cached (" + Assets.megabytes(bytesTotal) + " MB)");
		}

		if (needed.isEmpty()) {
			return new Result(cached, 0, 0, List.of());
		}

		AtomicInteger done = new AtomicInteger();
		AtomicLong bytesDone = new AtomicLong();
		ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
		ExecutorService pool = Executors.newFixedThreadPool(THREADS, runnable -> {
			Thread thread = new Thread(runnable, "shrd-download");
			thread.setDaemon(true);
			return thread;
		});

		for (LaunchPlan.Download download : needed) {
			pool.submit(() -> {
				try {
					long bytes = Http.download(download.url(), download.target());

					if (download.sha1() != null && !download.sha1().isBlank() && !download.sha1().equalsIgnoreCase(sha1(download.target()))) {
						Files.deleteIfExists(download.target());
						throw new IOException("SHA-1 mismatch on " + download.label());
					}

					bytesDone.addAndGet(Math.max(bytes, 0));
				} catch (IOException e) {
					failures.add(download.label() + ": " + e.getMessage());
					Log.error("failed to download %s — %s", download.label(), e.getMessage());
				}

				int completed = done.incrementAndGet();

				if (progress != null) {
					progress.update(download.label(), completed, needed.size(), bytesDone.get(), bytesTotal);
				}
			});
		}

		pool.shutdown();

		try {
			if (!pool.awaitTermination(60, TimeUnit.MINUTES)) {
				pool.shutdownNow();
				failures.add("downloads timed out");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			pool.shutdownNow();
			failures.add("downloads interrupted");
		}

		Result result = new Result(cached, done.get() - failures.size(), bytesDone.get(), new ArrayList<>(failures));

		if (progress != null && !result.failures().isEmpty()) {
			progress.note(result.failures().size() + " download(s) failed");
		}

		return result;
	}

	public static String sha1(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");

			try (InputStream stream = Files.newInputStream(file); DigestInputStream digestStream = new DigestInputStream(stream, digest)) {
				byte[] buffer = new byte[16384];

				while (digestStream.read(buffer) != -1) {
					// Reading is what feeds the digest.
				}
			}

			StringBuilder builder = new StringBuilder();

			for (byte value : digest.digest()) {
				builder.append(String.format("%02x", value));
			}

			return builder.toString();
		} catch (Exception e) {
			throw new IOException("could not hash " + file, e);
		}
	}
}
