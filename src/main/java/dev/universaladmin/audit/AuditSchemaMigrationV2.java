package dev.universaladmin.audit;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Widens {@code audit_log} (created by {@link AuditSchemaMigration}, version 1)
 * with every field the full "AUDIT ENTRY" shape needs (see {@link AuditEvent}) -
 * a new migration rather than editing version 1, since migrations are
 * forward-only (see docs/architecture/storage.md). Every added column is
 * nullable (or has a default), so existing rows from version 1 stay valid
 * without a backfill.
 *
 * <p>Column type names here (VARCHAR/TEXT/BOOLEAN/DOUBLE/BIGINT) are ones
 * both SQLite (dynamically typed, accepts any declared type via type
 * affinity) and MySQL/MariaDB understand identically, so unlike
 * {@link AuditSchemaMigration}'s id column this migration does not need to
 * branch on the driver - see docs/architecture/storage.md#dialekt-unterschiede.
 */
public final class AuditSchemaMigrationV2 implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Widen audit_log with action/module/target/source/success/reason/old-new/world-position/metadata/correlation columns";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            addColumn(statement, "action_id VARCHAR(255)");
            addColumn(statement, "module VARCHAR(64)");
            addColumn(statement, "target_type VARCHAR(64)");
            addColumn(statement, "target_display_name VARCHAR(255)");
            addColumn(statement, "source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM'");
            addColumn(statement, "success BOOLEAN NOT NULL DEFAULT TRUE");
            addColumn(statement, "reason VARCHAR(1024)");
            addColumn(statement, "old_value TEXT");
            addColumn(statement, "new_value TEXT");
            addColumn(statement, "world VARCHAR(255)");
            addColumn(statement, "pos_x DOUBLE");
            addColumn(statement, "pos_y DOUBLE");
            addColumn(statement, "pos_z DOUBLE");
            addColumn(statement, "metadata TEXT");
            addColumn(statement, "correlation_id VARCHAR(64)");

            statement.execute("CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at)");
            statement.execute("CREATE INDEX idx_audit_log_actor ON audit_log (actor_type, actor_id)");
            statement.execute("CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id)");
            statement.execute("CREATE INDEX idx_audit_log_action ON audit_log (action_id)");
            statement.execute("CREATE INDEX idx_audit_log_module ON audit_log (module)");
            statement.execute("CREATE INDEX idx_audit_log_source ON audit_log (source)");
        }
    }

    private void addColumn(Statement statement, String columnDefinition) throws SQLException {
        statement.execute("ALTER TABLE audit_log ADD COLUMN " + columnDefinition);
    }
}
