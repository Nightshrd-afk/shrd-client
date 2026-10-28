package dev.shrd.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Watches your own attacks to power the Reach and Combo overlays.
 * <p>
 * This only observes what the vanilla client already does: it never swings, aims or
 * attacks for you. If the crosshair is on an entity when a left click starts, that click
 * is recorded with its distance and folded into the combo counter.
 */
public final class CombatTracker {

	private static final long COMBO_TIMEOUT_MILLIS = 3000L;

	private static boolean leftDown;
	private static float lastReach = -1f;
	private static float lastReachMax = 0f;
	private static int combo;
	private static int bestCombo;
	private static long lastHitMillis;

	private CombatTracker() {
	}

	public static void poll() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null || client.gui.screen() != null) {
			leftDown = false;
			return;
		}

		long handle = client.getWindow() == null ? 0L : client.getWindow().handle();

		if (handle == 0L) {
			return;
		}

		boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if (down && !leftDown) {
			registerHit(client);
		}

		leftDown = down;

		if (combo > 0 && System.currentTimeMillis() - lastHitMillis > COMBO_TIMEOUT_MILLIS) {
			combo = 0;
		}
	}

	private static void registerHit(Minecraft client) {
		if (!(client.hitResult instanceof EntityHitResult hit)) {
			return;
		}

		double distance = Math.sqrt(client.player.distanceToSqr(hit.getEntity()));

		lastReach = (float) distance;
		lastReachMax = Math.max(lastReachMax, lastReach);

		long now = System.currentTimeMillis();

		if (now - lastHitMillis <= COMBO_TIMEOUT_MILLIS) {
			combo++;
		} else {
			combo = 1;
		}

		bestCombo = Math.max(bestCombo, combo);
		lastHitMillis = now;
	}

	public static float lastReach() {
		return lastReach;
	}

	public static float maxReach() {
		return lastReachMax;
	}

	public static int combo() {
		return combo;
	}

	public static int bestCombo() {
		return bestCombo;
	}

	public static long millisSinceLastHit() {
		return lastHitMillis == 0L ? -1L : System.currentTimeMillis() - lastHitMillis;
	}

	public static void reset() {
		lastReach = -1f;
		lastReachMax = 0f;
		combo = 0;
		bestCombo = 0;
		lastHitMillis = 0L;
		leftDown = false;
	}
}
