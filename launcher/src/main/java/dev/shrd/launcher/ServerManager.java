package dev.shrd.launcher;

import com.google.gson.JsonObject;
import dev.shrd.launcher.launch.Http;
import dev.shrd.launcher.launch.JavaRuntime;
import dev.shrd.launcher.launch.MinecraftMeta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A Minecraft server the launcher runs on this machine, for free: the jar comes from Mojang's own
 * version manifest, so it is exactly the version the instance plays, and the folder lives under the
 * launcher's data directory so it can be backed up or moved.
 * <p>
 * This is the honest form of "free hosting": no account, no queue, no service that can shut it
 * down — but it runs while this computer and this launcher are on. A hosted free tier (Aternos and
 * friends) sleeps when empty and cannot be created from outside its own website, which no launcher
 * can drive.
 */
public final class ServerManager {

	public enum State {
		STOPPED, STARTING, RUNNING, STOPPING
	}

	public interface Listener {
		void line(String text);
	}

	/** Kept short so a server log cannot grow without bound in memory. */
	private static final int LOG_LIMIT = 600;
	public static final int DEFAULT_PORT = 25565;

	private final List<String> log = Collections.synchronizedList(new ArrayList<>());
	private volatile State state = State.STOPPED;
	private final PortMapper mapper = new PortMapper();
	private volatile PortMapper.Result mapping = new PortMapper.Result(false, "", "");
	private Process process;
	private Path directory;
	private Listener listener = text -> {
	};
	private String version = "";

	public Path directory(Instance instance) {
		return ShrdPaths.servers().resolve(instance.name.replaceAll("[^A-Za-z0-9 _-]", "_").trim());
	}

	public State state() {
		return state;
	}

	public boolean isRunning() {
		return state == State.RUNNING || state == State.STARTING;
	}

	public String version() {
		return version;
	}

	/** Whether the router accepted a port mapping, and what it said when it did not. */
	public PortMapper.Result mapping() {
		return mapping;
	}

	/** The address a friend types to join from anywhere; empty when the port is not open. */
	public String publicAddress() {
		return mapping.mapped() ? mapping.publicAddress() : "";
	}

	public void setListener(Listener listener) {
		this.listener = listener == null ? text -> {
		} : listener;
	}

	public List<String> log() {
		synchronized (log) {
			return new ArrayList<>(log);
		}
	}

	// ------------------------------------------------------------------ files

	/**
	 * Downloads the server jar if it is missing, writes {@code eula.txt} and a
	 * {@code server.properties} that matches the account type, and returns the folder.
	 * <p>
	 * Writing {@code eula=true} is how the server is allowed to start; the page states that plainly
	 * next to the button rather than hiding it.
	 */
	public Path prepare(Instance instance, boolean onlineMode, Listener progress) throws IOException {
		Path folder = directory(instance);
		Files.createDirectories(folder);
		directory = folder;
		version = instance.minecraftVersion;

		JsonObject json = MinecraftMeta.versionJson(instance.minecraftVersion);
		JsonObject downloads = MinecraftMeta.object(json, "downloads");
		JsonObject server = MinecraftMeta.object(downloads, "server");

		if (server == null) {
			throw new IOException("Minecraft " + instance.minecraftVersion + " has no dedicated server download");
		}

		String url = MinecraftMeta.string(server, "url", "");
		String sha1 = MinecraftMeta.string(server, "sha1", "");
		Path jar = folder.resolve("server.jar");

		if (!Files.isRegularFile(jar) || !sha1.equalsIgnoreCase(sha1Of(jar))) {
			progress.line("Downloading the " + instance.minecraftVersion + " server jar…");
			Http.download(url, jar);

			if (!sha1.isEmpty() && !sha1.equalsIgnoreCase(sha1Of(jar))) {
				Files.deleteIfExists(jar);
				throw new IOException("The downloaded server jar did not match Mojang's SHA-1");
			}
		}

		Path eula = folder.resolve("eula.txt");

		if (!Files.isRegularFile(eula)) {
			Files.writeString(eula, "# By starting the server from Shrd Launcher you accept Minecraft's EULA.\n"
				+ "# https://aka.ms/MinecraftEULA\n"
				+ "eula=true\n", StandardCharsets.UTF_8);
		}

		Path properties = folder.resolve("server.properties");

		if (!Files.isRegularFile(properties)) {
			Files.writeString(properties, """
				# Written by Shrd Launcher. Edit anything here and restart the server.
				server-port=25565
				motd=Shrd Client
				max-players=10
				difficulty=normal
				gamemode=survival
				pvp=true
				level-name=world
				online-mode=true
				""", StandardCharsets.UTF_8);
		}

		setProperty(properties, "online-mode", onlineMode ? "true" : "false");
		return folder;
	}

	/** Rewrites one key in server.properties, leaving every other line alone. */
	private void setProperty(Path file, String key, String value) throws IOException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		boolean replaced = false;

		for (int index = 0; index < lines.size(); index++) {
			if (lines.get(index).startsWith(key + "=")) {
				lines.set(index, key + "=" + value);
				replaced = true;
			}
		}

		if (!replaced) {
			lines.add(key + "=" + value);
		}

		Files.write(file, lines, StandardCharsets.UTF_8);
	}

	private static String sha1Of(Path file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");

			try (var stream = Files.newInputStream(file)) {
				byte[] buffer = new byte[65536];
				int read;

				while ((read = stream.read(buffer)) > 0) {
					digest.update(buffer, 0, read);
				}
			}

			StringBuilder hex = new StringBuilder();

			for (byte value : digest.digest()) {
				hex.append(String.format("%02x", value));
			}

			return hex.toString();
		} catch (Exception e) {
			return "";
		}
	}

	// ------------------------------------------------------------------ running

	public synchronized void start(Instance instance, boolean onlineMode, String javaPath) throws IOException {
		if (isRunning()) {
			return;
		}

		Path folder = prepare(instance, onlineMode, new Listener() {
			@Override
			public void line(String text) {
				append(text);
			}
		});

		Path java = javaPath != null && !javaPath.isBlank() ? Path.of(javaPath) : JavaRuntime.detect("");
		String executable = java == null ? JavaRuntime.executableName() : java.toAbsolutePath().toString();

		List<String> command = new ArrayList<>();
		command.add(executable);
		command.add("-Xms512M");
		command.add("-Xmx" + Math.max(1024, instance.memoryMb) + "M");
		command.add("-jar");
		command.add("server.jar");
		command.add("nogui");

		// Open the port before the server binds it, so the address is ready when it comes up.
		append("Asking the router to open port " + port() + " for friends…");
		mapping = mapper.open(port());
		append(mapping.mapped()
			? "Open: friends can join at " + mapping.publicAddress()
			: mapping.message());

		append("Starting " + executable + " in " + ShrdPaths.pretty(folder));
		ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile());
		builder.redirectErrorStream(true);
		process = builder.start();
		state = State.STARTING;

		Thread reader = new Thread(() -> {
			try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;

				while ((line = lines.readLine()) != null) {
					append(line);

					if (line.contains("Done (") && line.contains("For help")) {
						state = State.RUNNING;
						append("Server is up at " + address());
					}
				}
			} catch (IOException ignored) {
				// The stream closes when the process stops; that is the normal end of this loop.
			} finally {
				if (state != State.STOPPED) {
					state = State.STOPPED;
					append("Server stopped");
				}
			}
		}, "shrd-server-log");

		reader.setDaemon(true);
		reader.start();
	}

	/** Asks the server to save and shut down the way a console would, then waits for it. */
	public synchronized void stop(long waitMillis) {
		if (process == null || !process.isAlive()) {
			state = State.STOPPED;
			return;
		}

		state = State.STOPPING;
		append("Stopping the server…");
		mapper.close();
		mapping = new PortMapper.Result(false, "", "");

		try (Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
			stdin.write("stop\n");
			stdin.flush();
		} catch (IOException e) {
			append("Could not ask the server to stop: " + e.getMessage());
		}

		try {
			if (!process.waitFor(waitMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				process.destroy();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroy();
		}

		state = State.STOPPED;
	}

	private void append(String text) {
		synchronized (log) {
			log.add(text);

			while (log.size() > LOG_LIMIT) {
				log.remove(0);
			}
		}

		listener.line(text);
	}

	public int port() {
		if (directory == null) {
			return DEFAULT_PORT;
		}

		try {
			for (String line : Files.readAllLines(directory.resolve("server.properties"), StandardCharsets.UTF_8)) {
				if (line.startsWith("server-port=")) {
					return Integer.parseInt(line.substring("server-port=".length()).trim());
				}
			}
		} catch (Exception ignored) {
			// Falls back to the default.
		}

		return DEFAULT_PORT;
	}

	/** What a player types to join from this machine. */
	public String address() {
		return "localhost:" + port();
	}

	/** What a player on the same network types. */
	public String lanAddress() {
		return localIp() + ":" + port();
	}

	public static String localIp() {
		try {
			for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
					continue;
				}

				for (InetAddress address : Collections.list(network.getInetAddresses())) {
					if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
						return address.getHostAddress();
					}
				}
			}
		} catch (Exception ignored) {
			// Reported as unknown below.
		}

		return "your-local-ip";
	}

	public boolean onlineMode() {
		if (directory == null) {
			return true;
		}

		try {
			return !Files.readAllLines(directory.resolve("server.properties"), StandardCharsets.UTF_8)
				.stream()
				.filter(line -> line.startsWith("online-mode="))
				.findFirst()
				.map(line -> line.toLowerCase(Locale.ROOT).endsWith("true"))
				.orElse(true);
		} catch (Exception e) {
			return true;
		}
	}
}
