package dev.shrd.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shrd.launcher.Account;
import dev.shrd.launcher.Log;
import dev.shrd.launcher.launch.Http;

import java.io.IOException;
import java.util.Map;

/**
 * Microsoft sign-in, the same chain the official launcher walks: device code → Microsoft token →
 * Xbox Live → XSTS → Minecraft services → profile. The Minecraft token it ends with is what an
 * online server accepts, so this is what makes servers like Donut SMP possible at all.
 * <p>
 * It needs one thing this project cannot ship: an Azure application (client) id. A client id is
 * public by design — it is sent to Microsoft in the URL — but it belongs to whoever registers the
 * app, and registering one is a two-minute, free step in the Azure portal with your own Microsoft
 * account. {@link #CLIENT_HELP} is where to do it; the client id is then pasted into Settings.
 * <p>
 * The device-code flow is used rather than a browser redirect because it needs no client secret and
 * no local web server: the launcher shows a short code, you type it on Microsoft's page, and the
 * launcher polls until the token arrives.
 */
public final class MicrosoftAuth {

	public static final String CLIENT_HELP = "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade";

	private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
	private static final String SCOPE = "XboxLive.signin offline_access";

	private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
	private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
	private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
	private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
	private static final String XSTS_RELYING_PARTY = "rp://api.minecraftservices.com/";

	/** Progress the UI shows while the user is on Microsoft's page. */
	public interface Listener {
		void status(String message);
	}

	public record Device(String deviceCode, String userCode, String verificationUri, int interval, int expiresIn) {
	}

	public record Tokens(String accessToken, String refreshToken) {
	}

	private MicrosoftAuth() {
	}

	/** Asks Microsoft for a code to show the user, and the interval to poll at. */
	public static Device begin(String clientId) throws IOException {
		Http.Reply reply = Http.postForm(DEVICE_CODE_URL, Map.of("client_id", clientId, "scope", SCOPE), null);

		if (!reply.ok()) {
			throw new IOException(explain(reply));
		}

		JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();
		return new Device(
			text(json, "device_code"),
			text(json, "user_code"),
			text(json, "verification_uri"),
			number(json, "interval", 5),
			number(json, "expires_in", 900));
	}

	/**
	 * Polls until the user approves the code. An {@code authorization_pending} reply is the normal
	 * case — it means nobody has typed the code yet — so it is not treated as a failure.
	 */
	public static Tokens await(String clientId, Device device, Listener listener) throws IOException {
		long deadline = System.currentTimeMillis() + device.expiresIn() * 1000L;
		int interval = Math.max(1, device.interval());

		while (System.currentTimeMillis() < deadline) {
			Http.sleep(interval * 1000L);

			Http.Reply reply = Http.postForm(TOKEN_URL, Map.of(
				"grant_type", "urn:ietf:params:oauth:grant-type:device_code",
				"client_id", clientId,
				"device_code", device.deviceCode()), null);

			if (reply.ok()) {
				JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();
				return new Tokens(text(json, "access_token"), text(json, "refresh_token"));
			}

			String error = errorCode(reply);

			switch (error) {
				case "authorization_pending" -> listener.status("Waiting for the code to be entered…");
				case "slow_down" -> interval += 5;
				case "expired_token" -> throw new IOException("That code expired. Press Sign in again for a new one.");
				case "authorization_declined", "access_denied" -> throw new IOException("Sign-in was declined on the Microsoft page.");
				default -> throw new IOException(explain(reply));
			}
		}

		throw new IOException("That code expired. Press Sign in again for a new one.");
	}

	/**
	 * Trades the Microsoft token for a Minecraft one: Xbox Live, then XSTS, then Minecraft services,
	 * then the profile that supplies the real player name, UUID and skin.
	 */
	public static Account complete(Tokens tokens, Listener listener) throws IOException {
		listener.status("Signing in to Xbox Live…");
		String xblToken = xboxLive(tokens.accessToken());
		listener.status("Authorising with XSTS…");
		Xsts xsts = xsts(xblToken);
		listener.status("Signing in to Minecraft…");
		MinecraftToken minecraft = minecraft(xsts);
		listener.status("Reading your Minecraft profile…");
		JsonObject profile = profile(minecraft.token());

		Account account = new Account(text(profile, "name"));
		account.type = "microsoft";
		account.uuid = text(profile, "id");
		account.accessToken = minecraft.token();
		account.refreshToken = tokens.refreshToken();
		account.xuid = xsts.xuid();
		account.skinUrl = skinUrl(profile);

		if (account.name.isBlank() || account.uuid.isBlank()) {
			throw new IOException("Microsoft signed you in, but this account does not own Minecraft: Java Edition.");
		}

		Log.info("microsoft sign-in: %s (%s)", account.name, account.uuid);
		return account;
	}

	/** The whole chain, for callers that do not need to show the code themselves. */
	public static Account signIn(String clientId, Device device, Listener listener) throws IOException {
		return complete(await(clientId, device, listener), listener);
	}

	// ------------------------------------------------------------------ the chain

	private static String xboxLive(String microsoftToken) throws IOException {
		String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
			+ "\"RpsTicket\":\"d=" + escape(microsoftToken) + "\"},"
			+ "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
		Http.Reply reply = Http.postJson(XBL_URL, body, null);

		if (!reply.ok()) {
			throw new IOException("Xbox Live rejected the Microsoft token (" + reply.status() + "). " + explain(reply));
		}

		return text(JsonParser.parseString(reply.body()).getAsJsonObject(), "Token");
	}

	private record Xsts(String token, String uhs, String xuid) {
	}

	private static Xsts xsts(String xblToken) throws IOException {
		String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + escape(xblToken) + "\"]},"
			+ "\"RelyingParty\":\"" + XSTS_RELYING_PARTY + "\",\"TokenType\":\"JWT\"}";
		Http.Reply reply = Http.postJson(XSTS_URL, body, null);

		if (!reply.ok()) {
			throw new IOException(xstsMessage(reply));
		}

		JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();
		JsonObject user = firstClaim(json, "xui");
		return new Xsts(text(json, "Token"), text(user, "uhs"), text(user, "xid"));
	}

	private record MinecraftToken(String token) {
	}

	private static MinecraftToken minecraft(Xsts xsts) throws IOException {
		String body = "{\"identityToken\":\"XBL3.0 x=" + escape(xsts.uhs()) + ";" + escape(xsts.token()) + "\"}";
		Http.Reply reply = Http.postJson(MC_LOGIN_URL, body, null);

		if (!reply.ok()) {
			throw new IOException("Minecraft login failed (" + reply.status() + "). " + explain(reply));
		}

		return new MinecraftToken(text(JsonParser.parseString(reply.body()).getAsJsonObject(), "access_token"));
	}

	private static JsonObject profile(String minecraftToken) throws IOException {
		Http.Reply reply = Http.get(MC_PROFILE_URL, Map.of("Authorization", "Bearer " + minecraftToken));

		if (reply.status() == 404) {
			throw new IOException("This Microsoft account does not own Minecraft: Java Edition.");
		}

		if (!reply.ok()) {
			throw new IOException("Could not read the Minecraft profile (" + reply.status() + "). " + explain(reply));
		}

		return JsonParser.parseString(reply.body()).getAsJsonObject();
	}

	private static String skinUrl(JsonObject profile) {
		JsonArray skins = profile.has("skins") && profile.get("skins").isJsonArray() ? profile.getAsJsonArray("skins") : null;

		if (skins != null && !skins.isEmpty() && skins.get(0).isJsonObject()) {
			return text(skins.get(0).getAsJsonObject(), "url");
		}

		return "";
	}

	// ------------------------------------------------------------------ json bits

	private static JsonObject firstClaim(JsonObject json, String claim) {
		if (!json.has("DisplayClaims") || !json.get("DisplayClaims").isJsonObject()) {
			return new JsonObject();
		}

		JsonObject claims = json.getAsJsonObject("DisplayClaims");

		if (!claims.has(claim) || !claims.get(claim).isJsonArray()) {
			return new JsonObject();
		}

		JsonArray entries = claims.getAsJsonArray(claim);
		return entries.isEmpty() || !entries.get(0).isJsonObject() ? new JsonObject() : entries.get(0).getAsJsonObject();
	}

	private static String text(JsonObject json, String key) {
		return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
	}

	private static int number(JsonObject json, String key, int fallback) {
		try {
			return json.has(key) ? json.get(key).getAsInt() : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	private static String errorCode(Http.Reply reply) {
		try {
			JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();
			return text(json, "error");
		} catch (Exception e) {
			return "";
		}
	}

	/** Microsoft and Xbox put a human sentence in the body; prefer it over a bare status code. */
	private static String explain(Http.Reply reply) {
		try {
			JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();

			for (String key : new String[] { "error_description", "error", "message" }) {
				String value = text(json, key);

				if (!value.isBlank()) {
					return "HTTP " + reply.status() + ": " + value;
				}
			}
		} catch (Exception ignored) {
			// Not JSON: fall through to the raw body.
		}

		String body = reply.body() == null ? "" : reply.body().trim();
		return "HTTP " + reply.status() + (body.isBlank() ? "" : ": " + body.substring(0, Math.min(200, body.length())));
	}

	/** XSTS answers 401 with a numeric XErr, and each number means something different. */
	private static String xstsMessage(Http.Reply reply) {
		String code = "";

		try {
			JsonObject json = JsonParser.parseString(reply.body()).getAsJsonObject();
			code = json.has("XErr") && !json.get("XErr").isJsonNull() ? json.get("XErr").getAsString() : "";
		} catch (Exception ignored) {
			// Falls back to the generic message.
		}

		return switch (code) {
			case "2148916233" -> "This Microsoft account has never created an Xbox profile. Sign in once at xbox.com with it, then try again.";
			case "2148916235" -> "Xbox Live is not available in the country on this Microsoft account.";
			case "2148916236", "2148916237" -> "This Xbox account needs to be verified on xbox.com before it can be used.";
			case "2148916238" -> "This is a child account. It has to be added to a Microsoft family by an adult first.";
			default -> "Xbox Live authorisation failed (XErr " + (code.isBlank() ? "unknown" : code) + "). " + explain(reply);
		};
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
