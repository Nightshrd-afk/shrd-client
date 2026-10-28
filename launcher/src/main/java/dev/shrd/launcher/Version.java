package dev.shrd.launcher;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The running launcher's version, and the comparison the updater is built on. */
public final class Version {

	/** Filled in by Gradle from {@code project.version}; see src/main/resources/version.txt. */
	public static final String CURRENT = read();

	private Version() {
	}

	private static String read() {
		try (InputStream stream = Version.class.getResourceAsStream("/version.txt")) {
			if (stream != null) {
				String value = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();

				if (!value.isBlank() && !value.startsWith("$")) {
					return value;
				}
			}
		} catch (Exception ignored) {
			// Falls back to the development marker below.
		}

		return "0.0.0-dev";
	}

	/** {@code -1}, {@code 0} or {@code 1} for {@code a} against {@code b}, comparing number by number. */
	public static int compare(String a, String b) {
		int[] left = parts(a);
		int[] right = parts(b);
		int length = Math.max(left.length, right.length);

		for (int index = 0; index < length; index++) {
			int l = index < left.length ? left[index] : 0;
			int r = index < right.length ? right[index] : 0;

			if (l != r) {
				return l < r ? -1 : 1;
			}
		}

		return 0;
	}

	public static boolean isNewer(String candidate, String current) {
		return candidate != null && !candidate.isBlank() && compare(candidate, current) > 0;
	}

	private static int[] parts(String version) {
		if (version == null) {
			return new int[0];
		}

		// Only the leading dotted number matters: 1.2.3-beta sorts as 1.2.3.
		String cleaned = version.trim().replaceFirst("[^0-9.].*$", "");
		String[] pieces = cleaned.split("\\.");
		int[] numbers = new int[pieces.length];

		for (int index = 0; index < pieces.length; index++) {
			try {
				numbers[index] = Integer.parseInt(pieces[index]);
			} catch (NumberFormatException e) {
				numbers[index] = 0;
			}
		}

		return numbers;
	}
}
