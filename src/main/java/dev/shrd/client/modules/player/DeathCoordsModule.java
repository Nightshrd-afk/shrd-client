package dev.shrd.client.modules.player;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.ModuleManager;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.core.setting.IntSetting;
import dev.shrd.client.util.Chat;
import dev.shrd.client.util.PlayerUtils;
import dev.shrd.client.util.TimeUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Remembers where you died so you are not hunting through screenshots after a bad fight.
 * The list is local to the client — the server never sees it — and is readable with
 * {@code /shrd deaths}.
 */
public class DeathCoordsModule extends Module implements ModuleManager.SessionAware {

	public record Death(String coordinates, String dimension, String biome, long whenMillis) {
		public String describe() {
			return String.format(Locale.ROOT, "%s  in %s (%s)  %s ago",
				coordinates, dimension, biome, TimeUtils.formatDuration(System.currentTimeMillis() - whenMillis));
		}
	}

	private final BooleanSetting chatMessage = add(new BooleanSetting("Chat Message", "Print the death position in chat.", true));
	private final IntSetting history = add(new IntSetting("History", "How many deaths to remember.", 5, 1, 20));

	private final Deque<Death> deaths = new ArrayDeque<>();
	private boolean wasAlive = true;
	private float lastKnownHealth = 20f;

	public DeathCoordsModule() {
		super("Death Coords", "Records where you died and when.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (!PlayerUtils.inGame()) {
			wasAlive = true;
			return;
		}

		float health = PlayerUtils.player().getHealth();

		if (health <= 0f && wasAlive) {
			recordDeath();
		}

		wasAlive = health > 0f;
		lastKnownHealth = health;
	}

	private void recordDeath() {
		Death death = new Death(
			PlayerUtils.coords(false),
			PlayerUtils.dimension(),
			PlayerUtils.biome(),
			System.currentTimeMillis()
		);

		deaths.addFirst(death);

		while (deaths.size() > history.get()) {
			deaths.removeLast();
		}

		if (chatMessage.get()) {
			Chat.warn("you died at " + death.coordinates() + " in " + death.dimension() + " — /shrd deaths for the list");
		}
	}

	@Override
	public void onSessionEnd() {
		wasAlive = true;
	}

	public Deque<Death> getDeaths() {
		return deaths;
	}

	public Death lastDeath() {
		return deaths.peekFirst();
	}

	public float lastKnownHealth() {
		return lastKnownHealth;
	}

	/** Called by {@code /shrd deaths}. */
	public boolean printHistory(java.util.function.Consumer<String> sink) {
		if (deaths.isEmpty()) {
			sink.accept("no deaths recorded yet");
			return false;
		}

		int index = 1;

		for (Death death : deaths) {
			sink.accept("#" + index + " " + death.describe());
			index++;
		}

		return true;
	}
}
