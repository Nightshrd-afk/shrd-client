package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;
import dev.shrd.launcher.ShrdPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Starts Minecraft and keeps its console output flowing into the launcher's console page.
 * <p>
 * Modern Minecraft logs through log4j into {@code <game dir>/logs/latest.log} and only sometimes
 * mirrors that to stdout, so both are read — but exactly one of them is forwarded, otherwise every
 * line arrives twice. The log file is preferred; stdout is buffered for a short grace period and
 * only used if the game never writes a log file (which is what old versions did).
 */
public final class GameLauncher {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	/** How long stdout is held back while the game's own log file is given a chance to appear. */
	private static final long STDOUT_GRACE_MS = 5000L;
	private static final int PENDING_LIMIT = 2000;

	/**
	 * A running game. {@code process} owns it; the log file is kept so a user can go back and read
	 * or share what the game said.
	 */
	public record Session(Process process, Path logFile, Path gameDir) {
		public boolean isAlive() {
			return process.isAlive();
		}

		public long pid() {
			return process.pid();
		}

		public void stop() {
			if (process.isAlive()) {
				process.destroy();
			}
		}

		public int waitFor() throws InterruptedException {
			return process.waitFor();
		}
	}

	private GameLauncher() {
	}

	public static Session start(LaunchContext context, LaunchPlan plan, Consumer<String> sink) throws IOException {
		List<String> command = plan.command(context.java);
		Files.createDirectories(ShrdPaths.logs());
		Path logFile = ShrdPaths.logs().resolve(safeName(context.instance.name) + "-" + LocalDateTime.now().format(STAMP) + ".log");
		Files.writeString(logFile, "$ " + String.join(" ", command) + System.lineSeparator() + System.lineSeparator(),
			StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		Files.createDirectories(context.gameDir());
		Files.createDirectories(context.modsDir());

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(context.gameDir().toFile());
		builder.redirectErrorStream(true);

		Log.title("Launching %s as %s", plan.versionId, context.account.name);
		Process process = builder.start();

		Console console = new Console(process, context.gameDir(), logFile, sink);
		console.start();
		return new Session(process, logFile, context.gameDir());
	}

	private static String safeName(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	/** Owns the two reader threads and decides which of them is allowed to speak. */
	private static final class Console {
		private final Process process;
		private final Path logFile;
		private final Consumer<String> sink;
		private final Path latestLog;
		private final long startedAt = System.currentTimeMillis();
		private final Deque<String> pending = new ArrayDeque<>();
		private boolean logFileOwnsOutput;
		private boolean stdoutStreaming;

		private Console(Process process, Path gameDir, Path logFile, Consumer<String> sink) {
			this.process = process;
			this.logFile = logFile;
			this.sink = sink;
			this.latestLog = gameDir.resolve("logs").resolve("latest.log");
		}

		private void start() {
			Thread tail = new Thread(this::tail, "shrd-game-logfile");
			tail.setDaemon(true);
			tail.start();

			Thread pump = new Thread(this::pump, "shrd-game-console");
			pump.setDaemon(true);
			pump.start();

			Thread grace = new Thread(this::grace, "shrd-game-grace");
			grace.setDaemon(true);
			grace.start();
		}

		private synchronized void fromLogFile(String line) {
			if (!logFileOwnsOutput) {
				logFileOwnsOutput = true;
				// stdout was only ever a fallback; its backlog is redundant now.
				pending.clear();
			}

			deliver(line);
		}

		private synchronized void fromStdout(String line) {
			if (logFileOwnsOutput) {
				return;
			}

			if (stdoutStreaming) {
				deliver(line);
				return;
			}

			if (pending.size() < PENDING_LIMIT) {
				pending.add(line);
			}
		}

		private void deliver(String line) {
			try {
				Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				Log.warn("could not write the game log: %s", e.getMessage());
			}

			if (sink != null) {
				sink.accept(line);
			}
		}

		/** If no log file ever appears, stdout becomes the console. */
		private void grace() {
			long elapsed = 0;

			while (elapsed < STDOUT_GRACE_MS && process.isAlive()) {
				Http.sleep(250);
				elapsed += 250;

				synchronized (this) {
					if (logFileOwnsOutput) {
						return;
					}
				}
			}

			synchronized (this) {
				if (logFileOwnsOutput || stdoutStreaming) {
					return;
				}

				stdoutStreaming = true;

				for (String line : pending) {
					deliver(line);
				}

				pending.clear();
			}
		}

		/**
		 * Follows the game's own log. Whatever was already in the file belongs to a previous
		 * session, so the reader starts at its current end and only reports what this launch
		 * writes — including the truncation Minecraft performs when it starts logging.
		 */
		private void tail() {
			StringBuilder partial = new StringBuilder();
			long offset = size(latestLog);

			while (process.isAlive()) {
				try {
					if (Files.isRegularFile(latestLog)) {
						long size = Files.size(latestLog);

						if (size < offset) {
							offset = 0;
							partial.setLength(0);
						}

						if (size > offset) {
							int length = (int) Math.min(size - offset, 1 << 20);
							byte[] bytes = new byte[length];

							try (RandomAccessFile file = new RandomAccessFile(latestLog.toFile(), "r")) {
								file.seek(offset);
								file.readFully(bytes);
							}

							offset += length;
							partial.append(new String(bytes, StandardCharsets.UTF_8));
							int newline;

							while ((newline = partial.indexOf("\n")) >= 0) {
								String line = partial.substring(0, newline).stripTrailing();
								partial.delete(0, newline + 1);

								if (!line.isEmpty() && System.currentTimeMillis() >= startedAt - 2000) {
									fromLogFile(line);
								}
							}
						}
					}
				} catch (IOException e) {
					// Mid-rotation reads are expected; the next pass picks the file up again.
				}

				Http.sleep(250);
			}
		}

		private void pump() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;

				while ((line = reader.readLine()) != null) {
					fromStdout(line);
				}
			} catch (IOException e) {
				Log.warn("game console stream ended: %s", e.getMessage());
			}

			int exit = -1;

			try {
				exit = process.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			if (exit == 0) {
				Log.title("Minecraft exited normally (log: %s)", ShrdPaths.pretty(logFile));
			} else {
				Log.warn("Minecraft exited with code %d (log: %s)", exit, ShrdPaths.pretty(logFile));
			}
		}

		private long size(Path file) {
			try {
				return Files.isRegularFile(file) ? Files.size(file) : 0L;
			} catch (IOException e) {
				return 0L;
			}
		}
	}
}
