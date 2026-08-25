package dev.universaladmin.storage;

import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Owns the plugin's {@link DataSource} and {@link MigrationRunner}. One
 * instance lives on {@link dev.universaladmin.core.UniversalAdmin}; modules
 * get their {@link Repository} implementations a {@link DataSource} from
 * here and register their migrations with {@link #migrations()}.
 *
 * <h2 id="health">Health</h2>
 *
 * The constructor creates the connection pool and runs one validation query
 * against it, tracking the result as a {@link DatabaseHealth} (see
 * {@link #health()}). Storage is a <b>critical</b> bootstrap component (see
 * docs/architecture/modules.md): if the database is completely unavailable
 * - a bad SQLite path, an unreachable MySQL/MariaDB host - this constructor
 * throws, {@link dev.universaladmin.bootstrap.UniversalAdminPlugin} logs it
 * with full context and disables the whole plugin. UniversalAdmin does not
 * attempt to run in a degraded, storage-less mode: every built-in module
 * assumes a working database, and a plugin that appears to start while
 * silently unable to persist anything is worse than one that refuses to
 * start. This tracked state is a startup/shutdown snapshot, not a
 * continuously-updated live probe - a DB outage that starts mid-session
 * (e.g. a remote MySQL server going down) is not reflected here; that would
 * need a periodic background health check, which is future work.
 */
public final class StorageService {

    /** How long {@link Connection#isValid(int)} may take before the pool is considered unreachable. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;
    private final MigrationRunner migrationRunner;
    private final AtomicReference<DatabaseHealth> health = new AtomicReference<>(DatabaseHealth.DISCONNECTED);

    public StorageService(DatabaseConfig config, Path dataFolder, Logger logger) {
        health.set(DatabaseHealth.CONNECTING);
        try {
            this.dataSource = DataSourceFactory.create(config, dataFolder);
            validate(dataSource);
            health.set(DatabaseHealth.READY);
        } catch (RuntimeException e) {
            health.set(DatabaseHealth.FAILED);
            throw e;
        }
        this.migrationRunner = new MigrationRunner(dataSource, logger);
    }

    private static void validate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new StorageException("Database connection did not pass validation", null);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to validate the database connection", e);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public MigrationRunner migrations() {
        return migrationRunner;
    }

    /** See the class-level "Health" section above. */
    public DatabaseHealth health() {
        return health.get();
    }

    /** Closes the underlying connection pool. Called once from plugin disable. */
    public void close() {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close data source", e);
            } finally {
                health.set(DatabaseHealth.DISCONNECTED);
            }
        }
    }
}
