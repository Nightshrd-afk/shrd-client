package dev.shrd.client.util;

public final class TimeUtils {

	private TimeUtils() {
	}

	/** {@code 1h 05m 12s} — used by session timers. */
	public static String formatDuration(long milliseconds) {
		long totalSeconds = milliseconds / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;

		if (hours > 0) {
			return String.format(java.util.Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
		}

		if (minutes > 0) {
			return String.format(java.util.Locale.ROOT, "%dm %02ds", minutes, seconds);
		}

		return seconds + "s";
	}

	/** Current local time as {@code HH:mm:ss}. */
	public static String formatClock(java.time.LocalTime time, boolean seconds) {
		return seconds
			? String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond())
			: String.format(java.util.Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
	}
}
