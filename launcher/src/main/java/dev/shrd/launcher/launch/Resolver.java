package dev.shrd.launcher.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.shrd.launcher.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a version id (and, for modded instances, Fabric's launch profile) into a
 * {@link LaunchPlan}: which files to download, the classpath, and the exact argument list.
 * <p>
 * The rules implemented here are the ones Mojang's own launcher applies, and they were written
 * against the real 26.2 JSON rather than from memory:
 * <ul>
 *   <li>{@code inheritsFrom} — a Fabric profile only declares the differences, so the parent
 *       version's JSON is merged underneath it.</li>
 *   <li>{@code rules} — OS-dependent libraries and arguments. A rule list that matches nothing
 *       means "excluded", which is how macOS-only libraries stay off a Windows classpath.</li>
 *   <li>26.2 lists its natives as ordinary libraries with an OS rule
 *       ({@code org.lwjgl:lwjgl:3.4.1:natives-windows}) and points LWJGL at
 *       {@code ${natives_directory}/lwjgl}; older versions instead declare a {@code natives} map
 *       whose classifiers must be unzipped, and that path is handled too.</li>
 * </ul>
 */
public final class Resolver {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");
	private static final int MAX_INHERITANCE_DEPTH = 10;

	private Resolver() {
	}

	public static LaunchPlan resolve(LaunchContext context) throws IOException {
		List<JsonObject> chain = inheritanceChain(context);
		JsonObject child = chain.get(0);
		Map<String, Boolean> features = features(context);

		LaunchPlan plan = new LaunchPlan();
		plan.versionId = context.loaderId();
		plan.minecraftVersion = context.versionId();
		plan.memoryMb = context.memoryMb();
		plan.gameDir = context.gameDir();
		plan.assetsDir = context.assetsDir();
		plan.librariesDir = context.librariesDir();
		plan.nativesDir = context.nativesDir();

		if (chain.size() > 1) {
			Log.info("Fabric profile %s inherits from %s", child.get("id"), child.get("inheritsFrom").getAsString());
		}

		plan.mainClass = nearest(context, chain, "mainClass", "net.minecraft.client.main.Main");
		plan.requiredJavaMajor = MinecraftMeta.requiredJavaMajor(chain.get(chain.size() - 1));

		resolveLibraries(plan, chain, features);
		resolveClientJar(plan, context, chain);
		resolveAssets(plan, chain);
		resolveLogConfig(plan, chain);
		collectArguments(plan, context, chain, features);
		createExtractDirs(plan);
		return plan;
	}

	/**
	 * The requested version first, then each {@code inheritsFrom} parent.
	 * <p>
	 * For a modded instance the chain starts at Fabric's profile, which itself declares
	 * {@code inheritsFrom}. Starting from the vanilla JSON as well would put Minecraft 26.2 in the
	 * chain twice — which silently duplicated every game argument.
	 */
	private static List<JsonObject> inheritanceChain(LaunchContext context) throws IOException {
		List<JsonObject> chain = new ArrayList<>();
		JsonObject current;

		if (context.instance.isFabric()) {
			String loaderVersion = context.instance.loaderVersion;

			if (loaderVersion == null || loaderVersion.isBlank()) {
				List<String> available = MinecraftMeta.fabricLoaderVersions(context.versionId());

				if (available.isEmpty()) {
					throw new IOException("Fabric has no loader for Minecraft " + context.versionId());
				}

				loaderVersion = available.get(0);
				context.instance.loaderVersion = loaderVersion;
			}

			current = MinecraftMeta.loaderProfile(context.versionId(), loaderVersion, context.loaderId());
		} else {
			current = MinecraftMeta.versionJson(context.versionId());
		}

		chain.add(current);
		int depth = 0;

		while (current.has("inheritsFrom") && depth++ < MAX_INHERITANCE_DEPTH) {
			current = MinecraftMeta.versionJson(current.get("inheritsFrom").getAsString());
			chain.add(current);
		}

		return chain;
	}

	private static Map<String, Boolean> features(LaunchContext context) {
		Map<String, Boolean> features = new LinkedHashMap<>();
		features.put("is_demo_user", false);
		features.put("has_custom_resolution", context.customResolution());
		features.put("has_quick_plays_support", false);
		features.put("is_quick_play_singleplayer", false);
		features.put("is_quick_play_multiplayer", false);
		features.put("is_quick_play_realms", false);
		return features;
	}

	private static void resolveLibraries(LaunchPlan plan, List<JsonObject> chain, Map<String, Boolean> features) {
		// Parents first, then the child, so a profile can override a library by declaring it again.
		Map<String, JsonObject> merged = new LinkedHashMap<>();

		for (int i = chain.size() - 1; i >= 0; i--) {
			for (JsonElement element : MinecraftMeta.array(chain.get(i), "libraries")) {
				JsonObject library = element.getAsJsonObject();
				merged.put(libraryKey(library), library);
			}
		}

		for (JsonObject library : merged.values()) {
			if (!rulesAllow(library, features)) {
				continue;
			}

			JsonObject nativeSelection = MinecraftMeta.object(library, "natives");

			if (nativeSelection != null) {
				// Old-style natives: a classifier jar that has to be unzipped into the natives directory.
				String classifier = classifierFor(nativeSelection);

				if (classifier == null) {
					continue;
				}

				JsonObject classifiers = MinecraftMeta.object(MinecraftMeta.object(library, "downloads"), "classifiers");
				JsonObject artifact = classifiers == null ? null : MinecraftMeta.object(classifiers, classifier);

				if (artifact == null) {
					Log.warn("no %s artifact for %s", classifier, MinecraftMeta.string(library, "name", "?"));
					continue;
				}

				LaunchPlan.Download download = download(library, artifact, plan.librariesDir);
				List<String> excludes = new ArrayList<>();

				for (JsonElement exclude : MinecraftMeta.array(MinecraftMeta.object(library, "extract"), "exclude")) {
					excludes.add(exclude.getAsString());
				}

				plan.extractions.add(new LaunchPlan.Extract(download, excludes));
				plan.libraries.add(download);
				continue;
			}

			LaunchPlan.Download download = download(library, MinecraftMeta.object(MinecraftMeta.object(library, "downloads"), "artifact"), plan.librariesDir);

			if (download != null) {
				plan.libraries.add(download);
			}
		}
	}

	private static void resolveClientJar(LaunchPlan plan, LaunchContext context, List<JsonObject> chain) throws IOException {
		String jarId = MinecraftMeta.string(chain.get(0), "jar", null);
		JsonObject source = null;

		if (jarId != null) {
			source = MinecraftMeta.versionJson(jarId);
		} else {
			// Whichever JSON in the chain actually ships the client download.
			for (JsonObject json : chain) {
				JsonObject client = MinecraftMeta.object(MinecraftMeta.object(json, "downloads"), "client");

				if (client != null) {
					source = json;
					jarId = MinecraftMeta.string(json, "id", context.versionId());
					break;
				}
			}
		}

		if (source == null) {
			throw new IOException("no client download found for " + context.versionId());
		}

		JsonObject client = MinecraftMeta.object(MinecraftMeta.object(source, "downloads"), "client");
		Path target = context.versionsDir().resolve(jarId).resolve(jarId + ".jar");
		String path = jarId + "/" + jarId + ".jar";
		plan.clientJar = new LaunchPlan.Download(target, MinecraftMeta.string(client, "url", null),
			MinecraftMeta.string(client, "sha1", null), MinecraftMeta.number(client, "size", 0L), "client/" + path);
		plan.classpath.add(target);

		for (LaunchPlan.Download download : plan.libraries) {
			plan.classpath.add(download.target());
		}
	}

	private static void resolveAssets(LaunchPlan plan, List<JsonObject> chain) throws IOException {
		for (JsonObject json : chain) {
			JsonObject assetIndex = MinecraftMeta.object(json, "assetIndex");

			if (assetIndex == null) {
				continue;
			}

			plan.assetIndexId = MinecraftMeta.string(assetIndex, "id", "legacy");
			String url = MinecraftMeta.string(assetIndex, "url", null);

			if (url == null) {
				// Pre-1.6 versions have no downloadable index, only a virtual one.
				Log.warn("version %s has no asset index URL; asset lookup will be skipped", plan.minecraftVersion);
				return;
			}

			plan.assetIndex = new LaunchPlan.Download(plan.assetsDir.resolve("indexes").resolve(plan.assetIndexId + ".json"),
				url, MinecraftMeta.string(assetIndex, "sha1", null), MinecraftMeta.number(assetIndex, "size", 0L),
				"assets/indexes/" + plan.assetIndexId + ".json");
			return;
		}
	}

	private static void resolveLogConfig(LaunchPlan plan, List<JsonObject> chain) throws IOException {
		for (JsonObject json : chain) {
			JsonObject client = MinecraftMeta.object(MinecraftMeta.object(json, "logging"), "client");
			JsonObject file = MinecraftMeta.object(client, "file");

			if (file == null) {
				continue;
			}

			String id = MinecraftMeta.string(file, "id", "client.xml");
			plan.logConfig = new LaunchPlan.Download(plan.assetsDir.resolve("log_configs").resolve(id),
				MinecraftMeta.string(file, "url", null), MinecraftMeta.string(file, "sha1", null),
				MinecraftMeta.number(file, "size", 0L), "log config");
			return;
		}
	}

	private static void collectArguments(LaunchPlan plan, LaunchContext context, List<JsonObject> chain, Map<String, Boolean> features) {
		List<String> jvm = new ArrayList<>();
		List<String> game = new ArrayList<>();
		boolean sawArguments = false;

		for (int i = chain.size() - 1; i >= 0; i--) {
			JsonObject arguments = MinecraftMeta.object(chain.get(i), "arguments");

			if (arguments == null) {
				continue;
			}

			sawArguments = true;
			jvm.addAll(expand(MinecraftMeta.array(arguments, "jvm"), features));
			game.addAll(expand(MinecraftMeta.array(arguments, "game"), features));
		}

		if (!sawArguments) {
			// Pre-1.13 versions: one big string plus an implied library path argument.
			jvm.add("-Djava.library.path=${natives_directory}");
			String legacy = MinecraftMeta.string(chain.get(chain.size() - 1), "minecraftArguments", "");

			if (!legacy.isBlank()) {
				game.addAll(List.of(legacy.trim().split("\\s+")));
			}
		}

		jvm.add(0, "-cp");
		jvm.add(1, "${classpath}");
		plan.jvmArgs.addAll(substitute(jvm, placeholders(context, plan)));
		plan.gameArgs.addAll(substitute(game, placeholders(context, plan)));

		String extra = context.extraJvmArgs();

		if (!extra.isBlank()) {
			plan.jvmArgs.addAll(0, List.of(extra.trim().split("\\s+")));
		}
	}

	private static List<String> expand(JsonArray array, Map<String, Boolean> features) {
		List<String> args = new ArrayList<>();

		for (JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				args.add(element.getAsString());
				continue;
			}

			JsonObject entry = element.getAsJsonObject();

			if (!rulesAllow(entry, features)) {
				continue;
			}

			JsonElement value = entry.get("value");

			if (value == null) {
				continue;
			}

			if (value.isJsonArray()) {
				for (JsonElement item : value.getAsJsonArray()) {
					args.add(item.getAsString());
				}
			} else {
				args.add(value.getAsString());
			}
		}

		return args;
	}

	private static Map<String, String> placeholders(LaunchContext context, LaunchPlan plan) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("auth_player_name", context.account.name);
		values.put("auth_uuid", context.account.uuidCompact());
		// Offline launch: the server rejects these, singleplayer ignores them entirely.
		// A signed-in account hands the game its real token, which is what an online server checks.
		values.put("auth_access_token", context.account.token());
		values.put("auth_session", context.account.token());
		values.put("auth_xuid", context.account.online() && context.account.xuid != null && !context.account.xuid.isBlank()
			? context.account.xuid : "0");
		values.put("clientid", "shrd-launcher");
		values.put("user_type", "msa");
		values.put("user_properties", "{}");
		values.put("version_name", plan.versionId);
		values.put("version_type", context.instance.isFabric() ? "release" : "release");
		values.put("game_directory", plan.gameDir.toAbsolutePath().toString());
		values.put("assets_root", plan.assetsDir.toAbsolutePath().toString());
		values.put("assets_index_name", plan.assetIndexId);
		values.put("natives_directory", plan.nativesDir.toAbsolutePath().toString());
		values.put("launcher_name", LaunchContext.LAUNCHER_NAME);
		values.put("launcher_version", LaunchContext.LAUNCHER_VERSION);
		values.put("classpath", joinClasspath(plan));
		values.put("path", plan.logConfig != null ? plan.logConfig.target().toAbsolutePath().toString() : "");
		values.put("resolution_width", String.valueOf(context.instance.width));
		values.put("resolution_height", String.valueOf(context.instance.height));
		values.put("quickPlayPath", "");
		values.put("quickPlaySingleplayer", "");
		values.put("quickPlayMultiplayer", "");
		values.put("quickPlayRealms", "");
		values.put("library_directory", plan.librariesDir.toAbsolutePath().toString());
		values.put("classpath_separator", java.io.File.pathSeparator);
		return values;
	}

	private static String joinClasspath(LaunchPlan plan) {
		StringBuilder builder = new StringBuilder();

		for (Path entry : plan.classpath) {
			if (builder.length() > 0) {
				builder.append(java.io.File.pathSeparatorChar);
			}

			builder.append(entry.toAbsolutePath());
		}

		return builder.toString();
	}

	private static List<String> substitute(List<String> args, Map<String, String> values) {
		List<String> substituted = new ArrayList<>();

		for (String arg : args) {
			Matcher matcher = PLACEHOLDER.matcher(arg);
			StringBuilder builder = new StringBuilder();

			while (matcher.find()) {
				String name = matcher.group(1);
				String replacement = values.get(name);

				if (replacement == null) {
					Log.warn("unknown launch placeholder ${%s}", name);
					replacement = "";
				}

				matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
			}

			matcher.appendTail(builder);
			substituted.add(builder.toString());
		}

		return substituted;
	}

	private static void createExtractDirs(LaunchPlan plan) {
		// 26.2 points the JVM at these subdirectories of the natives folder; LWJGL and JNA extract
		// their shared libraries into them at runtime, so they have to exist first.
		for (String sub : new String[] { "java", "jna", "lwjgl", "netty" }) {
			try {
				java.nio.file.Files.createDirectories(plan.nativesDir.resolve(sub));
			} catch (IOException e) {
				Log.warn("could not create %s: %s", plan.nativesDir.resolve(sub), e.getMessage());
			}
		}
	}

	private static String nearest(LaunchContext context, List<JsonObject> chain, String key, String fallback) {
		for (JsonObject json : chain) {
			String value = MinecraftMeta.string(json, key, null);

			if (value != null) {
				return value;
			}
		}

		return fallback;
	}

	/** A stable identity for a library: group, artifact and classifier, so versions can override. */
	private static String libraryKey(JsonObject library) {
		String[] parts = MinecraftMeta.string(library, "name", "").split(":");
		StringBuilder key = new StringBuilder();

		for (int i = 0; i < Math.min(2, parts.length); i++) {
			key.append(parts[i]).append(':');
		}

		if (parts.length > 3) {
			key.append(parts[3]);
		}

		return key.toString();
	}

	private static LaunchPlan.Download download(JsonObject library, JsonObject artifact, Path librariesDir) {
		String name = MinecraftMeta.string(library, "name", "");

		if (name.isEmpty()) {
			return null;
		}

		String path = MinecraftMeta.string(artifact, "path", null);
		String url = MinecraftMeta.string(artifact, "url", null);
		String sha1 = MinecraftMeta.string(artifact, "sha1", null);
		long size = MinecraftMeta.number(artifact, "size", 0L);

		if (path == null) {
			// Fabric's profile gives only a Maven coordinate, so the path has to be derived.
			path = mavenPath(name);

			if (url == null) {
				String base = MinecraftMeta.string(library, "url", "https://libraries.minecraft.net/");
				url = base.endsWith("/") ? base + path : base + "/" + path;
			}

			if (sha1 == null) {
				sha1 = MinecraftMeta.string(library, "sha1", null);
			}

			if (size == 0L) {
				size = MinecraftMeta.number(library, "size", 0L);
			}
		}

		if (url == null) {
			Log.warn("library %s has no download URL; skipping", name);
			return null;
		}

		return new LaunchPlan.Download(librariesDir.resolve(path.replace('/', java.io.File.separatorChar)), url, sha1, size, name);
	}

	/** {@code group:artifact:version[:classifier]} to the Maven repository path of its jar. */
	static String mavenPath(String coordinate) {
		String[] parts = coordinate.split(":");

		if (parts.length < 3) {
			return coordinate.replace(':', '/') + ".jar";
		}

		String group = parts[0].replace('.', '/');
		String artifact = parts[1];
		String version = parts[2];
		String classifier = parts.length > 3 && !parts[3].isEmpty() ? "-" + parts[3] : "";
		return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
	}

	private static String classifierFor(JsonObject natives) {
		String os = osName();
		JsonElement element = natives.get(os);
		String classifier = element != null && element.isJsonPrimitive() ? element.getAsString() : null;

		if (classifier == null) {
			return null;
		}

		return classifier.replace("${arch}", is64Bit() ? "64" : "32");
	}

	/** Mojang's rule engine: the last matching rule wins, and no match means "excluded". */
	private static boolean rulesAllow(JsonObject owner, Map<String, Boolean> features) {
		JsonArray rules = MinecraftMeta.array(owner, "rules");

		if (rules.isEmpty()) {
			return true;
		}

		boolean allowed = false;

		for (JsonElement element : rules) {
			JsonObject rule = element.getAsJsonObject();

			if (ruleMatches(rule, features)) {
				allowed = "allow".equals(MinecraftMeta.string(rule, "action", "disallow"));
			}
		}

		return allowed;
	}

	private static boolean ruleMatches(JsonObject rule, Map<String, Boolean> features) {
		JsonObject os = MinecraftMeta.object(rule, "os");

		if (os != null) {
			String name = MinecraftMeta.string(os, "name", null);

			if (name != null && !name.equalsIgnoreCase(osName())) {
				return false;
			}

			String arch = MinecraftMeta.string(os, "arch", null);

			if (arch != null && !arch.equalsIgnoreCase(currentArch())) {
				return false;
			}

			String version = MinecraftMeta.string(os, "version", null);

			if (version != null && !System.getProperty("os.version", "").matches(version)) {
				return false;
			}
		}

		JsonObject required = MinecraftMeta.object(rule, "features");

		if (required != null) {
			for (String key : required.keySet()) {
				boolean expected = required.get(key).getAsBoolean();

				if (features.getOrDefault(key, false) != expected) {
					return false;
				}
			}
		}

		return true;
	}

	public static String osName() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		if (os.contains("win")) {
			return "windows";
		}

		if (os.contains("mac") || os.contains("darwin")) {
			return "osx";
		}

		return "linux";
	}

	private static String currentArch() {
		return is64Bit() ? "x86_64" : "x86";
	}

	private static boolean is64Bit() {
		return System.getProperty("os.arch", "").contains("64");
	}
}
