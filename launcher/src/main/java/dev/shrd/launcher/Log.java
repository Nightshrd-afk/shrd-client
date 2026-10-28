package dev.shrd.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The launcher's own log. Every line goes to the console panel, to stdout and to
 * {@code logs/launcher.log}, so a bug report can be a file rather than a screenshot.
 */
public final class Log {

	public enum Level {
		INFO, WARN, ERROR, GAME, TITLE
	}

	public record Line(Level level, String text, String time) {
	}

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final List<Line> HISTORY = new ArrayList<>();
	private static final List<Consumer<Line>> LISTENERS = new ArrayList<>();
	private static final int HISTORY_LIMIT = 4000;

	private Log() {
	}

	public static void addListener(Consumer<Line> listener) {
		List<Line> replay;

		synchronized (HISTORY) {
			LISTENERS.add(listener);
			replay = new ArrayList<>(HISTORY);
		}

		for (Line line : replay) {
			listener.accept(line);
		}
	}

	public static List<Line> history() {
		synchronized (HISTORY) {
			return new ArrayList<>(HISTORY);
		}
	}

	public static void clear() {
		synchronized (HISTORY) {
			HISTORY.clear();
		}
	}

	public static void info(String message, Object... args) {
		write(Level.INFO, format(message, args));
	}

	public static void warn(String message, Object... args) {
		write(Level.WARN, format(message, args));
	}

	public static void error(String message, Object... args) {
		write(Level.ERROR, format(message, args));
	}

	public static void game(String message) {
		write(Level.GAME, message);
	}

	/** A highlighted line, used to mark a launch starting or finishing. */
	public static void title(String message, Object... args) {
		write(Level.TITLE, format(message, args));
	}

	private static String format(String message, Object... args) {
		return args.length == 0 ? message : String.format(message, args);
	}

	private static void write(Level level, String text) {
		Line line = new Line(level, text, LocalTime.now().format(CLOCK));

		synchronized (HISTORY) {
			HISTORY.add(line);

			if (HISTORY.size() > HISTORY_LIMIT) {
				HISTORY.remove(0);
			}

			for (Consumer<Line> listener : LISTENERS) {
				listener.accept(line);
			}
		}

		if (level != Level.GAME) {
			System.out.println("[" + line.time() + "] " + level + " " + text);
		}

		appendToFile(line);
	}

	private static void appendToFile(Line line) {
		try {
			Path file = ShrdPaths.logs().resolve("launcher.log");
			Files.createDirectories(file.getParent());
			Files.writeString(file, line.time() + " " + line.level() + " " + line.text() + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// Logging must never be the thing that breaks a launch.
		}
	}
}
