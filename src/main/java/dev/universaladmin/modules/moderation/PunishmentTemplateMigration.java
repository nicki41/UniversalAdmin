package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds {@code punishments.template} - the punishment template a row was
 * issued with ({@code /punish}), {@code null} for every other row - and an
 * index for the escalation lookup ({@code target_id + template}). Version
 * 1013. The column is nullable, so servers of a network that still run an
 * older version keep inserting rows without it.
 */
public final class PunishmentTemplateMigration implements Migration {

    @Override
    public int version() {
        return 1013;
    }

    @Override
    public String description() {
        return "Add punishments.template for punishment templates";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // No "IF NOT EXISTS": MigrationRunner runs this at most once, and real
        // MySQL rejects the clause on CREATE INDEX - see ModerationPunishmentIndexMigration.
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE punishments ADD COLUMN template VARCHAR(64)");
            statement.execute("CREATE INDEX idx_punishments_target_template ON punishments(target_id, template)");
        }
    }
}
