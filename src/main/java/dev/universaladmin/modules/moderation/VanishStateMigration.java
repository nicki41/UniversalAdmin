package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the {@code vanish_state} table - version 1004, right after {@link ModerationPunishmentIndexMigration}'s 1003. */
public final class VanishStateMigration implements Migration {

    @Override
    public int version() {
        return 1004;
    }

    @Override
    public String description() {
        return "Create vanish_state table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS vanish_state (
                        player_id VARCHAR(36) PRIMARY KEY,
                        vanished_at BIGINT NOT NULL
                    )
                    """);
        }
    }
}
