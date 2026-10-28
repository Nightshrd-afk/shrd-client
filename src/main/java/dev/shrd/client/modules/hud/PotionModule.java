package dev.shrd.client.modules.hud;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PotionModule extends HudModule {

	private final BooleanSetting amplifier = add(new BooleanSetting("Amplifier", "Show the effect level in roman numerals.", true));
	private final BooleanSetting hideAmbient = add(new BooleanSetting("Hide Ambient", "Hide beacon and conduit style effects.", false));

	public PotionModule() {
		super("Potion Effects", "Active status effects with their remaining time on the right side.", Category.HUD, 0.995, 0.45);
	}

	/** One effect row: its text and its width, measured once. */
	private record Row(String label, int width) {
	}

	/**
	 * The effect rows, sorted and measured once per tick.
	 * <p>
	 * This used to copy and sort the effect list, resolve every effect's translated name
	 * and format its timer on every frame — several list allocations and a sort per frame
	 * for a list that only changes when an effect starts, ends or ticks down.
	 */
	private List<Row> rows() {
		return cached(this::buildRows);
	}

	private List<Row> buildRows() {
		List<MobEffectInstance> effects = new ArrayList<>();

		if (PlayerUtils.inGame()) {
			effects.addAll(PlayerUtils.player().getActiveEffects());
			effects.removeIf(effect -> hideAmbient.get() && effect.isAmbient());
		}

		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));

		List<Row> rows = new ArrayList<>(effects.size());

		for (MobEffectInstance effect : effects) {
			String label = label(effect);
			rows.add(new Row(label, Renderer.textWidth(label)));
		}

		return rows;
	}

	private String label(MobEffectInstance effect) {
		String name = Component.translatable(effect.getDescriptionId()).getString();
		// Strip the "Potion of " style prefix so rows stay short.
		name = name.replace("Potion of ", "").replace("Potion of the ", "");

		if (amplifier.get() && effect.getAmplifier() > 0) {
			name = name + " " + roman(effect.getAmplifier() + 1);
		}

		int seconds = effect.getDuration() / 20;
		return String.format(java.util.Locale.ROOT, "%s  %d:%02d", name, seconds / 60, seconds % 60);
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			default -> "X+";
		};
	}

	@Override
	protected int measureWidth() {
		int widest = 90;

		for (Row row : rows()) {
			widest = Math.max(widest, row.width() + 14);
		}

		return widest;
	}

	@Override
	protected int measureHeight() {
		int count = rows().size();
		return count == 0 ? 13 : count * 11 + 5;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		List<Row> rows = rows();
		int width = getWidth();

		if (hasBackground()) {
			Renderer.panel(graphics, 0, 0, width, getHeight(), backgroundArgb(), Theme.PANEL_OUTLINE);
		}

		if (rows.isEmpty()) {
			var text = "no effects";
			Renderer.text(graphics, text, width - Renderer.textWidth(text) - 5, 3, Theme.TEXT_DISABLED, hasShadow());
			return;
		}

		int y = 3;

		for (Row row : rows) {
			// Right aligned so the column hugs the screen edge.
			graphics.fill(width - 3, y, width - 2, y + 9, Theme.ACCENT_ALT);
			Renderer.text(graphics, row.label(), width - row.width() - 6, y, Theme.TEXT, hasShadow());
			y += 11;
		}
	}
}
