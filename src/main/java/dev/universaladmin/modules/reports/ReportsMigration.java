package dev.universaladmin.modules.reports;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code ua_reports}. New tables carry the {@code ua_} prefix so
 * they cannot collide with another plugin's tables in a shared MySQL
 * database - see docs/architecture/storage.md.
 */
public final class ReportsMigration implements Migration {

    @Override
    public int version() {
        return 1009;
    }

    @Override
    public String description() {
        return "Create ua_reports table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String idColumn = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_reports (
                        %s,
                        reporter_id VARCHAR(36) NOT NULL,
                        reporter_name VARCHAR(16) NOT NULL,
                        target_id VARCHAR(36) NOT NULL,
                        target_name VARCHAR(16) NOT NULL,
                        reason VARCHAR(512) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        handler_id VARCHAR(36),
                        handler_name VARCHAR(255),
                        resolution VARCHAR(512),
                        world VARCHAR(255),
                        x DOUBLE,
                        y DOUBLE,
                        z DOUBLE,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """.formatted(idColumn));
            statement.execute("CREATE INDEX ua_reports_status ON ua_reports (status)");
            statement.execute("CREATE INDEX ua_reports_target ON ua_reports (target_id)");
        }
    }
}
