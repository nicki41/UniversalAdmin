package dev.universaladmin.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Applies pending {@link Migration}s in version order and tracks what has
 * already run in a {@code schema_version} table.
 *
 * <p>Modules call {@link #register} during {@code onEnable}; nothing runs
 * until {@link #runPending()} is called once by {@link StorageService}
 * during plugin startup, after every module has had a chance to register its
 * migrations.
 */
public final class MigrationRunner {

    private final DataSource dataSource;
    private final Logger logger;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationRunner(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    /**
     * Registers a migration. Registering the same migration class again (an
     * extension whose plugin was reloaded) is a no-op.
     *
     * @throws IllegalStateException if a <i>different</i> migration already uses the same version -
     *                               versions are global across the core and every extension
     */
    public synchronized void register(Migration migration) {
        for (Migration existing : migrations) {
            if (existing.version() == migration.version()) {
                if (existing.getClass().equals(migration.getClass())) {
                    return;
                }
                throw new IllegalStateException("Migration version " + migration.version() + " is used by both "
                        + existing.getClass().getName() + " and " + migration.getClass().getName());
            }
        }
        migrations.add(migration);
    }

    /**
     * Runs every migration whose version is newer than the current schema
     * version. Blocking JDBC work - must be called from a background thread,
     * never the Paper main thread. See docs/architecture/threading.md.
     */
    public void runPending() {
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(Migration::version));

        try (Connection connection = dataSource.getConnection()) {
            ensureSchemaVersionTable(connection);
            int current = currentVersion(connection);
            for (Migration migration : ordered) {
                if (migration.version() <= current) {
                    continue;
                }
                logger.info(() -> "Applying migration " + migration.version() + ": " + migration.description());
                migration.migrate(connection);
                recordVersion(connection, migration);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run database migrations", e);
        }
    }

    private void ensureSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at BIGINT NOT NULL
                    )
                    """);
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void recordVersion(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}
