package dev.universaladmin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link Transactions} against a real (temporary) SQLite database -
 * same convention as {@link MigrationRunnerTest}, see docs/development/testing.md.
 */
class TransactionsTest {

    // Runs on CompletableFuture's default (async) executor rather than the
    // calling thread, so an exception thrown by the work surfaces the same
    // way it would with the real PaperTaskScheduler: wrapped in a
    // CompletionException when the caller joins the future.
    private static final TaskScheduler ASYNC_SCHEDULER = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            return CompletableFuture.supplyAsync(task);
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            return CompletableFuture.runAsync(task);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    };

    @Test
    void commitsAllStatementsOnSuccess(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("tx.db"), dir);
        try {
            createWidgetsTable(dataSource);

            Transactions.<Void>run(dataSource, ASYNC_SCHEDULER, connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO widgets (id) VALUES (1)");
                    statement.execute("INSERT INTO widgets (id) VALUES (2)");
                }
                return null;
            }).join();

            assertEquals(2, countWidgets(dataSource));
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void rollsBackEveryStatementWhenALaterStatementFails(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("tx.db"), dir);
        try {
            createWidgetsTable(dataSource);

            CompletableFuture<Void> result = Transactions.<Void>run(dataSource, ASYNC_SCHEDULER, connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO widgets (id) VALUES (1)");
                    // Duplicate primary key - fails, so the first insert above
                    // must be rolled back too, not left half-applied.
                    statement.execute("INSERT INTO widgets (id) VALUES (1)");
                }
                return null;
            });

            assertThrows(CompletionException.class, result::join);
            assertEquals(0, countWidgets(dataSource));
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private void createWidgetsTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE widgets (id INTEGER PRIMARY KEY)");
        }
    }

    private int countWidgets(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM widgets")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
