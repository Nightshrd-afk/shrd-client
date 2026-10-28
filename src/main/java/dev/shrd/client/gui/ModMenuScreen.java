package dev.shrd.client.gui;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.ColorSetting;
import dev.shrd.client.core.setting.DoubleSetting;
import dev.shrd.client.core.setting.EnumSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.core.setting.KeybindSetting;
import dev.shrd.client.core.setting.Setting;
import dev.shrd.client.core.setting.StringSetting;
import dev.shrd.client.util.KeyNames;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.Renderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Shrd Client mod menu, in the shape Feather Client popularised: one window over the whole
 * game, a category sidebar down the left, a search field, and a list of modules with a toggle
 * switch on each row.
 * <p>
 * This is what <b>Right Shift</b> opens. Compared with the draggable-panel ClickGUI it trades
 * panels you can arrange freely for a single predictable list: every module has room for its
 * description, its bind and its settings, and nothing has to be dragged out of the way to be
 * readable. The panel layout is still there if you prefer it — {@code /shrd panels}.
 * <p>
 * Left click a row to toggle, left click its chevron (or middle click) to expand settings,
 * right click to bind a key, and the search field filters across every category at once.
 */
public class ModMenuScreen extends Screen {

	private static final int MARGIN = 16;
	private static final int SIDEBAR_WIDTH = 136;
	private static final int HEADER_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 18;
	private static final int ROW_HEIGHT = 31;
	private static final int SETTING_ROW_HEIGHT = 21;
	private static final int SIDEBAR_ROW_HEIGHT = 17;
	private static final int SWITCH_WIDTH = 18;
	private static final int SWITCH_HEIGHT = 10;
	private static final int PAD = 8;

	/** Colour swatches offered for the HUD colour settings: the purple identity plus state colours. */
	private static final int[] COLOR_PRESETS = {
		Theme.TEXT, Theme.ACCENT, Theme.ACCENT_ALT, Theme.ACCENT_DEEP,
		Theme.MODULE_ENABLED, Theme.SETTING_BACKGROUND, Theme.WINDOW_BACKGROUND, Theme.TEXT_DISABLED,
		0xFF55E07A, 0xFFF5C542, 0xFFF0564A
	};

	private enum Kind {
		MODULE,
		SETTING
	}

	private record Row(Kind kind, Module module, Setting<?> setting, int x, int y, int width, int height) {
	}

	/** A clickable entry in the sidebar: a category filter or one of the window actions. */
	private record Entry(String label, Category category, boolean action, int y, int height) {
	}

	private record SliderDrag(Setting<?> setting, int rowX, int rowWidth) {
	}

	/** Null means "All": the list shows every module, cheap to reach from any category. */
	private Category selected;
	private final Set<Module> expanded = new HashSet<>();
	private final List<Row> rows = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();

	private String search = "";
	private boolean searchFocused;
	private Module capturingModule;
	private Setting<?> capturingSetting;
	private Setting<?> editingText;
	private String textBuffer = "";
	private SliderDrag sliderDrag;
	private int scroll;
	private int contentHeight;

	public ModMenuScreen() {
		super(Component.literal("Shrd Client"));
	}

	// ---------------------------------------------------------------- geometry

	private int windowX() {
		return MARGIN;
	}

	private int windowY() {
		return MARGIN;
	}

	private int windowWidth() {
		return Math.max(260, this.width - MARGIN * 2);
	}

	private int windowHeight() {
		return Math.max(160, this.height - MARGIN * 2);
	}

	private int contentX() {
		return windowX() + SIDEBAR_WIDTH + PAD;
	}

	private int contentWidth() {
		return windowWidth() - SIDEBAR_WIDTH - PAD * 2;
	}

	private int contentTop() {
		return windowY() + HEADER_HEIGHT;
	}

	private int contentBottom() {
		return windowY() + windowHeight() - FOOTER_HEIGHT - PAD;
	}

	private int searchX() {
		return contentX() + contentWidth() - searchWidth();
	}

	private int searchWidth() {
		return Math.min(210, Math.max(120, contentWidth() / 3));
	}

	@Override
	protected void init() {
		buildSidebar();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ShrdClient.get().getConfig().save();
		super.onClose();
	}

	// ---------------------------------------------------------------- sidebar

	private void buildSidebar() {
		entries.clear();

		int y = windowY() + 32;
		entries.add(new Entry("All", null, false, y, SIDEBAR_ROW_HEIGHT));

		for (Category category : Category.values()) {
			y += SIDEBAR_ROW_HEIGHT + 1;
			entries.add(new Entry(category.getDisplayName(), category, false, y, SIDEBAR_ROW_HEIGHT));
		}

		// Window actions sit at the bottom of the sidebar, like Feather's footer buttons. The
		// stack is placed by its total height so the last entry cannot hang past the window edge.
		String[] actions = {"HUD Editor", "Panels", "Save Config"};
		int stackHeight = actions.length * SIDEBAR_ROW_HEIGHT + (actions.length - 1);
		int footerY = windowY() + windowHeight() - PAD - stackHeight;

		for (String action : actions) {
			entries.add(new Entry(action, null, true, footerY, SIDEBAR_ROW_HEIGHT));
			footerY += SIDEBAR_ROW_HEIGHT + 1;
		}
	}

	private boolean isActive(Entry entry) {
		if (entry.action()) {
			return false;
		}

		return entry.category() == selected;
	}

	// ---------------------------------------------------------------- content

	private List<Module> visibleModules() {
		List<Module> result = new ArrayList<>();
		String needle = search.toLowerCase(Locale.ROOT);

		for (Module module : ShrdClient.get().getModules().getModules()) {
			if (selected != null && module.getCategory() != selected) {
				continue;
			}

			if (!needle.isEmpty()) {
				boolean matches = module.getName().toLowerCase(Locale.ROOT).contains(needle)
					|| module.getDescription().toLowerCase(Locale.ROOT).contains(needle);

				if (!matches) {
					continue;
				}
			}

			result.add(module);
		}

		return result;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - (contentBottom() - contentTop()));
	}

	private void buildRows() {
		rows.clear();
		contentHeight = 0;

		int cursor = contentTop() - scroll;

		for (Module module : visibleModules()) {
			int height = ROW_HEIGHT;

			if (expanded.contains(module)) {
				height += module.getSettings().size() * SETTING_ROW_HEIGHT;
			}

			contentHeight += height + 2;

			if (cursor + height >= contentTop() && cursor <= contentBottom()) {
				rows.add(new Row(Kind.MODULE, module, null, contentX(), cursor, contentWidth(), ROW_HEIGHT));

				if (expanded.contains(module)) {
					int settingY = cursor + ROW_HEIGHT;

					for (Setting<?> setting : module.getSettings()) {
						rows.add(new Row(Kind.SETTING, module, setting, contentX() + PAD, settingY,
							contentWidth() - PAD * 2, SETTING_ROW_HEIGHT));
						settingY += SETTING_ROW_HEIGHT;
					}
				}
			}

			cursor += height + 2;
		}
	}

	/**
	 * Screen-space bounds of a module's row as {@code {x, y, width, height}}, or null when that
	 * module is not currently visible.
	 * <p>
	 * Hit testing lives here rather than being duplicated elsewhere: the automated UI test drives
	 * this screen without any OS input by asking a row where it is and clicking the middle of it.
	 */
	public int[] rowBounds(Module module) {
		if (rows.isEmpty()) {
			buildRows();
		}

		for (Row row : rows) {
			if (row.kind() == Kind.MODULE && row.module() == module) {
				return new int[] {row.x(), row.y(), row.width(), row.height()};
			}
		}

		return null;
	}

	private Row rowAt(int mouseX, int mouseY) {
		for (Row row : rows) {
			if (mouseX >= row.x() && mouseX <= row.x() + row.width() && mouseY >= row.y() && mouseY <= row.y() + row.height()) {
				return row;
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (entries.isEmpty()) {
			buildSidebar();
		}

		buildRows();

		graphics.fill(0, 0, this.width, this.height, 0xB0050910);

		Renderer.roundedRect(graphics, windowX(), windowY(), windowWidth(), windowHeight(), Theme.WINDOW_BACKGROUND);
		Renderer.rect(graphics, windowX(), windowY(), windowWidth(), windowHeight(), Theme.PANEL_OUTLINE);

		drawSidebar(graphics, mouseX, mouseY);
		drawHeader(graphics, mouseX, mouseY);
		drawList(graphics, mouseX, mouseY);
		drawFooter(graphics);

		if (capturingModule != null) {
			drawCaptureOverlay(graphics);
		}
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Renderer.roundedRect(graphics, windowX(), windowY(), SIDEBAR_WIDTH, windowHeight(), Theme.PANEL_BACKGROUND);

		// Brand block: the watermark's wordmark, reused as the menu title. Text is measured
		// rather than assumed: the font in a development client is wider than the shipped one,
		// so anything placed by character count overruns the sidebar there.
		int brandLimit = SIDEBAR_WIDTH - 18;
		String brand = Renderer.truncate("shrd " + ShrdClient.VERSION, brandLimit);
		Renderer.text(graphics, brand, windowX() + 9, windowY() + 9, Theme.ACCENT, true);
		graphics.fill(windowX() + 8, windowY() + 22, windowX() + SIDEBAR_WIDTH - 8, windowY() + 23, Theme.PANEL_OUTLINE);

		for (Entry entry : entries) {
			boolean active = isActive(entry);
			boolean hovered = mouseX >= windowX() + 4 && mouseX <= windowX() + SIDEBAR_WIDTH - 4
				&& mouseY >= entry.y() && mouseY <= entry.y() + entry.height();

			int width = SIDEBAR_WIDTH - 8;
			int background = active ? Theme.MODULE_ENABLED : hovered ? Theme.MODULE_HOVER : 0;

			if (background != 0) {
				Renderer.roundedRect(graphics, windowX() + 4, entry.y(), width, entry.height(), background);
			}

			if (active) {
				graphics.fill(windowX() + 4, entry.y(), windowX() + 6, entry.y() + entry.height(), Theme.ACCENT);
			}

			int labelColor = active ? Theme.TEXT : (hovered ? Theme.TEXT : Theme.TEXT_DIM);
			int labelLimit = SIDEBAR_WIDTH - 22;

			// Right-aligned enabled/total counter per category, with the label truncated to
			// whatever room is left so a long name and a wide counter cannot collide.
			if (!entry.action() && entry.category() != null) {
				int total = ShrdClient.get().getModules().getModules(entry.category()).size();
				int on = 0;

				for (Module module : ShrdClient.get().getModules().getModules(entry.category())) {
					if (module.isEnabled()) {
						on++;
					}
				}

				String counter = on + "/" + total;
				int counterWidth = Renderer.textWidth(counter);
				labelLimit = SIDEBAR_WIDTH - 22 - counterWidth;
				Renderer.text(graphics, counter, windowX() + SIDEBAR_WIDTH - 10 - counterWidth,
					entry.y() + 5, active ? Theme.TEXT_ACCENT : Theme.TEXT_DISABLED, true);
			}

			Renderer.text(graphics, Renderer.truncate(entry.label(), labelLimit), windowX() + 11, entry.y() + 5,
				labelColor, true);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String title = selected == null ? "All Modules" : selected.getDisplayName();
		int titleWidth = Renderer.textWidth(title);
		Renderer.text(graphics, title, contentX(), windowY() + 8, Theme.TEXT, true);

		// The detail line only gets the space between the title and the search field, so it is
		// truncated to fit instead of running underneath the field.
		int detailX = contentX() + titleWidth + 8;
		String detail = ShrdClient.get().countEnabled() + " of " + ShrdClient.get().getModules().getModules().size()
			+ " enabled  •  " + PlayerUtils.fps() + " FPS";
		Renderer.text(graphics, Renderer.truncate(detail, Math.max(0, searchX() - 8 - detailX)), detailX,
			windowY() + 8, Theme.TEXT_DISABLED, true);

		int boxY = windowY() + 6;
		int boxHeight = 15;
		int boxWidth = searchWidth();
		int boxX = searchX();

		Renderer.roundedRect(graphics, boxX, boxY, boxWidth, boxHeight, Theme.SETTING_BACKGROUND);
		Renderer.rect(graphics, boxX, boxY, boxWidth, boxHeight, searchFocused ? Theme.ACCENT : Theme.PANEL_OUTLINE);

		String shown = search.isEmpty() ? "Search modules..." : search;
		Renderer.text(graphics, Renderer.truncate(shown, boxWidth - 8), boxX + 5, boxY + 4,
			search.isEmpty() ? Theme.TEXT_DISABLED : Theme.TEXT, true);

		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = boxX + 5 + Renderer.textWidth(search);
			graphics.fill(Math.min(caret, boxX + boxWidth - 3), boxY + 4, Math.min(caret, boxX + boxWidth - 3) + 1, boxY + 12, Theme.ACCENT);
		}

		graphics.fill(windowX() + SIDEBAR_WIDTH, windowY() + HEADER_HEIGHT - 3, windowX() + windowWidth() - PAD,
			windowY() + HEADER_HEIGHT - 2, Theme.PANEL_OUTLINE);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Module> modules = visibleModules();

		if (modules.isEmpty()) {
			Renderer.text(graphics, "nothing matches \"" + search + "\"", contentX() + 4, contentTop() + 8, Theme.TEXT_DISABLED, true);
			return;
		}

		graphics.enableScissor(contentX(), contentTop(), windowX() + windowWidth() - PAD, contentBottom());

		for (Row row : rows) {
			if (row.kind() == Kind.MODULE) {
				drawModuleRow(graphics, row, mouseX, mouseY);
			} else {
				drawSettingRow(graphics, row, mouseX, mouseY);
			}
		}

		graphics.disableScissor();

		int maxScroll = maxScroll();

		if (maxScroll > 0) {
			int track = contentBottom() - contentTop() - 4;
			int bar = Math.max(14, track * track / (track + maxScroll));
			int offset = scroll * (track - bar) / Math.max(1, maxScroll);
			int barX = windowX() + windowWidth() - 6;
			Renderer.roundedRect(graphics, barX, contentTop() + 2 + offset, 2, bar, Theme.ACCENT);
		}
	}

	private void drawModuleRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY) {
		Module module = row.module();
		boolean hovered = mouseX >= row.x() && mouseX <= row.x() + row.width()
			&& mouseY >= row.y() && mouseY <= row.y() + row.height();
		boolean open = expanded.contains(module);

		int background = module.isEnabled()
			? (hovered ? Theme.MODULE_ENABLED : Theme.withAlpha(Theme.MODULE_ENABLED, 0xA8))
			: (hovered ? Theme.MODULE_HOVER : Theme.MODULE_BACKGROUND);

		Renderer.roundedRect(graphics, row.x(), row.y(), row.width(), ROW_HEIGHT, background);

		if (module.isEnabled()) {
			graphics.fill(row.x(), row.y() + 3, row.x() + 2, row.y() + ROW_HEIGHT - 3, Theme.ACCENT);
		}

		int textX = row.x() + PAD + 6;
		Renderer.text(graphics, open ? "v" : ">", row.x() + PAD - 1, row.y() + 7, Theme.ACCENT, true);
		Renderer.text(graphics, module.getName(), textX, row.y() + 5,
			module.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM, true);

		if (module.isHold()) {
			int holdX = textX + Renderer.textWidth(module.getName()) + 6;
			Renderer.text(graphics, "hold", holdX, row.y() + 5, Theme.TEXT_DISABLED, true);
		}

		Renderer.text(graphics, Renderer.truncate(module.getDescription(), row.width() - 160), textX, row.y() + 17,
			Theme.TEXT_DISABLED, true);

		// Bind chip, then the switch, both right aligned. The chip is capped so a long bind name
		// cannot grow leftwards into the module name and description.
		int switchX = row.x() + row.width() - PAD - SWITCH_WIDTH;
		int switchY = row.y() + (ROW_HEIGHT - SWITCH_HEIGHT) / 2;

		if (module.hasKeybind()) {
			String bind = Renderer.truncate(KeyNames.get(module.getKeybind()), 66);
			int chipWidth = Renderer.textWidth(bind) + 8;
			int chipX = switchX - 8 - chipWidth;
			Renderer.roundedRect(graphics, chipX, row.y() + 10, chipWidth, 11, Theme.PANEL_HEADER);
			Renderer.text(graphics, bind, chipX + 4, row.y() + 12, Theme.TEXT_ACCENT, true);
		}

		drawSwitch(graphics, switchX, switchY, module.isEnabled());
	}

	private void drawSwitch(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
		Renderer.roundedRect(graphics, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, on ? Theme.ACCENT : Theme.SLIDER_TRACK);
		int knob = SWITCH_HEIGHT - 2;
		int knobX = on ? x + SWITCH_WIDTH - knob - 1 : x + 1;
		Renderer.roundedRect(graphics, knobX, y + 1, knob, knob, on ? Theme.ON_ACCENT : Theme.TEXT_DIM);
	}

	private void drawSettingRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY) {
		boolean hovered = mouseX >= row.x() && mouseX <= row.x() + row.width()
			&& mouseY >= row.y() && mouseY <= row.y() + row.height();

		Renderer.roundedRect(graphics, row.x(), row.y(), row.width(), row.height(), hovered ? Theme.MODULE_HOVER : Theme.SETTING_BACKGROUND);

		Setting<?> setting = row.setting();
		Renderer.text(graphics, Renderer.truncate(setting.getName(), row.width() / 2 + 20), row.x() + 5, row.y() + 6,
			Theme.TEXT_DIM, true);

		if (setting instanceof BooleanSetting bool) {
			drawSwitch(graphics, row.x() + row.width() - 5 - SWITCH_WIDTH, row.y() + (row.height() - SWITCH_HEIGHT) / 2, bool.get());
			return;
		}

		if (setting instanceof IntSetting intSetting) {
			drawSlider(graphics, row, (intSetting.get() - intSetting.getMin())
				/ (double) Math.max(1, intSetting.getMax() - intSetting.getMin()), String.valueOf(intSetting.get()));
			return;
		}

		if (setting instanceof DoubleSetting doubleSetting) {
			drawSlider(graphics, row, (doubleSetting.get() - doubleSetting.getMin())
				/ Math.max(0.0001, doubleSetting.getMax() - doubleSetting.getMin()), doubleSetting.display());
			return;
		}

		String value;
		int trail = 0;

		if (setting instanceof EnumSetting<?> enumSetting) {
			value = enumSetting.get().name();
		} else if (setting instanceof KeybindSetting keybind) {
			value = keybind.isBound() ? KeyNames.get(keybind.get()) : "NONE";
		} else if (setting instanceof ColorSetting colorSetting) {
			value = colorSetting.hex();
			trail = 12;
		} else if (setting instanceof StringSetting stringSetting) {
			value = editingText == setting ? textBuffer + "_" : Renderer.truncate(stringSetting.get(), 60);
		} else {
			value = "?";
		}

		String shown = Renderer.truncate(value, row.width() / 2);
		int valueX = row.x() + row.width() - 5 - trail - Renderer.textWidth(shown);
		Renderer.text(graphics, shown, Math.max(valueX, row.x() + row.width() / 2), row.y() + 6, Theme.TEXT_ACCENT, true);

		if (setting instanceof ColorSetting colorSetting) {
			Renderer.roundedRect(graphics, row.x() + row.width() - 13, row.y() + 6, 9, 9, colorSetting.argb());
		}
	}

	private void drawSlider(GuiGraphicsExtractor graphics, Row row, double ratio, String value) {
		int barX = row.x() + 5;
		int barY = row.y() + row.height() - 4;
		int barWidth = row.width() - 10;

		Renderer.bar(graphics, barX, barY, barWidth, 2, ratio, Theme.SLIDER_TRACK, Theme.ACCENT);

		int knobX = barX + (int) Math.round(ratio * barWidth);
		graphics.fill(knobX - 1, barY - 2, knobX + 1, barY + 4, Theme.TEXT);

		String shown = Renderer.truncate(value, 60);
		Renderer.text(graphics, shown, row.x() + row.width() - 5 - Renderer.textWidth(shown), row.y() + 6, Theme.TEXT_ACCENT, true);
	}

	/**
	 * One centred hint line. It used to be two strings pinned to opposite ends, which collided in
	 * the middle on a narrow window; a single line cannot overlap itself.
	 */
	private void drawFooter(GuiGraphicsExtractor graphics) {
		// Kept deliberately short: the font in a development client is about twice the width of the
		// shipped one, and a hint that wraps to an ellipsis there reads as a bug.
		String hint = Renderer.truncate("LMB toggle  •  MMB settings  •  RMB bind  •  ESC close", contentWidth());
		int x = contentX() + (contentWidth() - Renderer.textWidth(hint)) / 2;
		Renderer.text(graphics, hint, x, windowY() + windowHeight() - 13, Theme.TEXT_DISABLED, true);
	}

	private void drawCaptureOverlay(GuiGraphicsExtractor graphics) {
		int boxWidth = 250;
		int boxHeight = 46;
		int x = (this.width - boxWidth) / 2;
		int y = (this.height - boxHeight) / 2;

		Renderer.panel(graphics, x, y, boxWidth, boxHeight, Theme.WINDOW_BACKGROUND, Theme.ACCENT);
		Renderer.centeredText(graphics, "bind " + capturingModule.getName(), this.width / 2, y + 10, Theme.TEXT);
		Renderer.centeredText(graphics, "press a key or mouse button   (ESC clears)", this.width / 2, y + 26, Theme.TEXT_DIM);

		graphics.fill(0, 0, this.width, y, Theme.SHADOW);
		graphics.fill(0, y + boxHeight, this.width, this.height, Theme.SHADOW);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int button = event.button();

		if (capturingModule != null) {
			applyCapture(KeyNames.MOUSE_OFFSET + button);
			return true;
		}

		// Sidebar
		if (mouseX >= windowX() && mouseX <= windowX() + SIDEBAR_WIDTH) {
			for (Entry entry : entries) {
				if (mouseY < entry.y() || mouseY > entry.y() + entry.height()) {
					continue;
				}

				if (entry.action()) {
					runAction(entry.label());
				} else {
					selected = entry.category();
					scroll = 0;
				}

				return true;
			}

			return true;
		}

		// Search field
		if (mouseX >= searchX() && mouseX <= searchX() + searchWidth()
			&& mouseY >= windowY() + 6 && mouseY <= windowY() + 21) {
			searchFocused = true;
			return true;
		}

		searchFocused = false;

		Row row = rowAt(mouseX, mouseY);

		if (row == null) {
			return super.mouseClicked(event, doubleClick);
		}

		if (row.kind() == Kind.MODULE) {
			Module module = row.module();

			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				capturingModule = module;
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || mouseX - row.x() < PAD + 8) {
				toggleExpanded(module);
			} else {
				module.toggle();
			}

			return true;
		}

		handleSettingClick(row, mouseX, button);
		return true;
	}

	private void runAction(String label) {
		switch (label) {
			case "HUD Editor" -> ShrdClient.get().openHudEditor();
			case "Panels" -> ShrdClient.get().openClassicGui();
			case "Save Config" -> {
				ShrdClient.get().getConfig().save();
				dev.shrd.client.util.Chat.info("config saved");
			}
			default -> {
			}
		}
	}

	private void toggleExpanded(Module module) {
		if (!expanded.remove(module)) {
			expanded.add(module);
		}
	}

	private void applyCapture(int code) {
		if (capturingSetting instanceof KeybindSetting keybind) {
			keybind.set(code);
		} else if (capturingModule != null) {
			capturingModule.setKeybind(code);
		}

		capturingModule = null;
		capturingSetting = null;
	}

	private void handleSettingClick(Row row, int mouseX, int button) {
		Setting<?> setting = row.setting();
		double ratio = Math.max(0.0, Math.min(1.0, (mouseX - (row.x() + 5)) / (double) Math.max(1, row.width() - 10)));

		if (setting instanceof BooleanSetting bool) {
			bool.toggle();
			return;
		}

		if (setting instanceof IntSetting intSetting) {
			sliderDrag = new SliderDrag(setting, row.x(), row.width());
			intSetting.setClamped((int) Math.round(intSetting.getMin() + ratio * (intSetting.getMax() - intSetting.getMin())));
			return;
		}

		if (setting instanceof DoubleSetting doubleSetting) {
			sliderDrag = new SliderDrag(setting, row.x(), row.width());
			double raw = doubleSetting.getMin() + ratio * (doubleSetting.getMax() - doubleSetting.getMin());
			doubleSetting.setClamped(round(raw, doubleSetting.getDecimals()));
			return;
		}

		if (setting instanceof EnumSetting<?> enumSetting) {
			enumSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
			return;
		}

		if (setting instanceof KeybindSetting keybind) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				keybind.set(KeybindSetting.NONE);
			} else {
				capturingModule = row.module();
				capturingSetting = keybind;
			}

			return;
		}

		if (setting instanceof StringSetting stringSetting) {
			editingText = setting;
			textBuffer = stringSetting.get();
			return;
		}

		if (setting instanceof ColorSetting colorSetting) {
			int index = 0;

			for (int i = 0; i < COLOR_PRESETS.length; i++) {
				if (COLOR_PRESETS[i] == colorSetting.argb()) {
					index = i;
					break;
				}
			}

			int next = (index + (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? COLOR_PRESETS.length - 1 : 1)) % COLOR_PRESETS.length;
			colorSetting.set(COLOR_PRESETS[next]);
		}
	}

	private static double round(double value, int decimals) {
		double factor = Math.pow(10, decimals);
		return Math.round(value * factor) / factor;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		sliderDrag = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (sliderDrag == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		double ratio = Math.max(0.0, Math.min(1.0, (event.x() - (sliderDrag.rowX() + 5)) / (double) Math.max(1, sliderDrag.rowWidth() - 10)));

		if (sliderDrag.setting() instanceof IntSetting intSetting) {
			intSetting.setClamped((int) Math.round(intSetting.getMin() + ratio * (intSetting.getMax() - intSetting.getMin())));
		} else if (sliderDrag.setting() instanceof DoubleSetting doubleSetting) {
			doubleSetting.setClamped(round(doubleSetting.getMin() + ratio * (doubleSetting.getMax() - doubleSetting.getMin()),
				doubleSetting.getDecimals()));
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < contentX() || mouseY < contentTop() || mouseY > contentBottom()) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		buildRows();
		scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - scrollY * 16));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (capturingModule != null) {
			applyCapture(key == GLFW.GLFW_KEY_ESCAPE ? KeybindSetting.NONE : key);
			return true;
		}

		if (editingText instanceof StringSetting stringSetting) {
			if (key == GLFW.GLFW_KEY_BACKSPACE && !textBuffer.isEmpty()) {
				textBuffer = textBuffer.substring(0, textBuffer.length() - 1);
			} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				stringSetting.set(textBuffer);
				editingText = null;
			} else if (key == GLFW.GLFW_KEY_ESCAPE) {
				editingText = null;
			}

			return true;
		}

		if (searchFocused) {
			if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				scroll = 0;
				return true;
			}

			if (key == GLFW.GLFW_KEY_ESCAPE) {
				searchFocused = false;
				return true;
			}
		}

		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String typed = event.codepointAsString();

		if (editingText instanceof StringSetting) {
			textBuffer += typed;
			return true;
		}

		if (searchFocused && event.isAllowedChatCharacter()) {
			search += typed;
			scroll = 0;
			return true;
		}

		return super.charTyped(event);
	}
}
