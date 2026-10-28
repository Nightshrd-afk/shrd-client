package dev.shrd.client.core;

/**
 * The categories shown as panels in the Shrd Client ClickGUI.
 * <p>
 * Shrd Client deliberately ships only server-safe modules: information overlays and
 * quality-of-life tools. There is no combat automation, movement exploitation or
 * anti-cheat bypass anywhere in this client.
 */
public enum Category {
	HUD("HUD"),
	UTILITY("Utility"),
	PLAYER("Player"),
	RENDER("Render"),
	CLIENT("Client");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
