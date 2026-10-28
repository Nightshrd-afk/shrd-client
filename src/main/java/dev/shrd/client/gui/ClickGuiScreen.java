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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Shrd Client ClickGUI.
 * <p>
 * One draggable panel per category. Left click toggles a module, right click binds a key,
 * and clicking the arrow expands its settings in place — sliders, checkboxes, mode cycles
 * and colour swatches are all editable without leaving the screen.
 */
public class ClickGuiScreen extends Screen {

	private static final int PANEL_WIDTH = 136;
	private static final int HEADER_HEIGHT = 16;
	private static final int ROW_HEIGHT = 13;
	private static final int PADDING = 3;
	private static final int MAX_PANEL_HEIGHT = 260;
	/** Colour swatches offered for the HUD colour settings: the purple identity plus state colours. */
	private static final int[] COLOR_PRESETS = {
		Theme.TEXT, Theme.ACCENT, Theme.ACCENT_ALT, Theme.ACCENT_DEEP,
		0xFF55E07A, 0xFFF5C542, 0xFFF0564A, Theme.TEXT_DISABLED
	};

	private enum Kind {
		HEADER,
		MODULE,
		SETTING
	}

	private record Row(Kind kind, Category category, Module module, Setting<?> setting, int x, int y, int width, int height) {
	}

	private final Map<Category, int[]> panelPositions = new EnumMap<>(Category.class);
	private final Map<Category, Integer> scrollOffsets = new EnumMap<>(Category.class);
	private final Map<Category, Integer> contentHeights = new EnumMap<>(Category.class);
	private final Set<Module> expandedModules = new HashSet<>();
	private final List<Row> rows = new ArrayList<>();

	private String search = "";
	private boolean searchFocused;
	private Module capturingModule;
	private Setting<?> capturingSetting;
	private Setting<?> editingText;
	private String textBuffer = "";
	private SliderDrag sliderDrag;
	private int panelHeightLimit = MAX_PANEL_HEIGHT;
	private Category draggingPanel;
	private int dragOffsetX;
	private int dragOffsetY;

	private record SliderDrag(Module module, Setting<?> setting, int rowX, int rowWidth) {
	}

	public ClickGuiScreen() {
		super(Component.literal("Shrd Client"));
	}

	@Override
	protected void init() {
		panelPositions.clear();
		scrollOffsets.clear();

		// Fit as many panels per row as the window allows and shrink them to the row height,
		// so a small window wraps into a second row instead of stacking panels on top of
		// each other. Panels can still be dragged anywhere afterwards.
		Category[] categories = Category.values();
		int perRow = Math.max(1, (this.width - 16) / (PANEL_WIDTH + 6));
		int rows = (int) Math.ceil(categories.length / (double) perRow);
		int rowStep = Math.max(70, (this.height - 46) / Math.max(1, rows));
		panelHeightLimit = Math.max(70, Math.min(MAX_PANEL_HEIGHT, rowStep - 8));

		int x = 8;
		int y = 32;

		for (int index = 0; index < categories.length; index++) {
			if (index > 0 && index % perRow == 0) {
				x = 8;
				y += rowStep;
			}

			panelPositions.put(categories[index], new int[] {x, y});
			scrollOffsets.put(categories[index], 0);
			x += PANEL_WIDTH + 6;
		}
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

	// ---------------------------------------------------------------- filtering

	private List<Module> filteredModules(Category category) {
		List<Module> modules = new ArrayList<>();
		String needle = search.toLowerCase(Locale.ROOT);

		for (Module module : ShrdClient.get().getModules().getModules(category)) {
			if (needle.isEmpty() || module.getName().toLowerCase(Locale.ROOT).contains(needle)) {
				modules.add(module);
			}
		}

		return modules;
	}

	private boolean isSearching() {
		return !search.isEmpty();
	}

	// ---------------------------------------------------------------- layout

	private int panelHeight(Category category) {
		int height = HEADER_HEIGHT + PADDING;

		for (Module module : filteredModules(category)) {
			height += ROW_HEIGHT + 1;

			if (expandedModules.contains(module)) {
				height += (ROW_HEIGHT + 1) * module.getSettings().size();
			}
		}

		return Math.min(panelHeightLimit, height + PADDING);
	}

	private void buildRows() {
		rows.clear();

		for (Category category : Category.values()) {
			int[] position = panelPositions.get(category);

			if (position == null) {
				continue;
			}

			int panelX = position[0];
			int panelY = position[1];
			int panelHeight = panelHeight(category);
			int contentHeight = 0;

			List<Module> modules = filteredModules(category);
			int cursor = panelY + HEADER_HEIGHT + PADDING - scrollOffsets.getOrDefault(category, 0);

			for (Module module : modules) {
				contentHeight += ROW_HEIGHT + 1;

				if (cursor + ROW_HEIGHT >= panelY + HEADER_HEIGHT && cursor <= panelY + panelHeight) {
					rows.add(new Row(Kind.MODULE, category, module, null, panelX + PADDING, cursor, PANEL_WIDTH - PADDING * 2, ROW_HEIGHT));
				}

				cursor += ROW_HEIGHT + 1;

				if (expandedModules.contains(module)) {
					for (Setting<?> setting : module.getSettings()) {
						contentHeight += ROW_HEIGHT + 1;

						if (cursor + ROW_HEIGHT >= panelY + HEADER_HEIGHT && cursor <= panelY + panelHeight) {
							rows.add(new Row(Kind.SETTING, category, module, setting, panelX + PADDING + 6, cursor, PANEL_WIDTH - PADDING * 2 - 6, ROW_HEIGHT));
						}

						cursor += ROW_HEIGHT + 1;
					}
				}
			}

			contentHeights.put(category, contentHeight);
		}
	}

	private int maxScroll(Category category) {
		int visible = panelHeight(category) - HEADER_HEIGHT - PADDING * 2;
		return Math.max(0, contentHeights.getOrDefault(category, 0) - visible);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		buildRows();

		graphics.fill(0, 0, this.width, this.height, 0xB8070A12);
		drawTopBar(graphics, mouseX, mouseY);

		for (Category category : Category.values()) {
			drawPanel(graphics, category, mouseX, mouseY);
		}

		drawFooter(graphics);

		if (capturingModule != null) {
			drawCaptureOverlay(graphics);
		}
	}

	private void drawTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int barWidth = Math.min(420, this.width - 20);
		int barX = (this.width - barWidth) / 2;
		int barY = 10;

		Renderer.panel(graphics, barX, barY, barWidth, 18, Theme.PANEL_BACKGROUND, Theme.PANEL_OUTLINE);

		Renderer.text(graphics, "shrd", barX + 6, barY + 6, Theme.ACCENT, true);
		Renderer.text(graphics, ShrdClient.VERSION, barX + 6 + Renderer.textWidth("shrd") + 4, barY + 6, Theme.TEXT_DIM, true);

		int searchX = barX + 62;
		int searchWidth = barWidth - 68;
		boolean hovered = mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= barY + 3 && mouseY <= barY + 15;

		Renderer.roundedRect(graphics, searchX, barY + 3, searchWidth, 12, searchFocused ? Theme.SETTING_BACKGROUND : Theme.MODULE_BACKGROUND);
		String shown = search.isEmpty() ? "search modules..." : search;
		Renderer.text(graphics, shown, searchX + 4, barY + 5, search.isEmpty() ? Theme.TEXT_DISABLED : Theme.TEXT, true);

		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = searchX + 5 + Renderer.textWidth(search);
			graphics.fill(caret, barY + 5, caret + 1, barY + 13, Theme.ACCENT);
		} else if (hovered) {
			Renderer.rect(graphics, searchX, barY + 3, searchWidth, 12, Theme.PANEL_OUTLINE);
		}
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Category category, int mouseX, int mouseY) {
		int[] position = panelPositions.get(category);

		if (position == null) {
			return;
		}

		int x = position[0];
		int y = position[1];
		int height = panelHeight(category);
		List<Module> modules = filteredModules(category);

		Renderer.roundedRect(graphics, x, y, PANEL_WIDTH, height, Theme.PANEL_BACKGROUND);
		Renderer.rect(graphics, x, y, PANEL_WIDTH, height, Theme.PANEL_OUTLINE);

		// Header
		Renderer.roundedRect(graphics, x, y, PANEL_WIDTH, HEADER_HEIGHT, Theme.PANEL_HEADER);
		Renderer.text(graphics, category.getDisplayName(), x + 6, y + 5, Theme.ACCENT, true);

		long enabled = modules.stream().filter(Module::isEnabled).count();
		String count = enabled + "/" + modules.size();
		Renderer.text(graphics, count, x + PANEL_WIDTH - Renderer.textWidth(count) - 6, y + 5, Theme.TEXT_DIM, true);

		// Body, clipped to the panel so scrolled content cannot bleed out.
		graphics.enableScissor(x, y + HEADER_HEIGHT, x + PANEL_WIDTH, y + height);

		for (Row row : rows) {
			if (row.category() != category) {
				continue;
			}

			if (row.kind() == Kind.MODULE) {
				drawModuleRow(graphics, row, mouseX, mouseY);
			} else if (row.kind() == Kind.SETTING) {
				drawSettingRow(graphics, row, mouseX, mouseY);
			}
		}

		graphics.disableScissor();

		if (modules.isEmpty()) {
			Renderer.text(graphics, "no matches", x + 8, y + HEADER_HEIGHT + 4, Theme.TEXT_DISABLED, true);
		}

		// Scroll indicator
		int maxScroll = maxScroll(category);

		if (maxScroll > 0) {
			int trackHeight = height - HEADER_HEIGHT - PADDING * 2;
			int barHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maxScroll));
			int offset = scrollOffsets.getOrDefault(category, 0) * (trackHeight - barHeight) / Math.max(1, maxScroll);
			graphics.fill(x + PANEL_WIDTH - 2, y + HEADER_HEIGHT + PADDING + offset, x + PANEL_WIDTH - 1,
				y + HEADER_HEIGHT + PADDING + offset + barHeight, Theme.ACCENT);
		}
	}

	private void drawModuleRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY) {
		Module module = row.module();
		boolean hovered = isInside(mouseX, mouseY, row);
		boolean expanded = expandedModules.contains(module);

		int background = module.isEnabled()
			? (hovered ? Theme.MODULE_ENABLED : Theme.withAlpha(Theme.MODULE_ENABLED, 0xB0))
			: (hovered ? Theme.MODULE_HOVER : Theme.MODULE_BACKGROUND);

		Renderer.roundedRect(graphics, row.x(), row.y(), row.width(), row.height(), background);

		if (module.isEnabled()) {
			graphics.fill(row.x(), row.y(), row.x() + 2, row.y() + row.height(), Theme.ACCENT);
		}

		int textColor = module.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM;

		// Expand arrow on the left, then the name.
		Renderer.text(graphics, expanded ? "v" : ">", row.x() + 3, row.y() + 3, Theme.ACCENT, true);
		Renderer.text(graphics, Renderer.truncate(module.getName(), row.width() - 46), row.x() + 12, row.y() + 3, textColor, true);

		if (module.isHold()) {
			Renderer.text(graphics, "hold", row.x() + row.width() - 34, row.y() + 3, Theme.TEXT_DISABLED, true);
		}

		String bind = module.hasKeybind() ? KeyNames.get(module.getKeybind()) : "";
		int bindWidth = Renderer.textWidth(bind);
		Renderer.text(graphics, bind, row.x() + row.width() - bindWidth - 4, row.y() + 3, Theme.TEXT_ACCENT, true);
	}

	private void drawSettingRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY) {
		boolean hovered = isInside(mouseX, mouseY, row);
		Renderer.roundedRect(graphics, row.x(), row.y(), row.width(), row.height(), hovered ? Theme.MODULE_HOVER : Theme.SETTING_BACKGROUND);

		Setting<?> setting = row.setting();
		String name = Renderer.truncate(setting.getName(), row.width() / 2 + 10);
		Renderer.text(graphics, name, row.x() + 4, row.y() + 3, Theme.TEXT_DIM, true);

		if (setting instanceof BooleanSetting bool) {
			int boxX = row.x() + row.width() - 13;
			Renderer.roundedRect(graphics, boxX, row.y() + 3, 8, 8, bool.get() ? Theme.ACCENT : Theme.SLIDER_TRACK);

			if (bool.get()) {
				Renderer.text(graphics, "x", boxX + 1, row.y() + 3, Theme.ON_ACCENT, false);
			}

			return;
		}

		if (setting instanceof IntSetting intSetting) {
			drawSlider(graphics, row, (intSetting.get() - intSetting.getMin()) / (double) Math.max(1, intSetting.getMax() - intSetting.getMin()),
				String.valueOf(intSetting.get()));
			return;
		}

		if (setting instanceof DoubleSetting doubleSetting) {
			drawSlider(graphics, row,
				(doubleSetting.get() - doubleSetting.getMin()) / Math.max(0.0001, doubleSetting.getMax() - doubleSetting.getMin()),
				doubleSetting.display());
			return;
		}

		String value;

		if (setting instanceof EnumSetting<?> enumSetting) {
			value = enumSetting.get().name();
		} else if (setting instanceof KeybindSetting keybind) {
			value = keybind.isBound() ? KeyNames.get(keybind.get()) : "NONE";
		} else if (setting instanceof StringSetting stringSetting) {
			value = editingText == setting ? textBuffer + "_" : Renderer.truncate(stringSetting.get(), 60);
		} else if (setting instanceof ColorSetting colorSetting) {
			value = colorSetting.hex();
			int previewX = row.x() + row.width() - 12;
			Renderer.roundedRect(graphics, previewX, row.y() + 3, 8, 8, colorSetting.argb());
		} else {
			value = "?";
		}

		int valueWidth = Renderer.textWidth(value);
		int limit = row.width() - 8 - (setting instanceof ColorSetting ? 12 : 0);
		Renderer.text(graphics, Renderer.truncate(value, limit), row.x() + row.width() - valueWidth - 4 - (setting instanceof ColorSetting ? 12 : 0),
			row.y() + 3, Theme.TEXT_ACCENT, true);
	}

	private void drawSlider(GuiGraphicsExtractor graphics, Row row, double ratio, String value) {
		int barX = row.x() + 4;
		int barY = row.y() + row.height() - 3;
		int barWidth = row.width() - 8;

		Renderer.bar(graphics, barX, barY, barWidth, 2, ratio, Theme.SLIDER_TRACK, Theme.ACCENT);

		int knobX = barX + (int) Math.round(ratio * barWidth);
		graphics.fill(knobX - 1, barY - 1, knobX + 1, barY + 3, Theme.TEXT);

		int valueWidth = Renderer.textWidth(value);
		Renderer.text(graphics, value, row.x() + row.width() - valueWidth - 4, row.y() + 3, Theme.TEXT_ACCENT, true);
	}

	private void drawFooter(GuiGraphicsExtractor graphics) {
		String hint = "LMB toggle   RMB bind   arrow expand   /shrd for commands   " + ShrdClient.get().countEnabled() + " enabled";
		Renderer.centeredText(graphics, hint, this.width / 2, this.height - 14, Theme.TEXT_DISABLED);
	}

	private void drawCaptureOverlay(GuiGraphicsExtractor graphics) {
		int boxWidth = 240;
		int boxHeight = 44;
		int x = (this.width - boxWidth) / 2;
		int y = (this.height - boxHeight) / 2;

		Renderer.panel(graphics, x, y, boxWidth, boxHeight, Theme.WINDOW_BACKGROUND, Theme.ACCENT);
		Renderer.centeredText(graphics, "bind " + capturingModule.getName(), this.width / 2, y + 9, Theme.TEXT);
		Renderer.centeredText(graphics, "press a key or mouse button   (ESC clears)", this.width / 2, y + 25, Theme.TEXT_DIM);

		graphics.fill(0, 0, this.width, y, Theme.SHADOW);
		graphics.fill(0, y + boxHeight, this.width, this.height, Theme.SHADOW);
	}

	private boolean isInside(int mouseX, int mouseY, Row row) {
		return mouseX >= row.x() && mouseX <= row.x() + row.width() && mouseY >= row.y() && mouseY <= row.y() + row.height();
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

		// Search box
		int barWidth = Math.min(420, this.width - 20);
		int barX = (this.width - barWidth) / 2;
		int searchX = barX + 62;
		int searchWidth = barWidth - 68;

		if (mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= 13 && mouseY <= 25) {
			searchFocused = true;
			return true;
		}

		searchFocused = false;

		// Panel dragging by header
		for (Category category : Category.values()) {
			int[] position = panelPositions.get(category);

			if (position == null) {
				continue;
			}

			if (mouseX >= position[0] && mouseX <= position[0] + PANEL_WIDTH && mouseY >= position[1] && mouseY <= position[1] + HEADER_HEIGHT) {
				draggingPanel = category;
				dragOffsetX = mouseX - position[0];
				dragOffsetY = mouseY - position[1];
				return true;
			}
		}

		buildRows();

		for (Row row : rows) {
			if (!isInside(mouseX, mouseY, row)) {
				continue;
			}

			if (row.kind() == Kind.MODULE) {
				Module module = row.module();

				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					capturingModule = module;
				} else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
					toggleExpanded(module);
				} else if (mouseX - row.x() < 12) {
					toggleExpanded(module);
				} else {
					module.toggle();
				}

				return true;
			}

			handleSettingClick(row, mouseX, (int) event.y(), button);
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	/** Applies a captured keyboard or mouse code to whatever was waiting for one. */
	private void applyCapture(int code) {
		if (capturingSetting instanceof KeybindSetting keybind) {
			keybind.set(code);
		} else if (capturingModule != null) {
			capturingModule.setKeybind(code);
		}

		capturingModule = null;
		capturingSetting = null;
	}

	private void toggleExpanded(Module module) {
		if (!expandedModules.remove(module)) {
			expandedModules.add(module);
		}
	}

	private void handleSettingClick(Row row, int mouseX, int mouseY, int button) {
		Setting<?> setting = row.setting();
		double ratio = Math.max(0.0, Math.min(1.0, (mouseX - (row.x() + 4)) / (double) Math.max(1, row.width() - 8)));

		if (setting instanceof BooleanSetting bool) {
			bool.toggle();
			return;
		}

		if (setting instanceof IntSetting intSetting) {
			sliderDrag = new SliderDrag(row.module(), setting, row.x(), row.width());
			intSetting.setClamped((int) Math.round(intSetting.getMin() + ratio * (intSetting.getMax() - intSetting.getMin())));
			return;
		}

		if (setting instanceof DoubleSetting doubleSetting) {
			sliderDrag = new SliderDrag(row.module(), setting, row.x(), row.width());
			double raw = doubleSetting.getMin() + ratio * (doubleSetting.getMax() - doubleSetting.getMin());
			doubleSetting.setClamped(round(raw, doubleSetting.getDecimals()));
			return;
		}

		if (setting instanceof EnumSetting<?> enumSetting) {
			enumSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
			return;
		}

		if (setting instanceof KeybindSetting keybind) {
			// Click starts capture on the next key press; right click clears it.
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
		draggingPanel = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingPanel != null) {
			int[] position = panelPositions.get(draggingPanel);

			if (position != null) {
				position[0] = (int) Math.max(0, Math.min(this.width - PANEL_WIDTH, event.x() - dragOffsetX));
				position[1] = (int) Math.max(0, Math.min(this.height - HEADER_HEIGHT, event.y() - dragOffsetY));
			}

			return true;
		}

		if (sliderDrag != null) {
			double ratio = Math.max(0.0, Math.min(1.0, (event.x() - (sliderDrag.rowX() + 4)) / (double) Math.max(1, sliderDrag.rowWidth() - 8)));

			if (sliderDrag.setting() instanceof IntSetting intSetting) {
				intSetting.setClamped((int) Math.round(intSetting.getMin() + ratio * (intSetting.getMax() - intSetting.getMin())));
			} else if (sliderDrag.setting() instanceof DoubleSetting doubleSetting) {
				doubleSetting.setClamped(round(doubleSetting.getMin() + ratio * (doubleSetting.getMax() - doubleSetting.getMin()), doubleSetting.getDecimals()));
			}

			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (Category category : Category.values()) {
			int[] position = panelPositions.get(category);

			if (position == null) {
				continue;
			}

			boolean inside = mouseX >= position[0] && mouseX <= position[0] + PANEL_WIDTH
				&& mouseY >= position[1] && mouseY <= position[1] + panelHeight(category);

			if (inside) {
				int current = scrollOffsets.getOrDefault(category, 0);
				int next = (int) Math.max(0, Math.min(maxScroll(category), current - scrollY * 12));
				scrollOffsets.put(category, next);
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

		if (searchFocused && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			return true;
		}

		if (key == GLFW.GLFW_KEY_ESCAPE && !isSearching()) {
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
			return true;
		}

		return super.charTyped(event);
	}

	/** Small helper so the HUD editor can be opened from inside the GUI. */
	public static void openHudEditor() {
		ShrdClient.get().openHudEditor();
	}

	/** Used by the command layer to report what the GUI is showing. */
	public String describeState() {
		return String.format(Locale.ROOT, "gui: %d enabled, search '%s'", PlayerUtils.mc() == null ? 0 : ShrdClient.get().countEnabled(), search);
	}
}
