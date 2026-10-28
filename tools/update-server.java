import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Serves a folder over HTTP so the launcher's update check can be tested without publishing
 * anything. The updater fetches a manifest and an archive over HTTP, so a release folder on disk
 * needs a URL before it can be checked against:
 *
 * <pre>
 *   java tools/update-server.java dist-test 8791
 *   "dist/Shrd Launcher/Shrd Launcher.exe" --manifest http://127.0.0.1:8791/shrd-launcher-latest.json --check-update
 * </pre>
 *
 * Only GET and HEAD are answered, and only from inside the directory it was pointed at. It is a
 * test helper: it has no authentication and binds to the loopback address on purpose.
 */
public final class UpdateServer {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("usage: java tools/update-server.java <directory> [port]");
			System.exit(1);
		}

		Path root = Path.of(args[0]).toAbsolutePath().normalize();
		int port = args.length > 1 ? Integer.parseInt(args[1]) : 8791;

		if (!Files.isDirectory(root)) {
			System.err.println("not a directory: " + root);
			System.exit(1);
		}

		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		server.createContext("/", exchange -> serve(root, exchange));
		server.setExecutor(Executors.newFixedThreadPool(4));
		server.start();
		System.out.println("serving " + root + " on http://127.0.0.1:" + port + "/");
	}

	private static void serve(Path root, HttpExchange exchange) throws IOException {
		try {
			String request = exchange.getRequestURI().getPath().replaceFirst("^/", "");
			Path file = root.resolve(request).normalize();

			if (!file.startsWith(root) || !Files.isRegularFile(file)) {
				respond(exchange, 404, "not found".getBytes());
				return;
			}

			byte[] bytes = Files.readAllBytes(file);
			System.out.println(exchange.getRequestMethod() + " /" + request + " -> " + bytes.length + " bytes");
			respond(exchange, 200, bytes);
		} finally {
			exchange.close();
		}
	}

	private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
		exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);

		if (body.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}
	}
}
