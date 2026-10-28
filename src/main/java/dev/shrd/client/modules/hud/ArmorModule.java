package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ArmorModule extends HudModule {

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	};

	private final BooleanSetting durabilityPercent = add(new BooleanSetting("Percent", "Show the remaining durability number.", true));

	public ArmorModule() {
		super("Armor", "Armor durability with a bar per piece, plus the item in your hand.", Category.HUD, 0.995, 0.05);
	}

	/** One drawn row: its label, how full its bar is and whether the slot is empty. */
	private record Row(String label, double ratio, boolean empty) {
	}

	/**
	 * Rows for the four armor slots plus the held item, built once per tick.
	 * <p>
	 * Every row used to be rebuilt three times a frame (width measurement, panel layout and
	 * the draw itself), and each rebuild asked the item for its name and truncated it.
	 */
	private List<Row> rows() {
		return cached(this::buildRows);
	}

	private List<Row> buildRows() {
		List<Row> rows = new ArrayList<>(SLOTS.length + 1);

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = PlayerUtils.armor(slot);
			rows.add(new Row(rowLabel(stack), rowRatio(stack), stack.isEmpty()));
		}

		ItemStack held = PlayerUtils.player() == null ? ItemStack.EMPTY : PlayerUtils.player().getMainHandItem();
		rows.add(new Row(rowLabel(held), rowRatio(held), held.isEmpty()));

		return rows;
	}

	private String rowLabel(ItemStack stack) {
		if (stack.isEmpty()) {
			return "- empty -";
		}

		String name = Renderer.truncate(stack.getHoverName().getString(), 70);

		if (durabilityPercent.get() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			return name + " " + remaining + "/" + stack.getMaxDamage();
		}

		return name;
	}

	private double rowRatio(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return 1.0;
		}

		return (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage();
	}

	@Override
	protected int measureWidth() {
		int widest = 80;

		for (Row row : rows()) {
			widest = Math.max(widest, Renderer.textWidth(row.label()) + 8);
		}

		return widest;
	}

	@Override
	protected int measureHeight() {
		return SLOTS.length * 11 + 12;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		int width = getWidth();
		int height = getHeight();

		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, width, height, backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		List<Row> rows = rows();
		int y = 3;

		for (int index = 0; index < SLOTS.length; index++) {
			Row row = rows.get(index);
			drawRow(graphics, width, y, row);
			y += 11;
		}

		graphics.fill(4, y - 1, width - 4, y, Theme.PANEL_OUTLINE);
		drawRow(graphics, width, y + 2, rows.get(SLOTS.length));
	}

	private void drawRow(GuiGraphicsExtractor graphics, int width, int y, Row row) {
		int color = row.empty() ? Theme.TEXT_DISABLED : Theme.healthColor((float) row.ratio());
		Renderer.text(graphics, row.label(), 5, y, color, hasShadow());
		Renderer.bar(graphics, 5, y + 9, width - 10, 1, row.ratio(), Theme.SLIDER_TRACK, color);
	}
}
