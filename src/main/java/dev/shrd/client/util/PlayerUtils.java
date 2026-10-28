package dev.shrd.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Everything the HUD modules need to read about the game, in one place. */
public final class PlayerUtils {

	private PlayerUtils() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static boolean inGame() {
		return mc().player != null && mc().level != null;
	}

	public static LocalPlayer player() {
		return mc().player;
	}

	public static ClientLevel level() {
		return mc().level;
	}

	/** Tab-list latency in milliseconds, or -1 when unknown. */
	public static int ping() {
		if (!inGame() || mc().getConnection() == null) {
			return -1;
		}

		PlayerInfo info = mc().getConnection().getPlayerInfo(player().getUUID());
		return info == null ? -1 : info.getLatency();
	}

	public static int fps() {
		return mc().getFps();
	}

	/** {@code overworld}, {@code the_nether}, {@code the_end}, or a modded path. */
	public static String dimension() {
		if (mc().level == null) {
			return "none";
		}

		return mc().level.dimension().identifier().getPath();
	}

	public static String biome() {
		if (!inGame()) {
			return "unknown";
		}

		BlockPos pos = player().blockPosition();

		return level()
			.getBiome(pos)
			.unwrapKey()
			.map(key -> key.identifier().getPath())
			.orElse("unknown");
	}

	public static String facing() {
		if (!inGame()) {
			return "?";
		}

		Direction direction = player().getDirection();

		return switch (direction) {
			case NORTH -> "-Z (north)";
			case SOUTH -> "+Z (south)";
			case EAST -> "+X (east)";
			case WEST -> "-X (west)";
			case UP -> "up";
			case DOWN -> "down";
		};
	}

	/** Short facing letter used by the coordinate overlay: N/S/E/W. */
	public static String facingShort() {
		if (!inGame()) {
			return "?";
		}

		return switch (player().getDirection()) {
			case NORTH -> "N";
			case SOUTH -> "S";
			case EAST -> "E";
			case WEST -> "W";
			case UP -> "U";
			case DOWN -> "D";
		};
	}

	public static String serverLabel() {
		Minecraft client = mc();

		if (client.isLocalServer()) {
			return "singleplayer";
		}

		if (client.getCurrentServer() != null) {
			return client.getCurrentServer().ip;
		}

		return "unknown";
	}

	public static String coords(boolean withDecimals) {
		if (!inGame()) {
			return "0 0 0";
		}

		if (withDecimals) {
			return String.format(Locale.ROOT, "%.2f %.2f %.2f", player().getX(), player().getY(), player().getZ());
		}

		return String.format(Locale.ROOT, "%d %d %d",
			(int) Math.floor(player().getX()),
			(int) Math.floor(player().getY()),
			(int) Math.floor(player().getZ()));
	}

	public static String chunkCoords() {
		if (!inGame()) {
			return "0 0";
		}

		return String.format(Locale.ROOT, "%d %d",
			(int) Math.floor(player().getX()) >> 4,
			(int) Math.floor(player().getZ()) >> 4);
	}

	/** Horizontal speed in blocks per second. */
	public static double horizontalSpeed() {
		if (!inGame()) {
			return 0.0;
		}

		var movement = player().getDeltaMovement();
		return Math.sqrt(movement.x * movement.x + movement.z * movement.z) * 20.0;
	}

	public static double verticalSpeed() {
		if (!inGame()) {
			return 0.0;
		}

		return player().getDeltaMovement().y * 20.0;
	}

	public static float healthRatio() {
		if (!inGame()) {
			return 0f;
		}

		float max = Math.max(1f, player().getMaxHealth());
		return Math.max(0f, Math.min(1f, player().getHealth() / max));
	}

	/** Number of totems of undying anywhere in the player's inventory. */
	public static int totemCount() {
		if (!inGame()) {
			return 0;
		}

		var inventory = player().getInventory();
		int count = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
				count += stack.getCount();
			}
		}

		return count;
	}

	public static ItemStack armor(EquipmentSlot slot) {
		if (!inGame()) {
			return ItemStack.EMPTY;
		}

		return player().getItemBySlot(slot);
	}

	public static String label(ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.getHoverName().getString();
	}

	/** Ambient light of the current world, used by the fullbright code path for logging. */
	public static int skyDarken() {
		return level() == null ? 0 : level().getSkyDarken();
	}
}
