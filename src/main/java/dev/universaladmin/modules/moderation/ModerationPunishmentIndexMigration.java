package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Indexes for the three lookup shapes {@link PunishmentRepository} needs:
 * the join-time ban check and chat-time mute check (both {@code target_id +
 * type + active}), the periodic expiry sweep ({@code active + expires_at}),
 * and the IP-ban join check ({@code target_ip}). Version 1003, right after
 * {@link ModerationPunishmentMigration}'s 1002.
 */
public final class ModerationPunishmentIndexMigration implements Migration {

    @Override
    public int version() {
        return 1003;
    }

    @Override
    public String description() {
        return "Index punishments for active-lookup and expiry-sweep queries";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // No "IF NOT EXISTS" here: MigrationRunner already guarantees this
        // runs at most once, and real MySQL (unlike SQLite/MariaDB) rejects
        // that clause on CREATE INDEX - see docs/architecture/storage.md#dialekt-unterschiede.
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE INDEX idx_punishments_target_type_active ON punishments(target_id, type, active)");
            statement.execute(
                    "CREATE INDEX idx_punishments_active_expires ON punishments(active, expires_at)");
            statement.execute(
                    "CREATE INDEX idx_punishments_target_ip ON punishments(target_ip)");
        }
    }
}
