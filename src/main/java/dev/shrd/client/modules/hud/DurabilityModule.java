package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class DurabilityModule extends HudModule {

	private final IntSetting warningPercent = add(new IntSetting("Warning %", "Turn the text red below this durability.", 15, 1, 100));
	private final BooleanSetting stack = add(new BooleanSetting("Modal Count", "Show how many items are in the held stack.", false));

	public DurabilityModule() {
		super("Durability", "Remaining durability of the item you are holding.", Category.HUD, 0.42, 0.93);
	}

	private ItemStack held() {
		return PlayerUtils.player() == null ? ItemStack.EMPTY : PlayerUtils.player().getMainHandItem();
	}

	private String text() {
		return cached(this::build);
	}

	private String build() {
		ItemStack stack = held();

		if (stack.isEmpty()) {
			return "empty hand";
		}

		if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return Renderer.truncate(stack.getHoverName().getString(), 90);
		}

		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		int percent = (int) Math.round(remaining * 100.0 / stack.getMaxDamage());
		String suffix = this.stack.get() && stack.getCount() > 1 ? " x" + stack.getCount() : "";

		return Renderer.truncate(stack.getHoverName().getString(), 70) + " " + remaining + " (" + percent + "%)" + suffix;
	}

	private double ratio() {
		ItemStack stack = held();

		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return 1.0;
		}

		return (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage();
	}

	@Override
	protected int measureWidth() {
		return Math.max(90, Renderer.textWidth(text()) + 8);
	}

	@Override
	protected int measureHeight() {
		return Renderer.textHeight() + 7;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, getWidth(), getHeight(), backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		double ratio = ratio();
		int color = ratio * 100.0 < warningPercent.get() ? Theme.healthColor(0.1f) : Theme.healthColor(1f);
		Renderer.text(graphics, text(), 5, 3, color, hasShadow());
		Renderer.bar(graphics, 4, getHeight() - 2, getWidth() - 8, 1, ratio, Theme.SLIDER_TRACK, color);
	}
}
