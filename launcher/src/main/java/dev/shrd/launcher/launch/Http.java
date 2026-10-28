package dev.shrd.launcher.launch;

import dev.shrd.launcher.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Every outbound request the launcher makes goes through here. */
public final class Http {

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	private Http() {
	}

	public static String getString(String url) throws IOException {
		return new String(getBytes(url), StandardCharsets.UTF_8);
	}

	public static byte[] getBytes(String url) throws IOException {
		IOException last = null;

		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofMinutes(2))
					.header("User-Agent", "shrd-launcher/1.0.0")
					.GET()
					.build();
				HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

				if (response.statusCode() / 100 != 2) {
					throw new IOException("HTTP " + response.statusCode() + " from " + url);
				}

				return response.body();
			} catch (IOException e) {
				last = e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted while fetching " + url, e);
			}

			if (attempt < 3) {
				Log.warn("retrying %s (attempt %d failed: %s)", shorten(url), attempt, describe(last));
				sleep(400L * attempt);
			}
		}

		throw last != null ? last : new IOException("could not fetch " + url);
	}

	/**
	 * Downloads to {@code target} through a temporary file, so an interrupted download can never
	 * leave a half-written jar that later looks present. Returns the number of bytes written.
	 */
	public static long download(String url, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Path temporary = target.resolveSibling(target.getFileName() + ".part");
		IOException last = null;

		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofMinutes(10))
					.header("User-Agent", "shrd-launcher/1.0.0")
					.GET()
					.build();
				HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temporary));

				if (response.statusCode() / 100 != 2) {
					throw new IOException("HTTP " + response.statusCode() + " from " + url);
				}

				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				return Files.size(target);
			} catch (IOException e) {
				last = e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted while downloading " + url, e);
			}

			if (attempt < 3) {
				sleep(500L * attempt);
			}
		}

		try {
			Files.deleteIfExists(temporary);
		} catch (IOException ignored) {
			// Leftover .part files are harmless; the next attempt overwrites them.
		}

		throw last != null ? last : new IOException("could not download " + url);
	}

	/**
	 * A response kept whole, status included, for the endpoints where the status is the answer:
	 * Microsoft's device-code polling returns 400 with {@code authorization_pending} while the user
	 * is still typing the code, and that is not an error.
	 */
	public record Reply(int status, String body) {
		public boolean ok() {
			return status / 100 == 2;
		}
	}

	/** A GET with headers, for the endpoints that need a bearer token. */
	public static Reply get(String url, java.util.Map<String, String> headers) throws IOException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofMinutes(1))
			.header("User-Agent", "shrd-launcher/1.0.0")
			.header("Accept", "application/json")
			.GET();

		if (headers != null) {
			for (java.util.Map.Entry<String, String> header : headers.entrySet()) {
				builder.header(header.getKey(), header.getValue());
			}
		}

		try {
			HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new Reply(response.statusCode(), response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted while fetching " + url, e);
		}
	}

	public static Reply postForm(String url, java.util.Map<String, String> fields, java.util.Map<String, String> headers) throws IOException {
		StringBuilder form = new StringBuilder();

		for (java.util.Map.Entry<String, String> field : fields.entrySet()) {
			if (form.length() > 0) {
				form.append('&');
			}

			form.append(java.net.URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8))
				.append('=')
				.append(java.net.URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
		}

		return post(url, form.toString(), "application/x-www-form-urlencoded", headers);
	}

	public static Reply postJson(String url, String json, java.util.Map<String, String> headers) throws IOException {
		return post(url, json, "application/json", headers);
	}

	/** SOAP, for the router's port-mapping service: it wants both the action header and XML. */
	public static Reply postXml(String url, String xml, String soapAction) throws IOException {
		return post(url, xml, "text/xml; charset=\"utf-8\"",
			java.util.Map.of("SOAPAction", "\"" + soapAction + "\""));
	}

	private static Reply post(String url, String body, String contentType, java.util.Map<String, String> headers) throws IOException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofMinutes(1))
			.header("User-Agent", "shrd-launcher/1.0.0")
			.header("Content-Type", contentType)
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

		if (headers != null) {
			for (java.util.Map.Entry<String, String> header : headers.entrySet()) {
				builder.header(header.getKey(), header.getValue());
			}
		}

		try {
			HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new Reply(response.statusCode(), response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted while posting to " + url, e);
		}
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static String shorten(String url) {
		return url.length() <= 90 ? url : url.substring(0, 87) + "...";
	}

	/** A refused connection usually has no message, only a type, and "null" tells nobody anything. */
	public static String describe(Exception e) {
		if (e == null) {
			return "unknown error";
		}

		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}
}
