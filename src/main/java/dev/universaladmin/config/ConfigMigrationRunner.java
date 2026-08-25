package dev.universaladmin.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Applies pending {@link ConfigMigration}s in version order and stamps
 * {@code config-version} to {@link #CURRENT_VERSION} afterwards - whether or
 * not any migration actually ran, so a config file predating this system
 * entirely (no {@code config-version} key at all) still ends up correctly
 * versioned instead of being silently treated as current.
 *
 * <p>Run once at startup and again on every {@code /admin reload} (see
 * {@link dev.universaladmin.settings.ReloadConfigAction}) - idempotent
 * either way, since a migration only applies if its version is newer than
 * what's already stamped in the file.
 */
public final class ConfigMigrationRunner {

    public static final int CURRENT_VERSION = 1;

    private final List<ConfigMigration> migrations = new ArrayList<>();
    private final Logger logger;

    public ConfigMigrationRunner(Logger logger) {
        this.logger = logger;
    }

    public void register(ConfigMigration migration) {
        migrations.add(migration);
    }

    /** @return true if the file's version changed (i.e. the caller should persist it) */
    public boolean run(FileConfiguration config) {
        int currentVersion = config.getInt("config-version", 0);
        List<ConfigMigration> pending = migrations.stream()
                .filter(migration -> migration.version() > currentVersion)
                .sorted(Comparator.comparingInt(ConfigMigration::version))
                .toList();

        for (ConfigMigration migration : pending) {
            logger.info(() -> "Applying config migration " + migration.version() + ": " + migration.description());
            migration.migrate(config);
        }

        boolean changed = !pending.isEmpty() || currentVersion != CURRENT_VERSION;
        config.set("config-version", CURRENT_VERSION);
        return changed;
    }
}
