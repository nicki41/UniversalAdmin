package dev.universaladmin.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.universaladmin.storage.DatabaseConfig;
import java.nio.file.Path;
import javax.sql.DataSource;

/**
 * The only class in the codebase that knows how to turn a {@link DatabaseConfig}
 * into a pooled JDBC {@link DataSource}. Everything above this (repositories,
 * services, migrations) depends on {@code DataSource}/{@code Connection}
 * only, never on Hikari or a specific driver directly.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static DataSource create(DatabaseConfig config, Path dataFolder) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("universaladmin-pool");

        switch (config.type()) {
            case SQLITE -> {
                Path databaseFile = dataFolder.resolve(config.sqliteFileName());
                // Pragmas set via query parameters (sqlite-jdbc maps them onto
                // SQLiteConfig): WAL lets future readers run without blocking
                // on the single writer's file lock; synchronous=NORMAL is the
                // documented safe pairing with WAL (fsync at checkpoints, not
                // every commit); foreign_keys is off by default in SQLite and
                // must be turned on explicitly per connection; busy_timeout
                // avoids an immediate "database is locked" error if something
                // outside the pool (e.g. a backup tool) briefly holds the file
                // lock. See docs/architecture/storage.md#health.
                hikariConfig.setJdbcUrl("jdbc:sqlite:%s?journal_mode=WAL&synchronous=NORMAL&foreign_keys=on&busy_timeout=5000"
                        .formatted(databaseFile.toAbsolutePath()));
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                // SQLite has no real concept of concurrent writers; keep the pool
                // to a single connection to avoid "database is locked" errors.
                hikariConfig.setMaximumPoolSize(1);
            }
            case MYSQL -> {
                String jdbcUrl = "jdbc:mariadb://%s:%d/%s?useSSL=%s".formatted(
                        config.host(), config.port(), config.database(), config.ssl());
                hikariConfig.setJdbcUrl(jdbcUrl);
                hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
                hikariConfig.setUsername(config.username());
                hikariConfig.setPassword(config.password());
                hikariConfig.setMaximumPoolSize(Math.max(1, config.poolSize()));
            }
        }

        return new HikariDataSource(hikariConfig);
    }
}
