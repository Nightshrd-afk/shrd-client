package dev.shrd.client.modules.client;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.util.Chat;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Switches on the vanilla settings that actually move the frame rate.
 * <p>
 * Shrd Client's own overlays cost a fraction of a frame — the frame rate is decided by
 * graphics settings, and the two most common reasons a fast machine still shows a low
 * number are VSync locking to the monitor's refresh rate and Minecraft's own frame limit
 * (120 by default, which is below the 200+ this mode targets).
 * <p>
 * Every option this module changes is remembered the moment it is first touched and put
 * back exactly as it was when the module is switched off, so it is safe to toggle on and
 * off in the middle of a session. Settings are applied when the module is enabled and
 * whenever one of its own switches is flipped, never continuously, so manual tweaks you
 * make afterwards are left alone until you toggle the module again.
 */
public class PerformanceModule extends Module {

	private final BooleanSetting visualDowngrade = setting("Visual Downgrade",
		"Fast graphics: no clouds, minimal particles, no smooth lighting, no entity shadows, no biome blend, "
			+ "fastest leaves, no chunk fade-in, no vignette and chunk updates that never stall a frame.", true);

	private final BooleanSetting uncapFramerate = setting("Remove Frame Limit",
		"Turn VSync off and raise Minecraft's frame limit to its maximum. VSync alone caps most "
			+ "players at their monitor's refresh rate, usually 60.", true);

	private final BooleanSetting noBobbing = setting("No View Bobbing",
		"Removes the camera bob, which is pure extra rendering for a cosmetic effect.", true);

	/**
	 * The overlays are the one thing here that costs frame time in every scene, not just in heavy
	 * ones: at 500 fps a frame is under two milliseconds, and each HUD string was being drawn twice
	 * for its shadow. Measured on this project's benchmark (854x480, overlays on): 405 fps with the
	 * shadows, 575 without, against its own control phase either side.
	 */
	private final BooleanSetting fastHud = add(new BooleanSetting("Fast HUD",
		"Draw the overlays without drop shadows and with square panel corners: the same layout, "
			+ "fewer quads per frame. On by default — it is the single biggest overlay cost at high "
			+ "frame rates.",
		true, changed -> apply()));

	private final BooleanSetting lowDistance = setting("Reduce Render Distance",
		"Clamp render and simulation distance to 8 chunks. The single biggest frame rate lever, "
			+ "and also the one that changes how far you can see.", false);

	private final IntSetting lowDistanceChunks = add(new IntSetting("Distance",
		"Chunks to clamp render and simulation distance to.", 8, 2, 32, chunks -> apply()));

	/** Every option this module has changed, with the value it had before. */
	private final Map<OptionInstance<?>, Object> saved = new LinkedHashMap<>();

	public PerformanceModule() {
		super("Performance Mode", "Applies the vanilla settings that actually move the frame rate, and puts them back when switched off.", Category.CLIENT);
	}

	/** Adds a switch that re-applies the preset immediately when it is flipped. */
	private BooleanSetting setting(String name, String description, boolean defaultValue) {
		return add(new BooleanSetting(name, description, defaultValue, changed -> apply()));
	}

	private Options options() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.options;
	}

	@Override
	protected void onEnable() {
		apply();
		Chat.info("Performance Mode on — " + ShrdClient.VERSION + " preset applied, " + saved.size() + " settings remembered");
	}

	@Override
	protected void onDisable() {
		dev.shrd.client.util.Renderer.setFast(false);
		Options options = options();

		if (options == null || saved.isEmpty()) {
			return;
		}

		for (Map.Entry<OptionInstance<?>, Object> entry : saved.entrySet()) {
			restore(entry.getKey(), entry.getValue());
		}

		int restored = saved.size();
		saved.clear();
		Chat.info("Performance Mode off — " + restored + " settings restored");
	}

	@SuppressWarnings("unchecked")
	private static <T> void restore(OptionInstance<?> option, Object value) {
		OptionInstance<T> typed = (OptionInstance<T>) option;
		typed.set((T) value);
	}

	/**
	 * Applies every switch that is on, remembering the previous value of each option.
	 * <p>
	 * Options that a switch has just turned back off are restored straight away, so flipping
	 * a switch inside the ClickGUI behaves like the module being switched off for that one
	 * setting rather than leaving it changed until the whole module is disabled.
	 */
	private void apply() {
		if (!isEnabled()) {
			return;
		}

		Options options = options();

		if (options == null) {
			return;
		}

		dev.shrd.client.util.Renderer.setFast(fastHud.get());

		Set<OptionInstance<?>> wanted = new HashSet<>();

		if (visualDowngrade.get()) {
			apply(wanted, options.particles(), ParticleStatus.MINIMAL);
			apply(wanted, options.cloudStatus(), CloudStatus.OFF);
			apply(wanted, options.ambientOcclusion(), false);
			apply(wanted, options.entityShadows(), false);
			apply(wanted, options.biomeBlendRadius(), 0);
			apply(wanted, options.mipmapLevels(), 0);
			apply(wanted, options.entityDistanceScaling(), 0.5);
			apply(wanted, options.cutoutLeaves(), true);
			apply(wanted, options.improvedTransparency(), false);
			apply(wanted, options.chunkSectionFadeInTime(), 0.0);
			apply(wanted, options.textureFiltering(), TextureFilteringMethod.NONE);
			apply(wanted, options.vignette(), false);
			// Rebuilding chunks in the background keeps the frame that follows a block change from
			// doing the upload itself, which is what the hitch after breaking a block usually is.
			apply(wanted, options.prioritizeChunkUpdates(), net.minecraft.client.PrioritizeChunkUpdates.NONE);
		}

		if (uncapFramerate.get()) {
			apply(wanted, options.enableVsync(), false);
			// 260 is not a 260 FPS cap: it is Minecraft's own "unlimited" value
			// (Options.UNLIMITED_FRAMERATE_CUTOFF), the point at which the frame limiter stops being
			// called at all. Anything above it is left to the GPU.
			apply(wanted, options.framerateLimit(), 260);
		}

		if (noBobbing.get()) {
			apply(wanted, options.bobView(), false);
		}

		if (lowDistance.get()) {
			int chunks = lowDistanceChunks.get();
			apply(wanted, options.renderDistance(), Math.min(options.renderDistance().get(), chunks));
			apply(wanted, options.simulationDistance(), Math.min(options.simulationDistance().get(), chunks));
		}

		for (Map.Entry<OptionInstance<?>, Object> entry : new ArrayList<>(saved.entrySet())) {
			if (!wanted.contains(entry.getKey())) {
				restore(entry.getKey(), entry.getValue());
				saved.remove(entry.getKey());
			}
		}
	}

	/** Remembers an option's previous value the first time it is touched, then sets it. */
	private <T> void apply(Set<OptionInstance<?>> wanted, OptionInstance<T> option, T value) {
		wanted.add(option);

		if (!saved.containsKey(option)) {
			saved.put(option, option.get());
		}

		if (!option.get().equals(value)) {
			option.set(value);
		}
	}
}
