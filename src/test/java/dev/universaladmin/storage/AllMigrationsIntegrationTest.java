package dev.universaladmin.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.audit.AuditSchemaMigration;
import dev.universaladmin.audit.AuditSchemaMigrationV2;
import dev.universaladmin.modules.moderation.ModerationPunishmentIndexMigration;
import dev.universaladmin.modules.moderation.ModerationPunishmentMigration;
import dev.universaladmin.modules.moderation.StaffModeSnapshotMigration;
import dev.universaladmin.modules.moderation.VanishStateMigration;
import dev.universaladmin.modules.players.PlayerProfileIndexMigration;
import dev.universaladmin.modules.players.PlayerProfileMigration;
import dev.universaladmin.modules.server.ServerMaintenanceMigration;
import dev.universaladmin.modules.whitelist.WhitelistMigration;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every real, production {@link Migration} registered together against one
 * fresh SQLite database - each migration already has its own focused test
 * (e.g. {@code WhitelistMigrationTest}), but nothing until now verified the
 * *whole set* applies together without a version collision or a migration
 * that implicitly depends on another module's schema being present. Also
 * covers the "upgrade" scenario release testing asked for: applying an
 * older subset of migrations first, then the full set later, mirrors an
 * admin updating the plugin and picking up a migration that didn't exist
 * yet on their previous install.
 */
class AllMigrationsIntegrationTest {

    @Test
    void everyBuiltInMigrationAppliesCleanlyToAFreshDatabaseAndIsIdempotent(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("fresh.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            registerAllBuiltInMigrations(runner);

            assertDoesNotThrow(runner::runPending);
            assertDoesNotThrow(runner::runPending); // a restart against an already-migrated DB must not re-apply or fail

            assertEquals(10, appliedMigrationCount(dataSource));
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void aMigrationAddedAfterAPreviousInstallStillAppliesOnTopOfExistingData(@TempDir Path dir) throws Exception {
        DatabaseConfig config = DatabaseConfig.sqlite("upgrade.db");
        DataSource dataSource = DataSourceFactory.create(config, dir);
        try {
            // Simulates a server already running an older UniversalAdmin
            // version that shipped every migration except the whitelist one.
            MigrationRunner previousVersion = new MigrationRunner(dataSource, Logger.getLogger("test"));
            previousVersion.register(new AuditSchemaMigration());
            previousVersion.register(new AuditSchemaMigrationV2());
            previousVersion.register(new PlayerProfileMigration());
            previousVersion.register(new PlayerProfileIndexMigration());
            previousVersion.register(new ModerationPunishmentMigration());
            previousVersion.register(new ModerationPunishmentIndexMigration());
            previousVersion.register(new VanishStateMigration());
            previousVersion.register(new StaffModeSnapshotMigration());
            previousVersion.register(new ServerMaintenanceMigration());
            previousVersion.runPending();
            assertEquals(9, appliedMigrationCount(dataSource));

            // The "upgrade": same database, a newer plugin build that also
            // registers the whitelist migration added since.
            MigrationRunner newVersion = new MigrationRunner(dataSource, Logger.getLogger("test"));
            registerAllBuiltInMigrations(newVersion);
            assertDoesNotThrow(newVersion::runPending);

            assertEquals(10, appliedMigrationCount(dataSource));
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM whitelist_entries")) {
                resultSet.next();
                assertEquals(0, resultSet.getInt(1)); // table exists and is queryable, just empty
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private void registerAllBuiltInMigrations(MigrationRunner runner) {
        runner.register(new AuditSchemaMigration());
        runner.register(new AuditSchemaMigrationV2());
        runner.register(new PlayerProfileMigration());
        runner.register(new PlayerProfileIndexMigration());
        runner.register(new ModerationPunishmentMigration());
        runner.register(new ModerationPunishmentIndexMigration());
        runner.register(new VanishStateMigration());
        runner.register(new StaffModeSnapshotMigration());
        runner.register(new ServerMaintenanceMigration());
        runner.register(new WhitelistMigration());
    }

    private int appliedMigrationCount(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
