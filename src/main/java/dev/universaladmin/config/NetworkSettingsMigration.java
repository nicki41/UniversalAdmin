package dev.universaladmin.config;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Config version 3 (UniversalAdmin 1.1): adds the {@code network} section
 * (off by default) so it is discoverable in the file, and raises the Staff
 * Mode tool range from the old default of 40 blocks to the new 128 - only if
 * it still is exactly 40, a value the server owner chose stays.
 */
public final class NetworkSettingsMigration implements ConfigMigration {

    private static final String TARGET_RANGE = "moderation.staffmode.target-range-blocks";

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Add the network section and the new staff tool range";
    }

    @Override
    public void migrate(FileConfiguration config) {
        if (!config.isSet("network.enabled")) {
            config.set("network.enabled", false);
            config.setComments("network", List.of(
                    "Network mode for proxy networks (Velocity, BungeeCord): every server with UniversalAdmin",
                    "shares the MySQL/MariaDB database (database.type: mysql) and sees the others - servers,",
                    "players, cross-server bans, staff chat and reports. See docs/user/network.md."));
        }
        if (!config.isSet("network.server-name")) {
            config.set("network.server-name", "");
        }
        if (!config.isSet("network.poll-interval")) {
            config.set("network.poll-interval", "1s");
        }
        if (!config.isSet("network.list-other-servers")) {
            config.set("network.list-other-servers", true);
        }
        if (config.isInt(TARGET_RANGE) && config.getInt(TARGET_RANGE) == 40) {
            config.set(TARGET_RANGE, 128);
        }
    }
}
