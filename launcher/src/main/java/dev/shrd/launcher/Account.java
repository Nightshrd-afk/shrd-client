package dev.shrd.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A player account. Shrd Launcher ships offline accounts: you pick your own name and the launcher
 * derives the same offline UUID Minecraft's own launcher uses, which is what singleplayer and
 * LAN games key on. Online (Microsoft) sign-in is not implemented — see the README; launching
 * with an offline account works singleplayer and on servers with authentication disabled.
 */
public final class Account {

	public String name = "Player";
	/** Offline UUID, or the Mojang UUID if the account ever gets a real login. */
	public String uuid = "";
	public String type = "offline";

	public Account() {
	}

	public Account(String name) {
		this.name = name;
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
}
