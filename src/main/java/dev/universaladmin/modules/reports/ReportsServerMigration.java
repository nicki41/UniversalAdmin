package dev.universaladmin.modules.reports;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Adds {@code ua_reports.server}: the network server a report was filed on ({@code NULL} outside network mode). */
public final class ReportsServerMigration implements Migration {

    @Override
    public int version() {
        return 1010;
    }

    @Override
    public String description() {
        return "Add server column to ua_reports";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ua_reports ADD COLUMN server VARCHAR(64)");
        }
    }
}
