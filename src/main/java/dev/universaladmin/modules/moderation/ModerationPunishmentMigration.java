package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code punishments} table. Module migrations start at version
 * 1000 (see {@link Migration}); {@code players} already occupies 1000/1001,
 * so this one starts at 1002.
 */
public final class ModerationPunishmentMigration implements Migration {

    @Override
    public int version() {
        return 1002;
    }

    @Override
    public String description() {
        return "Create punishments table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // SQLite and MySQL/MariaDB spell "auto-incrementing primary key"
        // differently - see docs/architecture/storage.md.
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String idColumn = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        %s,
                        type VARCHAR(16) NOT NULL,
                        target_id VARCHAR(36) NOT NULL,
                        target_last_known_name VARCHAR(16) NOT NULL,
                        target_ip VARCHAR(45),
                        actor_id VARCHAR(36),
                        actor_name VARCHAR(255) NOT NULL,
                        reason VARCHAR(1024),
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT,
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        revoked_at BIGINT,
                        revoked_by VARCHAR(255),
                        metadata TEXT
                    )
                    """.formatted(idColumn));
        }
    }
}
