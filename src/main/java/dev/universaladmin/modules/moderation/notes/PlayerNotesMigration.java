package dev.universaladmin.modules.moderation.notes;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates {@code ua_player_notes} - see {@link PlayerNote}. */
public final class PlayerNotesMigration implements Migration {

    @Override
    public int version() {
        return 1008;
    }

    @Override
    public String description() {
        return "Create ua_player_notes table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String idColumn = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_player_notes (
                        %s,
                        target_id VARCHAR(36) NOT NULL,
                        target_name VARCHAR(16) NOT NULL,
                        author_id VARCHAR(36),
                        author_name VARCHAR(255) NOT NULL,
                        note VARCHAR(512) NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.formatted(idColumn));
            statement.execute("CREATE INDEX ua_player_notes_target ON ua_player_notes (target_id)");
        }
    }
}
