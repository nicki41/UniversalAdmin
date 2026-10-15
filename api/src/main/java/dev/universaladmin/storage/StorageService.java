package dev.universaladmin.storage;

import javax.sql.DataSource;

/**
 * The plugin's database: a pooled {@link DataSource} (SQLite or
 * MySQL/MariaDB, chosen in {@code config.yml}) plus the shared
 * {@link MigrationRunner}. Modules - built-in or extension - build their
 * {@link Repository} implementations on {@link #dataSource()} and register
 * their schema changes with {@link #migrations()} in {@code Module#onLoad}.
 *
 * <p>Every blocking call on the data source belongs off the main thread -
 * see {@link dev.universaladmin.scheduler.TaskScheduler} and
 * docs/architecture/threading.md. Tables of new modules should carry a
 * prefix of their own (UniversalAdmin uses {@code ua_}) so they never
 * collide with another plugin in a shared MySQL database.
 */
public interface StorageService {

    DataSource dataSource();

    /** Which database is configured - for the rare repository that needs dialect-specific SQL. */
    DatabaseType type();

    MigrationRunner migrations();

    /** Startup/shutdown health snapshot - not a live probe. */
    DatabaseHealth health();
}
