package dev.universaladmin.command.paper;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.UniversalAdminCommand;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers each {@link AdminCommand#shortcuts()} label (e.g. {@code /kick})
 * as a real top-level command through Paper's command lifecycle, delegating
 * to the same {@link UniversalAdminCommand} dispatcher {@code /admin} uses -
 * so a shortcut behaves exactly like its {@code /admin} form (permission,
 * completion, messages), it is only a shorter way to type it.
 *
 * <p>Runs inside Paper's {@code COMMANDS} lifecycle event, which Paper fires
 * once every plugin has enabled (and again on a datapack reload) - so
 * commands an extension registered in its own {@code onEnable} get their
 * shortcuts too. Paper always also registers the namespaced form
 * ({@code /universaladmin:kick}), so if another plugin owns the plain label
 * the command is still reachable; server owners can skip individual labels
 * with {@code commands.shortcuts.disabled} or switch admin shortcuts off
 * entirely with {@code commands.shortcuts.enabled: false}.
 */
public final class ShortcutCommands {

    private ShortcutCommands() {
    }

    public static void install(
            JavaPlugin plugin, UniversalAdminCommand dispatcher, SettingsService settings, MessageService messages, Logger logger) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            boolean adminShortcuts = settings.get(CoreSettings.COMMAND_SHORTCUTS_ENABLED);
            Set<String> skipped = settings.get(CoreSettings.COMMAND_SHORTCUTS_DISABLED).stream()
                    .map(label -> label.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            Set<String> registered = new HashSet<>();
            for (AdminCommand command : dispatcher.registry().all()) {
                if (!adminShortcuts && !command.alwaysRegisterShortcuts()) {
                    continue;
                }
                for (String shortcut : command.shortcuts()) {
                    String label = shortcut.toLowerCase(Locale.ROOT);
                    if (skipped.contains(label) || !registered.add(label)) {
                        continue;
                    }
                    String description = PlainTextComponentSerializer.plainText()
                            .serialize(ComponentMessages.render(messages.get(command.description())));
                    registrar.register(label, description, List.of(), new Shortcut(dispatcher, command, label));
                }
            }
            logger.fine(() -> "Registered " + registered.size() + " command shortcut(s): " + registered);
        });
    }

    /** One shortcut label bound to one {@link AdminCommand}. */
    private record Shortcut(UniversalAdminCommand dispatcher, AdminCommand target, String label) implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            dispatcher.dispatchResolved(source.getSender(), label, target, Arrays.asList(args));
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            // Paper splits the typed remainder on spaces keeping a trailing
            // empty element, so the last element is always the argument being
            // typed - except for no input at all, which arrives empty.
            List<String> typed = args.length == 0 ? List.of("") : Arrays.asList(args);
            return dispatcher.completeResolved(source.getSender(), label, target, typed);
        }

        @Override
        public String permission() {
            return target.permission() == null ? null : target.permission().value();
        }

    }
}
