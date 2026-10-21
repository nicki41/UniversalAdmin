package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code ua_import_records}: one row per entry imported from another
 * plugin, so running the same import again skips what is already there (see
 * {@link ImportRepository}).
 */
public final class ImportRecordsMigration implements Migration {

    @Override
    public int version() {
        return 1011;
    }

    @Override
    public String description() {
        return "Create ua_import_records table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_import_records (
                        source VARCHAR(32) NOT NULL,
                        source_id VARCHAR(128) NOT NULL,
                        kind VARCHAR(16) NOT NULL,
                        imported_at BIGINT NOT NULL,
                        PRIMARY KEY (source, source_id)
                    )
                    """);
        }
    }
}
