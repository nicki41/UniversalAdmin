package dev.universaladmin.audit;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code audit_log} table. This is a core migration (version
 * below 1000), registered directly by {@link dev.universaladmin.bootstrap.UniversalAdminPlugin}
 * rather than by a module, since audit logging is a core service every
 * module can depend on. See docs/architecture/storage.md.
 */
public final class AuditSchemaMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create audit_log table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // SQLite and MySQL/MariaDB spell "auto-incrementing primary key"
        // differently; every migration that needs one branches on the driver
        // like this rather than assuming SQLite. See docs/architecture/storage.md.
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String idColumn = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        %s,
                        event_type VARCHAR(255) NOT NULL,
                        actor_type VARCHAR(32) NOT NULL,
                        actor_id VARCHAR(36),
                        actor_name VARCHAR(255) NOT NULL,
                        summary VARCHAR(1024) NOT NULL,
                        target_id VARCHAR(255),
                        occurred_at BIGINT NOT NULL
                    )
                    """.formatted(idColumn));
        }
    }
}
