package dev.universaladmin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {

    @Test
    void appliesMigrationsInOrderExactlyOnce(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(createTableMigration());
            runner.register(insertRowMigration());

            runner.runPending();
            runner.runPending(); // must be idempotent

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM widgets")) {
                resultSet.next();
                assertEquals(1, resultSet.getInt(1));
            }
            // Each migration is recorded exactly once in schema_version, even
            // though runPending() above ran twice - proves migrations don't
            // silently re-apply on every plugin start.
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                resultSet.next();
                assertEquals(2, resultSet.getInt(1));
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private Migration createTableMigration() {
        return new Migration() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "create widgets table";
            }

            @Override
            public void migrate(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE widgets (id INTEGER PRIMARY KEY)");
                }
            }
        };
    }

    private Migration insertRowMigration() {
        return new Migration() {
            @Override
            public int version() {
                return 2;
            }

            @Override
            public String description() {
                return "seed one widget";
            }

            @Override
            public void migrate(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO widgets (id) VALUES (1)");
                }
            }
        };
    }
}
