package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

public class KeystrokesModule extends HudModule {

	private static final int BOX = 16;
	private static final int GAP = 2;
	private static final long FLASH_MILLIS = 120L;

	private final BooleanSetting showMouse = add(new BooleanSetting("Mouse Buttons", "Include left and right click boxes.", true));
	private final BooleanSetting showSpace = add(new BooleanSetting("Space Bar", "Include the jump key.", true));

	private boolean leftDown;
	private boolean rightDown;
	private long leftFlash;
	private long rightFlash;

	public KeystrokesModule() {
		super("Keystrokes", "Live WASD, space and mouse button display for streams and practice.", Category.HUD, 0.005, 0.62);
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		long handle = client.getWindow() == null ? 0L : client.getWindow().handle();

		if (handle == 0L) {
			return;
		}

		long now = System.currentTimeMillis();
		boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

		if (left && !leftDown) {
			leftFlash = now;
		}

		if (right && !rightDown) {
			rightFlash = now;
		}

		leftDown = left;
		rightDown = right;
	}

	private int columns() {
		return 3;
	}

	private int rows() {
		return 2 + (showMouse.get() ? 1 : 0) + (showSpace.get() ? 1 : 0);
	}

	@Override
	protected int measureWidth() {
		return columns() * BOX + (columns() + 1) * GAP;
	}

	@Override
	protected int measureHeight() {
		return rows() * BOX + (rows() - 1) * GAP;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		Minecraft client = Minecraft.getInstance();

		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, getWidth(), getHeight(), backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		int x = GAP;
		int y = hasBackground() ? GAP : 0;

		// Row 1: W centred.
		drawKey(graphics, "W", x + BOX + GAP, y, client.options.keyUp.isDown());

		// Row 2: A, S, D.
		int row2 = y + BOX + GAP;
		drawKey(graphics, "A", x, row2, client.options.keyLeft.isDown());
		drawKey(graphics, "S", x + BOX + GAP, row2, client.options.keyDown.isDown());
		drawKey(graphics, "D", x + BOX * 2 + GAP * 2, row2, client.options.keyRight.isDown());

		int nextRow = row2 + BOX + GAP;

		if (showMouse.get()) {
			long now = System.currentTimeMillis();
			drawWide(graphics, "LMB", x, nextRow, getWidth() - GAP * 2, leftDown || now - leftFlash < FLASH_MILLIS, leftDown);
			nextRow += BOX + GAP;
		}

		if (showSpace.get()) {
			drawWide(graphics, "SPACE", x, nextRow, getWidth() - GAP * 2, client.options.keyJump.isDown(), false);
		}
	}

	private void drawKey(GuiGraphicsExtractor graphics, String label, int x, int y, boolean active) {
		int background = active ? Theme.withAlpha(Theme.ACCENT, 0xCC) : Theme.MODULE_BACKGROUND;
		Renderer.roundedRect(graphics, x, y, BOX, BOX, background);
		Renderer.text(graphics, label, x + (BOX - Renderer.textWidth(label)) / 2, y + (BOX - 8) / 2 + 1,
			active ? Theme.ON_ACCENT : Theme.TEXT_DIM);
	}

	private void drawWide(GuiGraphicsExtractor graphics, String label, int x, int y, int width, boolean active, boolean mouse) {
		int background = active ? Theme.withAlpha(mouse ? Theme.ACCENT_ALT : Theme.ACCENT, 0xCC) : Theme.MODULE_BACKGROUND;
		Renderer.roundedRect(graphics, x, y, width, BOX, background);
		Renderer.text(graphics, label, x + (width - Renderer.textWidth(label)) / 2, y + (BOX - 8) / 2 + 1,
			active ? Theme.ON_ACCENT : Theme.TEXT_DIM);
	}
}
