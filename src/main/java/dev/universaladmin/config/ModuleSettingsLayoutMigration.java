package dev.universaladmin.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Config version 2 (UniversalAdmin 1.0): every module's settings now live in
 * that module's own section - {@code vanish.*} becomes
 * {@code moderation.vanish.*}, {@code countdown.*} becomes
 * {@code server.countdown.*}, and so on - and the sections added in 1.0
 * ({@code commands}, the {@code reports}/{@code chat} module toggles) are
 * written with their defaults so they are discoverable in the file.
 *
 * <p>Only moves keys the server owner actually set; a key that already
 * exists at its new place is never overwritten. Unrelated keys and comments
 * stay untouched.
 */
public final class ModuleSettingsLayoutMigration implements ConfigMigration {

    /** Old root (or leaf) path → new path. Roots are moved with every key below them. */
    private static final Map<String, String> MOVES = new LinkedHashMap<>();

    static {
        MOVES.put("reasons", "moderation.reasons");
        MOVES.put("durations", "moderation.durations");
        MOVES.put("expiry", "moderation.expiry");
        MOVES.put("freeze", "moderation.freeze");
        MOVES.put("staffmode", "moderation.staffmode");
        MOVES.put("vanish", "moderation.vanish");
        MOVES.put("countdown", "server.countdown");
        MOVES.put("gui.max-results", "players.gui.max-results");
        MOVES.put("gui.search-max-results", "whitelist.gui.search-max-results");
    }

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Group module settings under their module and add the 1.0 sections";
    }

    @Override
    public void migrate(FileConfiguration config) {
        MOVES.forEach((from, to) -> move(config, from, to));

        setIfMissing(config, "modules.reports", true);
        setIfMissing(config, "modules.chat", true);
        if (!config.isSet("commands.shortcuts.enabled")) {
            config.set("commands.shortcuts.enabled", true);
            config.setComments("commands", List.of(
                    "Top-level shortcuts such as /kick, /ban, /vanish, /heal next to /admin <command>.",
                    "/universaladmin:<command> always works, even if another plugin owns the plain label."));
        }
        setIfMissing(config, "commands.shortcuts.disabled", List.of());
    }

    private static void move(FileConfiguration config, String from, String to) {
        if (!config.isSet(from)) {
            return;
        }
        Object value = config.get(from);
        if (value instanceof ConfigurationSection section) {
            for (Map.Entry<String, Object> leaf : section.getValues(true).entrySet()) {
                if (!(leaf.getValue() instanceof ConfigurationSection)) {
                    setIfMissing(config, to + "." + leaf.getKey(), leaf.getValue());
                }
            }
        } else {
            setIfMissing(config, to, value);
        }
        config.set(from, null);
        removeIfEmpty(config, from);
    }

    /** After moving {@code gui.max-results}, an otherwise empty {@code gui} section would be left behind. */
    private static void removeIfEmpty(FileConfiguration config, String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String parent = path.substring(0, dot);
        ConfigurationSection section = config.getConfigurationSection(parent);
        if (section != null && section.getKeys(false).isEmpty()) {
            config.set(parent, null);
        }
    }

    private static void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.isSet(path)) {
            config.set(path, value);
        }
    }
}
