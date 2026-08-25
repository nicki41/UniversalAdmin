package dev.universaladmin.storage;

import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/**
 * Runs multi-statement work atomically against a single {@link Connection}:
 * autocommit is turned off, {@link Work#execute} runs, then the connection
 * is committed on success or rolled back on any exception. A repository
 * method that only issues one statement does not need this - it's for a
 * save/update that must not be observed half-applied (e.g. writing to two
 * tables for one logical change).
 *
 * <p>Like every other blocking JDBC call, this runs on the given
 * {@link TaskScheduler} - never call {@link #run} from the Paper main
 * thread. See docs/architecture/threading.md.
 */
public final class Transactions {

    private Transactions() {
    }

    public static <T> CompletableFuture<T> run(DataSource dataSource, TaskScheduler scheduler, Work<T> work) {
        return scheduler.supplyAsync(() -> {
            // HikariCP resets autocommit/read-only/catalog state on every
            // connection it hands back to the pool, so there is no need to
            // manually restore autoCommit before this connection is closed.
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T result = work.execute(connection);
                    connection.commit();
                    return result;
                } catch (Exception e) {
                    rollback(connection, e);
                    throw (e instanceof RuntimeException runtimeException)
                            ? runtimeException
                            : new StorageException("Transaction failed, rolled back", e);
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to open a transactional connection", e);
            }
        });
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            StorageException rollbackFailure = new StorageException("Failed to roll back transaction", e);
            rollbackFailure.addSuppressed(cause);
            throw rollbackFailure;
        }
    }

    /** The unit of work run inside one transaction. */
    @FunctionalInterface
    public interface Work<T> {
        T execute(Connection connection) throws SQLException;
    }
}
