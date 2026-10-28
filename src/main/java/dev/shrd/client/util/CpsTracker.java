package dev.shrd.client.util;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts mouse clicks in a rolling one second window.
 * <p>
 * {@link #poll(long)} is called from both the client tick and the HUD render pass, so a
 * click is never missed even when frames and ticks do not line up.
 */
public final class CpsTracker {

	private static final long WINDOW_MILLIS = 1000L;

	private final Deque<Long> leftClicks = new ArrayDeque<>();
	private final Deque<Long> rightClicks = new ArrayDeque<>();

	private boolean leftDown;
	private boolean rightDown;

	public void poll(long windowHandle) {
		if (windowHandle == 0L) {
			return;
		}

		long now = System.currentTimeMillis();

		boolean left = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean right = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

		if (left && !leftDown) {
			leftClicks.addLast(now);
		}

		if (right && !rightDown) {
			rightClicks.addLast(now);
		}

		leftDown = left;
		rightDown = right;

		trim(leftClicks, now);
		trim(rightClicks, now);
	}

	private void trim(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_MILLIS) {
			clicks.pollFirst();
		}
	}

	public int left() {
		return leftClicks.size();
	}

	public int right() {
		return rightClicks.size();
	}

	public boolean leftDown() {
		return leftDown;
	}

	public boolean rightDown() {
		return rightDown;
	}

	/** Milliseconds since the most recent left click, or -1 when there has never been one. */
	public long millisSinceLeftClick() {
		return leftClicks.isEmpty() ? -1L : System.currentTimeMillis() - leftClicks.peekLast();
	}
}
