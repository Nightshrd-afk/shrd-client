package dev.shrd.client.core;

import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.ColorSetting;
import dev.shrd.client.core.setting.DoubleSetting;
import dev.shrd.client.core.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * A module that draws something on top of the game HUD and can be dragged around
 * in the HUD editor (Right Shift &rarr; HUD Editor, or {@code /shrd hud}).
 */
public abstract class HudModule extends Module {

	private double x;
	private double y;

	protected final DoubleSetting scale = add(new DoubleSetting("Scale", "Size of this HUD element.", 1.0, 0.5, 2.0, 2));
	protected final BooleanSetting background = add(new BooleanSetting("Background", "Draw a panel behind this element.", true));
	protected final ColorSetting backgroundColor = add(new ColorSetting("Background Color", "Panel colour.", 0x90101014));
	protected final BooleanSetting shadow = add(new BooleanSetting("Text Shadow", "Draw a drop shadow behind the text.", true));

	protected HudModule(String name, String description, Category category, double defaultX, double defaultY) {
		super(name, description, category);
		this.x = defaultX;
		this.y = defaultY;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	/** Positions are stored as a fraction of the screen so they survive window resizes. */
	public void setPosition(double x, double y) {
		this.x = Math.max(0.0, Math.min(1.0, x));
		this.y = Math.max(0.0, Math.min(1.0, y));
	}

	public float getScale() {
		return scale.get().floatValue();
	}

	public boolean hasBackground() {
		return background.get();
	}

	public int backgroundArgb() {
		return backgroundColor.argb();
	}

	public boolean hasShadow() {
		return shadow.get();
	}

	private int measuredWidth = -1;
	private int measuredHeight = -1;

	private Object cachedValue;
	private boolean cacheValid;
	private int cacheSettings = -1;

	/**
	 * Caches one derived value (an overlay's text, its rows, its width) for a whole tick.
	 * <p>
	 * Overlay text does not change faster than the game ticks, so building it once per tick
	 * instead of on every draw call removes the per-frame string formatting, list sorting
	 * and font measurement that used to happen with every frame. The cache also drops
	 * immediately when any setting changes, so edits made in the ClickGUI show up straight
	 * away even while the game is paused and no ticks are running.
	 *
	 * @param builder produces the value; only called when the cached one is stale
	 */
	@SuppressWarnings("unchecked")
	protected final <T> T cached(Supplier<T> builder) {
		if (!cacheValid || cacheSettings != Setting.getVersion()) {
			cachedValue = builder.get();
			cacheSettings = Setting.getVersion();
			cacheValid = true;
		}

		return (T) cachedValue;
	}

	/**
	 * Width in GUI pixels at the current scale, measured at most once per tick.
	 * <p>
	 * Building an overlay's size means building its text and laying that text out with the
	 * font, and the renderer asks for a module's size several times per frame (panel,
	 * accent bar, HUD editor hit box). With every overlay switched on that is over a
	 * hundred redundant font layouts per frame, so the measurement is cached here and
	 * refreshed once per tick by the module manager.
	 */
	public final int getWidth() {
		if (measuredWidth < 0) {
			measuredWidth = measureWidth();
		}

		return measuredWidth;
	}

	/** Height in GUI pixels at the current scale, measured at most once per tick. */
	public final int getHeight() {
		if (measuredHeight < 0) {
			measuredHeight = measureHeight();
		}

		return measuredHeight;
	}

	/** The real width measurement, called at most once per tick. */
	protected abstract int measureWidth();

	/** The real height measurement, called at most once per tick. */
	protected abstract int measureHeight();

	/**
	 * Drops the cached size and the cached content; the module manager calls this once per
	 * tick, so both are rebuilt at most 20 times a second instead of once per frame.
	 */
	public final void invalidateLayout() {
		measuredWidth = -1;
		measuredHeight = -1;
		cacheValid = false;
	}

	/**
	 * Draws the element. The matrix is already translated so that {@code (0, 0)} is the
	 * element's top-left corner, and scaled by {@link #getScale()}.
	 */
	public abstract void render(GuiGraphicsExtractor graphics, float partialTick);
}
