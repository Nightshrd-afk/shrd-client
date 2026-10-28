package dev.shrd.client.gametest;

import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.core.setting.Setting;
import dev.shrd.client.gui.ClickGuiScreen;
import dev.shrd.client.gui.HudEditorScreen;
import dev.shrd.client.gui.ModMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end UI test and frame rate benchmark for Shrd Client.
 * <p>
 * It joins a fresh singleplayer world, switches a representative set of modules on, and
 * screenshots the four surfaces that matter: the mod menu, the in-game HUD, the draggable
 * panel layout and the HUD editor. Running it proves the overlays and screens actually
 * render with the real Minecraft 26.2 render pipeline — a successful compile alone would not.
 * <p>
 * After the screenshots it measures the frame rate in four configurations and prints one
 * {@code SHRD-BENCH} line per phase, so the cost of the overlays and the gain from
 * Performance Mode are measured rather than claimed.
 * <p>
 * Run with:
 * <pre>
 *   ./gradlew runGametest
 *   java tools/UiCheck.java run/screenshots/shrd-clickgui.png accent,text,textDim
 * </pre>
 * The entrypoint is inert in a normal game: Fabric only runs {@code fabric-client-gametest}
 * entrypoints when the {@code fabric.client.gametest} system property is set, which the
 * dedicated run configuration does.
 */
public class ShrdClientGameTest implements FabricClientGameTest {

	private static final String[] MODULES = {
		"Watermark", "FPS", "CPS", "Ping", "Coordinates", "Direction", "Speed",
		"Biome", "Dimension", "Server", "Clock", "Session", "Memory",
		"Keystrokes", "Armor", "Potion Effects", "Durability", "Totems", "Reach", "Combo", "ArrayList"
	};

	private static final List<Long> FRAME_TIMES = new ArrayList<>();
	private static final List<Integer> FPS_COUNTER = new ArrayList<>();
	private static volatile boolean recording;
	private static boolean hookInstalled;

	@Override
	public void runTest(ClientGameTestContext context) {
		installSampler();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(client -> client.player != null && client.level != null, 400);
			context.waitTicks(40);

			enableOverlays(context);
			context.waitTicks(40);
			context.takeScreenshot("shrd-hud");

			// The Feather-style mod menu is what Right Shift opens, so it is the first surface
			// shown here; the draggable panel layout follows as the secondary screen.
			context.runOnClient(client -> client.setScreenAndShow(new ModMenuScreen()));
			context.waitTicks(20);
			context.takeScreenshot("shrd-modmenu");

			context.runOnClient(client -> client.setScreenAndShow(new ClickGuiScreen()));
			context.waitTicks(20);
			context.takeScreenshot("shrd-clickgui");

			context.runOnClient(client -> client.setScreenAndShow(new HudEditorScreen()));
			context.waitTicks(20);
			context.takeScreenshot("shrd-hud-editor");

			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(10);

			checkModMenuInteraction(context);
			benchmark(context);
		}
	}

	// ------------------------------------------------------------------ screenshots

	private void enableOverlays(ClientGameTestContext context) {
		context.runOnClient(client -> setOverlays(true));
	}

	private static void setOverlays(boolean enabled) {
		ShrdClient shrd = ShrdClient.get();

		for (String name : MODULES) {
			Module module = shrd.getModule(name);

			if (module != null) {
				module.setEnabled(enabled);
			}
		}
	}

	// --------------------------------------------------------------- interaction check

	/**
	 * Drives the mod menu through its real input handlers and fails the test if they do not do
	 * what the screen promises.
	 * <p>
	 * A screenshot proves the menu renders; it says nothing about whether clicking a row toggles a
	 * module. Rather than rely on synthetic OS input — which never reached the game's GLFW input
	 * reliably on this machine — the screen is asked for a row's bounds and then given real
	 * {@code mouseClicked} and {@code keyPressed} events, through the same code path a player's
	 * clicks take.
	 */
	private void checkModMenuInteraction(ClientGameTestContext context) {
		String[] result = context.computeOnClient(client -> {
			ModMenuScreen screen = new ModMenuScreen();
			client.setScreenAndShow(screen);

			Module target = ShrdClient.get().getModule("FPS");
			boolean before = target != null && target.isEnabled();
			int[] bounds = target == null ? null : screen.rowBounds(target);

			if (bounds == null) {
				return new String[] {"row-not-found", "?", "?", "false"};
			}

			int clickX = bounds[0] + bounds[2] / 2;
			int clickY = bounds[1] + bounds[3] / 2;
			screen.mouseClicked(new MouseButtonEvent(clickX, clickY, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
			boolean afterClick = target.isEnabled();

			boolean closed = screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)) && client.gui.screen() == null;
			return new String[] {"clicked", String.valueOf(before), String.valueOf(afterClick), String.valueOf(closed)};
		});

		System.out.println("SHRD-UITEST modmenu-click=" + result[0] + " fpsBefore=" + result[1]
			+ " fpsAfter=" + result[2] + " escapeClosedMenu=" + result[3]);

		if (!result[0].equals("clicked")) {
			throw new AssertionError("mod menu: no row found for the FPS module, so the click path was never exercised");
		}

		if (result[1].equals(result[2])) {
			throw new AssertionError("mod menu: clicking a module row did not toggle it (was " + result[1] + ")");
		}

		if (!result[3].equals("true")) {
			throw new AssertionError("mod menu: ESC did not close the menu");
		}
	}

	// -------------------------------------------------------------------- benchmark

	/**
	 * What a phase actually ran with, so a logged number can be checked against its settings
	 * instead of taken on faith.
	 */
	private record PhaseState(int windowWidth, int windowHeight, boolean windowActive, int renderDistance, String particles, String clouds,
			boolean ambientOcclusion, int mipmaps, boolean entityShadows, boolean cutoutLeaves,
			boolean vsync, int framerateLimit, boolean bobView, int overlays, boolean performanceMode) {
	}

	/**
	 * Measures the configurations that matter, each effect bracketed by controls.
	 * <p>
	 * The frame rate on a machine like this one drifts by tens of percent over a few minutes —
	 * background chunk work, GC, thermal behaviour — which is larger than the effects being
	 * measured. So every effect is measured twice, with a control phase on each side (A B A for
	 * the overlays, B C C B for Performance Mode), each phase runs for 20 seconds after a
	 * 10 second settle, and each phase is summarised by the median rather than the mean so a
	 * single loading stutter cannot move the number. Compare an effect against the average of
	 * the controls that bracket it, not against a single number elsewhere in the run.
	 * <p>
	 * Every phase runs with VSync off and the frame limit at Minecraft's maximum so nothing
	 * here is the monitor's refresh rate or vanilla's 120 FPS default cap.
	 */
	private void benchmark(ClientGameTestContext context) {
		// The client writes its config on shutdown, so a previous run can leave Performance Mode
		// switched on and its distance clamp applied. Normalise module state first, or phase A
		// would silently be measuring performance mode too.
		context.runOnClient(client -> {
			getModule("Performance Mode").setEnabled(false);
			setBooleanSetting("Performance Mode", "Reduce Render Distance", false);
			setOverlays(false);
		});

		context.runOnClient(client -> {
			// Keep the world ticking while the test window is in the background, and stop
			// Minecraft from throttling frames for an idle or unfocused window: either would
			// turn this into a measurement of vanilla's idle behaviour instead of rendering.
			client.options.pauseOnLostFocus = false;
			client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);

			// Start from the profile a normal install actually runs. The gametest harness sets
			// up its own minimal, deterministic profile (5 chunks, custom graphics) for
			// reproducible screenshots, which would hide every graphics lever being measured
			// here — a test world pinned at 5 chunks cannot show what 16 or 8 chunks costs.
			GraphicsPreset.FANCY.apply(client);
			client.options.renderDistance().set(16);
			client.options.simulationDistance().set(12);
			client.options.bobView().set(true);

			// VSync off and the frame limit at Minecraft's maximum for every phase, so nothing
			// measured here is the monitor's refresh rate or vanilla's 120 FPS default cap.
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(260);
		});

		// Overlay cost: control, overlays, control.
		context.runOnClient(client -> setOverlays(false));
		measurePhase(context, "A1-overlays-off");

		context.runOnClient(client -> setOverlays(true));
		measurePhase(context, "B1-overlays-on");

		context.runOnClient(client -> setOverlays(false));
		measurePhase(context, "A2-overlays-off");

		// Performance Mode cost/gain: overlays on throughout, so the only change is the preset.
		context.runOnClient(client -> setOverlays(true));
		measurePhase(context, "B2-overlays-on");

		context.runOnClient(client -> getModule("Performance Mode").setEnabled(true));
		measurePhase(context, "C1-performance-mode");

		context.runOnClient(client -> getModule("Performance Mode").setEnabled(false));
		measurePhase(context, "C2-performance-mode");

		// The distance lever last, and only once: going from 16 to 8 chunks forces a long
		// burst of chunk unloads and light recalculation that contaminates whatever is
		// measured next, so nothing else is measured after it.
		context.runOnClient(client -> {
			getModule("Performance Mode").setEnabled(true);
			setBooleanSetting("Performance Mode", "Reduce Render Distance", true);
		});
		measurePhase(context, "D1-performance-8-chunks", 300);

		// Finally, the two hard caps a stock install runs with. This phase is the answer to
		// "why can't I get 200 FPS": with VSync on and Minecraft's 120 FPS limit in place, the
		// frame rate cannot exceed 120 no matter how fast the machine or the client is.
		context.runOnClient(client -> {
			getModule("Performance Mode").setEnabled(false);
			client.options.enableVsync().set(true);
			client.options.framerateLimit().set(120);
		});
		measurePhase(context, "E1-vanilla-caps-vsync-on-120fps-limit", 300);

		// Everything above ran at the gametest harness's small window (854x480), where nothing on
		// this machine is under real pressure. A 1080p pass would say much more about what a
		// player actually gets, so it is attempted here — but the harness window is not
		// resizable, and these phases are skipped rather than logged at the wrong pixel count if
		// the resize does not take. The logged window size is the check.
		context.runOnClient(client -> {
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(260);
			org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().handle(), 1920, 1080);
		});
		context.waitTicks(100);

		boolean largeWindow = context.computeOnClient(client -> client.getWindow().getWidth() >= 1280);

		if (!largeWindow) {
			System.out.println("SHRD-BENCH phase=F-skipped NOTE=gametest-window-is-not-resizable "
				+ "— every number above is at 854x480, not a typical play window");
			return;
		}

		measurePhase(context, "F1-large-window-overlays-on", 200);

		context.runOnClient(client -> getModule("Performance Mode").setEnabled(true));
		measurePhase(context, "F2-large-window-performance-mode", 200);
	}

	private static Module getModule(String name) {
		return ShrdClient.get().getModule(name);
	}

	private static void setBooleanSetting(String moduleName, String settingName, boolean value) {
		Module module = getModule(moduleName);

		if (module == null) {
			return;
		}

		for (Setting<?> setting : module.getSettings()) {
			if (setting.getName().equals(settingName) && setting instanceof BooleanSetting bool) {
				bool.set(value);
				return;
			}
		}
	}

	private void measurePhase(ClientGameTestContext context, String label) {
		measurePhase(context, label, 200);
	}

	private void measurePhase(ClientGameTestContext context, String label, int warmupTicks) {
		// Settle first: a settings change can trigger a burst of chunk loads or unloads and
		// light recalculation that would otherwise land in the samples. 200 ticks (10s) is
		// enough for a normal settings change; a render distance change gets longer.
		context.waitTicks(warmupTicks);

		FRAME_TIMES.clear();
		FPS_COUNTER.clear();
		recording = true;
		context.waitTicks(400);
		recording = false;

		// Read on the client thread: Minecraft.getInstance() is off limits to the test thread.
		PhaseState state = context.computeOnClient(client -> new PhaseState(
			client.getWindow().getWidth(),
			client.getWindow().getHeight(),
			client.isWindowActive(),
			client.options.renderDistance().get(),
			client.options.particles().get().name(),
			client.options.cloudStatus().get().name(),
			client.options.ambientOcclusion().get(),
			client.options.mipmapLevels().get(),
			client.options.entityShadows().get(),
			client.options.cutoutLeaves().get(),
			client.options.enableVsync().get(),
			client.options.framerateLimit().get(),
			client.options.bobView().get(),
			ShrdClient.get().countHudModules(),
			getModule("Performance Mode").isEnabled()));

		report(label, state);
	}

	private static void report(String label, PhaseState state) {
		if (FRAME_TIMES.isEmpty()) {
			System.out.println("SHRD-BENCH phase=" + label + " samples=0 NOTE=no-frame-samples-captured");
			return;
		}

		double[] fps = FRAME_TIMES.stream()
			.mapToDouble(nanos -> nanos <= 0L ? 0.0 : 1_000_000_000.0 / nanos)
			.filter(value -> value > 0.0)
			.sorted()
			.toArray();

		double mean = 0.0;

		for (double value : fps) {
			mean += value;
		}

		mean /= fps.length;

		double median = fps[fps.length / 2];
		double worst = fps[0];
		// The slowest one percent of frames, averaged: the stutter the eye actually notices.
		int slowCount = Math.max(1, fps.length / 100);
		double low = 0.0;

		for (int index = 0; index < slowCount; index++) {
			low += fps[index];
		}

		low /= slowCount;

		// The client's own FPS counter is a one second rolling average of presented frames and
		// is the number the player sees in F3, so its median over the window is the headline.
		// The frame-time statistic only covers client-side frame work and runs higher.
		double counterMedian = medianOf(FPS_COUNTER);

		System.out.printf(
			"SHRD-BENCH phase=%s samples=%d clientFpsCounterMedian=%.1f clientFpsCounterMean=%.1f "
				+ "frameWorkMedianFps=%.1f frameWorkMeanFps=%.1f slowest1pctFps=%.1f worstFrameFps=%.1f "
				+ "window=%dx%d windowActive=%s renderDistance=%d particles=%s clouds=%s ao=%s mipmaps=%d entityShadows=%s "
				+ "cutoutLeaves=%s vsync=%s maxFps=%d bobView=%s overlays=%d performanceMode=%s%n",
			label, fps.length, counterMedian,
			FPS_COUNTER.stream().mapToInt(Integer::intValue).average().orElse(0.0),
			median, mean, low, worst,
			state.windowWidth(), state.windowHeight(), state.windowActive(), state.renderDistance(), state.particles(), state.clouds(), state.ambientOcclusion(),
			state.mipmaps(), state.entityShadows(), state.cutoutLeaves(), state.vsync(), state.framerateLimit(),
			state.bobView(), state.overlays(), state.performanceMode());
	}

	private static double medianOf(List<Integer> values) {
		if (values.isEmpty()) {
			return 0.0;
		}

		int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
		return sorted[sorted.length / 2];
	}

	/** Samples the live frame time once per client tick while a phase is recording. */
	private static void installSampler() {
		if (hookInstalled) {
			return;
		}

		hookInstalled = true;

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (recording) {
				FRAME_TIMES.add(client.getFrameTimeNs());
				FPS_COUNTER.add(client.getFps());
			}
		});
	}
}
