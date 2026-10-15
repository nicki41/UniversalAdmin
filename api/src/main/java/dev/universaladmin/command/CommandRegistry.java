package dev.universaladmin.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every {@link AdminCommand} known to the platform, looked up by name or
 * alias (case-insensitive). Modules and extensions register into this in
 * {@code onEnable}; the root {@code /admin} command dispatches through it.
 * A name or alias can only be owned once - a clash is a programming error
 * and fails loudly at registration instead of silently shadowing a command.
 */
public final class CommandRegistry {

    private final Map<String, AdminCommand> byName = new ConcurrentHashMap<>();
    private final Map<String, AdminCommand> byLabel = new ConcurrentHashMap<>();

    /** @throws IllegalStateException if the name or one of the aliases is already taken */
    public synchronized void register(AdminCommand command) {
        List<String> labels = new ArrayList<>();
        labels.add(normalize(command.name()));
        command.aliases().forEach(alias -> labels.add(normalize(alias)));
        for (String label : labels) {
            AdminCommand existing = byLabel.get(label);
            if (existing != null) {
                throw new IllegalStateException("Command label '" + label + "' is already registered by /admin "
                        + existing.name());
            }
        }
        byName.put(normalize(command.name()), command);
        labels.forEach(label -> byLabel.put(label, command));
    }

    /** Removes the command registered under {@code name} together with its aliases. */
    public synchronized void unregister(String name) {
        AdminCommand removed = byName.remove(normalize(name));
        if (removed != null) {
            byLabel.values().removeIf(command -> command == removed);
        }
    }

    /** Looks a command up by its name or one of its aliases. */
    public Optional<AdminCommand> find(String nameOrAlias) {
        return Optional.ofNullable(byLabel.get(normalize(nameOrAlias)));
    }

    /** Every registered command, sorted by name. */
    public List<AdminCommand> all() {
        Collection<AdminCommand> commands = byName.values();
        return commands.stream().sorted(Comparator.comparing(AdminCommand::name)).toList();
    }

    private static String normalize(String label) {
        return label.toLowerCase(Locale.ROOT);
    }
}
