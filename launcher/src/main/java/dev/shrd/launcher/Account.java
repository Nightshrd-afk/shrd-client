package dev.shrd.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A player account. An offline account has only a name — the launcher derives the same offline UUID
 * Minecraft's own launcher uses, which is what singleplayer and LAN games key on. A Microsoft
 * account carries the Minecraft access token and Mojang UUID from the sign-in chain, and that token
 * is what servers like Donut SMP ask for.
 */
public final class Account {

	public String name = "Player";
	/** Offline UUID, or the Mojang UUID for a signed-in account. */
	public String uuid = "";
	public String type = "offline";
	/** Minecraft access token from api.minecraftservices.com; empty for offline accounts. */
	public String accessToken = "";
	/** Microsoft refresh token, so a sign-in can outlive one access token. */
	public String refreshToken = "";
	/** Xbox user id, sent to the game as auth_xuid. */
	public String xuid = "";
	/** The skin the account actually wears, when the profile says so. */
	public String skinUrl = "";

	public Account() {
	}

	public Account(String name) {
		this.name = name;
	}

	public boolean online() {
		return "microsoft".equals(type) && accessToken != null && !accessToken.isBlank();
	}

	/** What the game gets as {@code --access-token}: real for a signed-in account. */
	public String token() {
		return online() ? accessToken : "shrd-offline";
	}

	public String kind() {
		return online() ? "Microsoft" : "offline";
	}

	public String uuid() {
		if (uuid == null || uuid.isBlank()) {
			uuid = offlineUuid(name);
		}

		return uuid;
	}

	public String uuidCompact() {
		return uuid().replace("-", "");
	}

	/** Vanilla's offline UUID: the version-3 name UUID of {@code OfflinePlayer:<name>}. */
	public static String offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
	}

	public static boolean validName(String name) {
		return name != null && name.matches("[A-Za-z0-9_]{3,16}");
	}

	/** Drops the tokens but keeps the account, for signing out without losing the name. */
	public void signOut() {
		type = "offline";
		accessToken = "";
		refreshToken = "";
		xuid = "";
		uuid = "";
		skinUrl = "";
	}
}
