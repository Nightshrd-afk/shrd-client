package dev.shrd.client.gui;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.ShrdHudRenderer;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.Module;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Visual HUD layout editor: every overlay is drawn exactly where it appears in game and
 * can be dragged, switched on or off, and nudged with the arrow keys. Open it from
 * {@code HUD Editor} in the mod menu's sidebar (Right Shift) or with {@code /shrd hud}.
 */
public class HudEditorScreen extends Screen {

	private static final int GRID = 4;

	private HudModule dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean snapToGrid = true;

	public HudEditorScreen() {
		super(Component.literal("Shrd Client HUD Editor"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ShrdClient.get().onHudEditorClosed();
		super.onClose();
	}

	private List<HudModule> overlays() {
		List<HudModule> result = new java.util.ArrayList<>();

		for (Module module : ShrdClient.get().getModules().getModules()) {
			if (module instanceof HudModule hud) {
				result.add(hud);
			}
		}

		return result;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x90050910);

		for (HudModule hud : overlays()) {
			int[] bounds = ShrdHudRenderer.bounds(hud, this.width, this.height);
			boolean hovered = isInside(mouseX, mouseY, bounds);

			// Show disabled overlays as ghost outlines so they can still be placed.
			if (!hud.isEnabled()) {
				Renderer.rect(graphics, bounds[0], bounds[1], Math.max(24, bounds[2]), Math.max(11, bounds[3]), Theme.TEXT_DISABLED);
			} else {
				ShrdHudRenderer.renderModule(graphics, hud, partialTick, this.width, this.height, true);
			}

			if (hovered || hud == dragging) {
				Renderer.rect(graphics, bounds[0] - 1, bounds[1] - 1, bounds[2] + 2, bounds[3] + 2, hud == dragging ? Theme.ACCENT_ALT : Theme.ACCENT);
			}

			if (hovered) {
				graphics.fill(bounds[0], bounds[1] - 9, bounds[0] + Renderer.textWidth(hud.getName()) + 6, bounds[1], Theme.PANEL_HEADER);
				Renderer.text(graphics, hud.getName(), bounds[0] + 3, bounds[1] - 8, Theme.TEXT_ACCENT, true);
			}
		}

		drawHeader(graphics);
		drawFooter(graphics);
	}

	private void drawHeader(GuiGraphicsExtractor graphics) {
		int width = 300;
		int x = (this.width - width) / 2;

		Renderer.panel(graphics, x, 8, width, 18, Theme.WINDOW_BACKGROUND, Theme.PANEL_OUTLINE);
		Renderer.centeredText(graphics, "Shrd Client  •  HUD Editor", this.width / 2, 13, Theme.ACCENT);
	}

	private void drawFooter(GuiGraphicsExtractor graphics) {
		String hint = "drag to move   LMB outside a panel toggles it   arrows nudge   SHIFT+" + (snapToGrid ? "on" : "off")
			+ " snap   R reset all   ESC close";
		Renderer.centeredText(graphics, hint, this.width / 2, this.height - 14, Theme.TEXT_DISABLED);
	}

	private boolean isInside(int mouseX, int mouseY, int[] bounds) {
		return mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3];
	}

	private HudModule moduleAt(int mouseX, int mouseY) {
		List<HudModule> overlays = overlays();

		// Reverse order: the last drawn overlay wins, matching what the user sees on top.
		for (int index = overlays.size() - 1; index >= 0; index--) {
			HudModule hud = overlays.get(index);

			if (hud.isEnabled() && isInside(mouseX, mouseY, ShrdHudRenderer.bounds(hud, this.width, this.height))) {
				return hud;
			}
		}

		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		HudModule hud = moduleAt(mouseX, mouseY);

		if (hud != null) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				hud.toggle();
				return true;
			}

			int[] bounds = ShrdHudRenderer.bounds(hud, this.width, this.height);
			dragging = hud;
			dragOffsetX = mouseX - bounds[0];
			dragOffsetY = mouseY - bounds[1];
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		int x = (int) event.x() - dragOffsetX;
		int y = (int) event.y() - dragOffsetY;

		if (snapToGrid) {
			x = Math.round(x / (float) GRID) * GRID;
			y = Math.round(y / (float) GRID) * GRID;
		}

		dragging.setPosition(x / (double) this.width, y / (double) this.height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int step = event.modifiers() == GLFW.GLFW_MOD_SHIFT ? 1 : GRID;
		HudModule target = dragging;

		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
				// Arrow keys move the last overlay that was touched, or the only one on screen.
				if (target == null && overlays().size() == 1) {
					target = overlays().get(0);
				}

				if (target != null) {
					double dx = event.key() == GLFW.GLFW_KEY_LEFT ? -step : event.key() == GLFW.GLFW_KEY_RIGHT ? step : 0;
					double dy = event.key() == GLFW.GLFW_KEY_UP ? -step : event.key() == GLFW.GLFW_KEY_DOWN ? step : 0;
					target.setPosition(target.getX() + dx / this.width, target.getY() + dy / this.height);
					return true;
				}
			}
			case GLFW.GLFW_KEY_R -> {
				resetAll();
				return true;
			}
			case GLFW.GLFW_KEY_G -> {
				snapToGrid = !snapToGrid;
				return true;
			}
			default -> {
			}
		}

		return super.keyPressed(event);
	}

	private void resetAll() {
		int index = 0;

		for (HudModule hud : overlays()) {
			double x = hud.getX() > 0.5 ? 0.995 : 0.005;
			hud.setPosition(x, Math.min(0.9, 0.008 + index * 0.04));
			index++;
		}
	}
}
