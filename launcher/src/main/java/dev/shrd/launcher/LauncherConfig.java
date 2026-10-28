package dev.shrd.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.shrd.launcher.update.Updater;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the launcher remembers between runs: accounts, instances, the Java it should use and
 * the defaults new instances inherit. Stored as pretty-printed JSON so it can be read and edited
 * by hand.
 */
public final class LauncherConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public List<Account> accounts = new ArrayList<>();
	public List<Instance> instances = new ArrayList<>();
	public String activeAccount = "";
	public String activeInstance = "Default";
	/** Explicit java.exe. Blank means "detect one" (bundled toolchain, JAVA_HOME, then PATH). */
	public String javaPath = "";
	public String extraJvmArgs = "";
	public boolean closeOnLaunch = false;
	public boolean streamGameLog = true;
	public int defaultMemoryMb = 4096;
	/** Where update checks read the release manifest from. Blank turns checking off entirely. */
	public String updateManifestUrl = Updater.DEFAULT_MANIFEST_URL;
	/** Check once when the launcher starts, as well as when the button is pressed. */
	public boolean checkForUpdatesOnStart = true;
	/** Image drawn behind the Home hero. Blank means the built-in purple gradient. */
	public String backgroundImage = "";
	/**
	 * Azure application (client) id used for Microsoft sign-in. A public client with
	 * {@code XboxLive.signin} delegated permission and device-code flow enabled. Blank means the
	 * Sign in button explains how to make one instead of failing.
	 */
	public String microsoftClientId = "";

	public static LauncherConfig load() {
		File file = ShrdPaths.configFile();
		LauncherConfig config = new LauncherConfig();

		if (file.isFile()) {
			try {
				LauncherConfig stored = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), LauncherConfig.class);

				if (stored != null) {
					config = stored;
				}
			} catch (Exception e) {
				System.err.println("could not read " + file + ": " + e.getMessage());
			}
		}

		config.repair();
		return config;
	}

	/** Fills in whatever a hand-edited or older config is missing. */
	public void repair() {
		if (accounts == null) {
			accounts = new ArrayList<>();
		}

		if (instances == null) {
			instances = new ArrayList<>();
		}

		if (instances.isEmpty()) {
			instances.add(new Instance("Default"));
		}

		if (accounts.isEmpty()) {
			accounts.add(new Account("Player"));
		}

		if (findInstance(activeInstance) == null) {
			activeInstance = instances.get(0).name;
		}

		if (findAccount(activeAccount) == null) {
			activeAccount = accounts.get(0).name;
		}

		if (defaultMemoryMb < 512) {
			defaultMemoryMb = 4096;
		}

		if (updateManifestUrl == null) {
			// Blank is a real choice — it disables the check — so only null gets the default.
			updateManifestUrl = Updater.DEFAULT_MANIFEST_URL;
		}

		if (backgroundImage == null) {
			backgroundImage = "";
		}

		if (microsoftClientId == null) {
			microsoftClientId = "";
		}
	}

	public void save() {
		try {
			ShrdPaths.ensureDirs();
			Files.writeString(ShrdPaths.configFile().toPath(), GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("could not save config: " + e.getMessage());
		}
	}

	public Instance findInstance(String name) {
		for (Instance instance : instances) {
			if (instance.name.equalsIgnoreCase(name)) {
				return instance;
			}
		}

		return null;
	}

	public Instance active() {
		Instance instance = findInstance(activeInstance);
		return instance != null ? instance : instances.get(0);
	}

	public Account findAccount(String name) {
		for (Account account : accounts) {
			if (account.name.equalsIgnoreCase(name)) {
				return account;
			}
		}

		return null;
	}

	public Account activeAccount() {
		Account account = findAccount(activeAccount);
		return account != null ? account : accounts.get(0);
	}

	/** A free instance name, so "New Instance" never silently overwrites a real one. */
	public String uniqueInstanceName() {
		int index = 1;

		while (findInstance("Instance " + index) != null) {
			index++;
		}

		return "Instance " + index;
	}

	public String uniqueAccountName() {
		int index = 1;

		while (findAccount("Player" + index) != null) {
			index++;
		}

		return "Player" + index;
	}
}
