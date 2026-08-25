package dev.universaladmin.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * A forward-only transformation of {@code config.yml} from one
 * {@code config-version} to the next - e.g. renaming a key, restructuring a
 * section, changing a default's meaning. Mirrors
 * {@link dev.universaladmin.storage.Migration} for the database schema, but
 * operates on the YAML config instead of SQL.
 *
 * <p>Nothing implements this yet - {@code config-version} is 1 and there is
 * no prior version to migrate from. It exists so a future config change
 * doesn't have to silently overwrite (or worse, corrupt) an existing user's
 * {@code config.yml}; see {@link ConfigMigrationRunner} and
 * docs/user/configuration.md.
 */
public interface ConfigMigration {

    /** The version this migration upgrades *to*. Must be globally unique and increasing. */
    int version();

    String description();

    /** Mutates {@code config} in place. Bukkit's YAML config keeps unrelated keys untouched by default. */
    void migrate(FileConfiguration config);
}
