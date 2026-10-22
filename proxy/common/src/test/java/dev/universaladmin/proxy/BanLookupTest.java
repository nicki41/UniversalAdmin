package dev.universaladmin.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * The proxy's ban query against the {@code punishments} table as the
 * plugin's own migration creates it (column layout copied from
 * {@code ModerationPunishmentMigration}).
 */
class BanLookupTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final UUID STEVE = UUID.randomUUID();

    @TempDir
    Path dir;

    private DataSource dataSource;
    private BanLookup lookup;

    @BeforeEach
    void setUp() throws SQLException {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + dir.resolve("bans.db"));
        dataSource = sqlite;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
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
                    )""");
        }
        lookup = new BanLookup(dataSource);
    }

    private void insert(String type, UUID target, String ip, String reason, Instant expires, boolean active) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("bans.db"));
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO punishments (type, target_id, target_last_known_name, target_ip, actor_name, reason,
                            created_at, expires_at, active) VALUES (?, ?, 'Steve', ?, 'Mod', ?, ?, ?, ?)""")) {
            statement.setString(1, type);
            statement.setString(2, target.toString());
            statement.setString(3, ip);
            statement.setString(4, reason);
            statement.setLong(5, NOW.minusSeconds(60).toEpochMilli());
            if (expires == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, expires.toEpochMilli());
            }
            statement.setBoolean(7, active);
            statement.executeUpdate();
        }
    }

    @Test
    void noPunishmentMeansNoBan() throws SQLException {
        assertTrue(lookup.activeBan(STEVE, "10.0.0.1", NOW).isEmpty());
    }

    @Test
    void aPermanentBanIsFound() throws SQLException {
        insert("BAN", STEVE, null, "griefing", null, true);

        BanLookup.Ban ban = lookup.activeBan(STEVE, "10.0.0.1", NOW).orElseThrow();
        assertEquals("griefing", ban.reason());
        assertNull(ban.expiresAt());
    }

    @Test
    void aTemporaryBanCountsUntilItExpires() throws SQLException {
        Instant until = NOW.plusSeconds(3600);
        insert("TEMP_BAN", STEVE, null, "spam", until, true);

        assertEquals(until, lookup.activeBan(STEVE, null, NOW).orElseThrow().expiresAt());
        assertTrue(lookup.activeBan(STEVE, null, until.plusSeconds(1)).isEmpty());
    }

    @Test
    void anIpBanMatchesAnyAccountFromThatAddress() throws SQLException {
        insert("IP_BAN", UUID.randomUUID(), "10.0.0.7", "alts", null, true);

        assertTrue(lookup.activeBan(STEVE, "10.0.0.7", NOW).isPresent());
        assertTrue(lookup.activeBan(STEVE, "10.0.0.8", NOW).isEmpty());
        assertTrue(lookup.activeBan(STEVE, null, NOW).isEmpty());
    }

    @Test
    void liftedBansAndOtherPunishmentsDoNotCount() throws SQLException {
        insert("BAN", STEVE, null, "old", null, false);
        insert("MUTE", STEVE, null, "chat", null, true);
        insert("WARN", STEVE, null, "be nice", null, true);

        assertTrue(lookup.activeBan(STEVE, null, NOW).isEmpty());
    }
}
