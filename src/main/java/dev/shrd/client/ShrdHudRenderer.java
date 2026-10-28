package dev.shrd.client;

import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.Module;
import dev.shrd.client.util.CombatTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The single HUD hook Shrd Client needs: it draws every enabled overlay in order,
 * each one positioned by its stored screen fraction and scaled by its own setting.
 */
public final class ShrdHudRenderer implements HudElement {

	private static final Identifier ID = Identifier.fromNamespaceAndPath(ShrdClient.ID, "overlays");

	private ShrdHudRenderer() {
	}

	public static void register() {
		HudElementRegistry.addLast(ID, new ShrdHudRenderer());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();

		// Overlays follow vanilla behaviour and stay visible behind inventory, chat and the
		// pause menu. The one exception is the HUD editor, which draws them itself so they
		// can be dragged without doubling up.
		if (client.gui == null || client.player == null || client.level == null || ShrdClient.get().isHudEditorOpen()) {
			return;
		}

		CombatTracker.poll();

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();

		for (Module module : ShrdClient.get().getModules().getHudModules()) {
			HudModule hud = (HudModule) module;
			renderModule(graphics, hud, partialTick, screenWidth, screenHeight, false);
		}
	}

	/**
	 * Draws one overlay. {@code clamped} keeps elements inside the screen in the HUD editor
	 * while dragging, and normal play lets them float exactly where they were dropped.
	 */
	public static void renderModule(GuiGraphicsExtractor graphics, HudModule hud, float partialTick,
									int screenWidth, int screenHeight, boolean clamped) {
		float scale = hud.getScale();
		int width = Math.round(hud.getWidth() * scale);
		int height = Math.round(hud.getHeight() * scale);

		double x = hud.getX() * screenWidth;
		double y = hud.getY() * screenHeight;

		if (clamped) {
			// Elements anchored to the right or bottom half stay fully on screen.
			if (hud.getX() > 0.5) {
				x = Math.min(x, screenWidth - width);
			}

			if (hud.getY() > 0.5) {
				y = Math.min(y, screenHeight - height);
			}

			x = Math.max(0, x);
			y = Math.max(0, y);
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(scale, scale);
		hud.render(graphics, partialTick);
		pose.popMatrix();
	}

	/** Bounding box of an overlay on screen, used by the HUD editor for hit testing. */
	public static int[] bounds(HudModule hud, int screenWidth, int screenHeight) {
		float scale = hud.getScale();
		int width = Math.round(hud.getWidth() * scale);
		int height = Math.round(hud.getHeight() * scale);
		int x = (int) Math.round(hud.getX() * screenWidth);
		int y = (int) Math.round(hud.getY() * screenHeight);

		if (hud.getX() > 0.5) {
			x = Math.min(x, screenWidth - width);
		}

		if (hud.getY() > 0.5) {
			y = Math.min(y, screenHeight - height);
		}

		return new int[] {Math.max(0, x), Math.max(0, y), width, height};
	}
}
