package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds {@code punishments.mute_kind} - where a mute silences the player
 * ({@code CHAT}, {@code VOICE} or {@code FULL}, see ADR 0022). Version 1016.
 * {@code NULL} reads as {@code FULL}: every row before it, every row that is
 * not a mute, and every row a server of a network on an older version
 * inserts.
 */
public final class MuteKindMigration implements Migration {

    @Override
    public int version() {
        return 1016;
    }

    @Override
    public String description() {
        return "Add punishments.mute_kind for chat, voice and full mutes";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE punishments ADD COLUMN mute_kind VARCHAR(8)");
        }
    }
}
