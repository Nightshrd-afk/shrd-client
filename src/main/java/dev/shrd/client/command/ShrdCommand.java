package dev.shrd.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.shrd.client.ShrdClient;
import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.modules.player.DeathCoordsModule;
import dev.shrd.client.modules.utility.CopyCoordsModule;
import dev.shrd.client.util.KeyNames;
import dev.shrd.client.util.PlayerUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /shrd} — a fully client-side command namespace that mirrors everything the mod menu
 * can do, for players who prefer typing.
 */
public final class ShrdCommand {

	private ShrdCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("shrd")
			.executes(context -> {
				info(context.getSource(), ShrdClient.NAME + " v" + ShrdClient.VERSION + " — " + ShrdClient.get().countEnabled() + " modules enabled");
				info(context.getSource(), "try: /shrd modules | /shrd toggle <name> | /shrd gui (Right Shift)");
				return 1;
			})
			.then(ClientCommands.literal("gui")
				.executes(context -> {
					ShrdClient.get().openModMenu();
					return 1;
				}))
			.then(ClientCommands.literal("panels")
				.executes(context -> {
					ShrdClient.get().openClassicGui();
					return 1;
				}))
			.then(ClientCommands.literal("hud")
				.executes(context -> {
					ShrdClient.get().openHudEditor();
					return 1;
				}))
			.then(ClientCommands.literal("modules")
				.executes(context -> {
					for (Category category : Category.values()) {
						StringBuilder builder = new StringBuilder(category.getDisplayName()).append(": ");

						for (Module module : ShrdClient.get().getModules().getModules(category)) {
							builder.append(module.isEnabled() ? "+" : "-").append(module.getName()).append("  ");
						}

						info(context.getSource(), builder.toString().trim());
					}

					return 1;
				}))
			.then(ClientCommands.literal("toggle")
				.then(ClientCommands.argument("module", StringArgumentType.greedyString())
					.executes(context -> {
						String name = StringArgumentType.getString(context, "module");
						Module module = ShrdClient.get().getModule(name);

						if (module == null) {
							error(context.getSource(), "no module called '" + name + "'");
							return 0;
						}

						module.toggle();
						info(context.getSource(), module.getName() + (module.isEnabled() ? " enabled" : " disabled"));
						return 1;
					})))
			.then(ClientCommands.literal("bind")
				.then(ClientCommands.argument("args", StringArgumentType.greedyString())
					.executes(context -> {
						String[] parts = StringArgumentType.getString(context, "args").split("\\s+");

						if (parts.length < 2) {
							error(context.getSource(), "usage: /shrd bind <module> <key>");
							return 0;
						}

						String keyName = parts[parts.length - 1];
						String moduleName = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
						Module module = ShrdClient.get().getModule(moduleName);

						if (module == null) {
							error(context.getSource(), "no module called '" + moduleName + "'");
							return 0;
						}

						int code = KeyNames.parse(keyName);
						module.setKeybind(code);
						info(context.getSource(), module.getName() + " bound to " + KeyNames.get(code));
						return 1;
					})))
			.then(ClientCommands.literal("coords")
				.executes(context -> {
					Module module = ShrdClient.get().getModule("Copy Coords");

					if (module instanceof CopyCoordsModule copy) {
						String text = copy.currentText();
						PlayerUtils.mc().keyboardHandler.setClipboard(text);
						info(context.getSource(), "copied: " + text);
					} else {
						error(context.getSource(), "copy coords module is unavailable");
					}

					return 1;
				}))
			.then(ClientCommands.literal("deaths")
				.executes(context -> {
					Module module = ShrdClient.get().getModule("Death Coords");

					if (module instanceof DeathCoordsModule deaths) {
						deaths.printHistory(line -> info(context.getSource(), line));
					} else {
						error(context.getSource(), "death coords module is unavailable");
					}

					return 1;
				}))
			.then(ClientCommands.literal("config")
				.then(ClientCommands.literal("save")
					.executes(context -> {
						ShrdClient.get().getConfig().save();
						info(context.getSource(), "saved to " + ShrdClient.get().getConfig().getConfigFile());
						return 1;
					}))
				.then(ClientCommands.literal("load")
					.executes(context -> {
						ShrdClient.get().getConfig().load();
						info(context.getSource(), "config reloaded");
						return 1;
					}))
				.then(ClientCommands.literal("path")
					.executes(context -> {
						info(context.getSource(), ShrdClient.get().getConfig().getConfigFile().toString());
						return 1;
					})))
			.then(ClientCommands.literal("search")
				.then(ClientCommands.argument("query", StringArgumentType.greedyString())
					.executes(context -> {
						String query = StringArgumentType.getString(context, "query").toLowerCase(Locale.ROOT);
						int matches = 0;

						for (Module module : ShrdClient.get().getModules().getModules()) {
							if (module.getName().toLowerCase(Locale.ROOT).contains(query)) {
								info(context.getSource(), module.getName() + " — " + module.getDescription());
								matches++;
							}
						}

						if (matches == 0) {
							error(context.getSource(), "nothing matches '" + query + "'");
						}

						return matches;
					}))));
	}

	private static void info(FabricClientCommandSource source, String message) {
		source.sendFeedback(Component.literal("shrd » " + message));
	}

	private static void error(FabricClientCommandSource source, String message) {
		source.sendError(Component.literal("shrd » " + message));
	}
}
