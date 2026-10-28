package dev.shrd.client.modules.hud;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.HudModule;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.gui.Theme;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The classic "array list": every enabled module in a right-aligned column, with a coloured
 * accent bar per row and an optional session counter in the header.
 */
public class ArrayListModule extends HudModule {

	private final BooleanSetting header = add(new BooleanSetting("Header", "Show the shrd header row.", true));
	private final BooleanSetting sortByWidth = add(new BooleanSetting("Sort By Width", "Longest names on top.", false));
	private final BooleanSetting hideSelf = add(new BooleanSetting("Hide Self", "Don't list this element.", true));

	public ArrayListModule() {
		super("ArrayList", "A column of every enabled module, newest state included.", Category.HUD, 0.995, 0.008);
	}

	/** One listed module: its name and its measured width. */
	private record Row(String name, int width) {
	}

	/** The finished column: sorted rows plus the width of the widest one. */
	private record Layout(List<Row> rows, int width) {
	}

	/**
	 * The whole column, built once per tick.
	 * <p>
	 * Building it meant walking every registered module, measuring each name with the font
	 * to sort it, and doing that again for the width — several times per frame. Now it runs
	 * once per tick, and only the rows themselves are handed to the renderer.
	 */
	private Layout layout() {
		return cached(this::buildLayout);
	}

	private Layout buildLayout() {
		List<Row> rows = new ArrayList<>();

		for (Module module : ShrdClient.get().getModules().getModules()) {
			if (!module.isEnabled()) {
				continue;
			}

			if (hideSelf.get() && module == this) {
				continue;
			}

			rows.add(new Row(module.getName(), Renderer.textWidth(module.getName())));
		}

		Comparator<Row> byWidth = Comparator.comparingInt(Row::width);
		rows.sort(sortByWidth.get() ? byWidth.reversed() : byWidth);

		int widest = 40;

		for (Row row : rows) {
			widest = Math.max(widest, row.width() + 8);
		}

		return new Layout(rows, widest);
	}

	@Override
	protected int measureWidth() {
		return layout().width();
	}

	@Override
	protected int measureHeight() {
		return layout().rows().size() * 10 + (header.get() ? 12 : 0);
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, float partialTick) {
		Layout layout = layout();
		int width = layout.width();
		int y = 0;

		if (header.get()) {
			String title = "shrd";
			Renderer.text(graphics, title, width - Renderer.textWidth(title) - 4, 2, Theme.ACCENT, hasShadow());
			y += 12;
		}

		for (Row row : layout.rows()) {
			Renderer.roundedRect(graphics, 0, y, width, 9, hasBackground() ? backgroundArgb() : Theme.MODULE_BACKGROUND);
			graphics.fill(width - 1, y, width, y + 9, Theme.ACCENT);
			Renderer.text(graphics, row.name(), width - row.width() - 4, y + 1, Theme.TEXT, hasShadow());
			y += 10;
		}
	}
}
