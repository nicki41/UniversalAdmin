package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code staff_mode_snapshots} table - version 1005, right
 * after {@link VanishStateMigration}'s 1004. {@code MEDIUMBLOB} on
 * MySQL/MariaDB (its plain {@code BLOB} defaults to a 64KB cap, too tight
 * for a full 41-slot inventory with heavy-NBT items like written books);
 * SQLite's {@code BLOB} has no such practical limit - same dialect-branch
 * idiom every other migration in this module uses.
 */
public final class StaffModeSnapshotMigration implements Migration {

    @Override
    public int version() {
        return 1005;
    }

    @Override
    public String description() {
        return "Create staff_mode_snapshots table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String blobColumn = sqlite ? "BLOB" : "MEDIUMBLOB";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_mode_snapshots (
                        player_id VARCHAR(36) PRIMARY KEY,
                        inventory_data %s NOT NULL,
                        gamemode VARCHAR(16) NOT NULL,
                        exp FLOAT NOT NULL,
                        level INT NOT NULL,
                        allow_flight BOOLEAN NOT NULL,
                        flying BOOLEAN NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.formatted(blobColumn));
        }
    }
}
