package dev.universaladmin.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * What the proxy refuses, and which refusal a bypass node lifts - the logic
 * both proxy plugins share - against the tables as the plugin's migrations
 * create them.
 */
class ProxyChecksTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    @TempDir
    Path dir;

    private SQLiteDataSource dataSource;
    private ProxyChecks checks;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dir.resolve("proxy.db"));
        execute("""
                CREATE TABLE punishments (id INTEGER PRIMARY KEY AUTOINCREMENT, type VARCHAR(16) NOT NULL,
                    target_id VARCHAR(36) NOT NULL, target_last_known_name VARCHAR(16) NOT NULL, target_ip VARCHAR(45),
                    actor_id VARCHAR(36), actor_name VARCHAR(255) NOT NULL, reason VARCHAR(1024), created_at BIGINT NOT NULL,
                    expires_at BIGINT, active BOOLEAN NOT NULL DEFAULT TRUE, revoked_at BIGINT, revoked_by VARCHAR(255),
                    metadata TEXT, template VARCHAR(64))""", """
                CREATE TABLE ua_maintenance (scope VARCHAR(100) PRIMARY KEY, enabled BOOLEAN NOT NULL, reason VARCHAR(255),
                    message VARCHAR(500), allowed_players TEXT NOT NULL, updated_at BIGINT NOT NULL, updated_by VARCHAR(64))""", """
                CREATE TABLE ua_whitelist (scope VARCHAR(100) NOT NULL, player_id VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL, source VARCHAR(32) NOT NULL, added_by_id VARCHAR(36),
                    added_by_name VARCHAR(64) NOT NULL, added_at BIGINT NOT NULL, reason VARCHAR(255), notes VARCHAR(255),
                    expires_at BIGINT, PRIMARY KEY (scope, player_id))""", """
                CREATE TABLE ua_whitelist_state (scope VARCHAR(100) PRIMARY KEY, enabled BOOLEAN NOT NULL,
                    updated_at BIGINT NOT NULL, updated_by VARCHAR(64))""");
        ProxySettings settings = new ProxySettings("localhost", 3306, "universaladmin", "universaladmin", "", false, 1,
                true, true, "Banned: <reason>", "Banned: <reason> until <expires>", "no reason", "yyyy-MM-dd HH:mm",
                true, "<red>Maintenance", "Maintenance", "Network maintenance", true, "Not whitelisted", true);
        checks = new ProxyChecks(settings, new ProxyLog() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }
        });
    }

    /** Switches the checks on - after the test wrote its rows, since they read the state once at start and keep it briefly. */
    private ProxyChecks started() {
        checks.use(dataSource);
        return checks;
    }

    private void execute(String... sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String line : sql) {
                statement.execute(line);
            }
        }
    }

    @Test
    void anOpenNetworkLetsEveryoneIn() {
        ProxyChecks checks = started();
        assertTrue(checks.active());
        assertTrue(checks.check(STEVE, "Steve", "10.0.0.1").isEmpty());
        assertTrue(checks.ping().isEmpty());
    }

    @Test
    void aBanRefusesAndNoNodeLiftsIt() throws SQLException {
        execute("INSERT INTO punishments (type, target_id, target_last_known_name, actor_name, reason, created_at, active) "
                + "VALUES ('BAN', '" + STEVE + "', 'Steve', 'Mod', 'griefing', 1, TRUE)");
        execute("INSERT INTO ua_maintenance VALUES ('network', TRUE, 'update', NULL, '', 1, 'Admin')");
        ProxyChecks checks = started();

        List<ProxyChecks.Refusal> refusals = checks.check(STEVE, "Steve", "10.0.0.1");

        assertEquals(List.of(ProxyChecks.Kind.BANNED), refusals.stream().map(ProxyChecks.Refusal::kind).toList());
        assertEquals("Banned: griefing", plain(checks.refusalFor(refusals, permission -> true).orElseThrow()));
    }

    @Test
    void maintenanceAndTheWhitelistRefuseUnlessTheBypassNodeIsHeld() throws SQLException {
        execute("INSERT INTO ua_maintenance VALUES ('network', TRUE, 'update', NULL, 'nick', 1, 'Admin')",
                "INSERT INTO ua_whitelist_state VALUES ('network', TRUE, 1, 'Admin')",
                "INSERT INTO ua_whitelist VALUES ('network', '" + ADMIN + "', 'Nick', 'UNIVERSAL_ADMIN', NULL, 'Admin', 1, NULL, NULL, NULL)");
        ProxyChecks checks = started();

        List<ProxyChecks.Refusal> steve = checks.check(STEVE, "Steve", "10.0.0.1");
        assertEquals(List.of(ProxyChecks.Kind.MAINTENANCE, ProxyChecks.Kind.NOT_WHITELISTED),
                steve.stream().map(ProxyChecks.Refusal::kind).toList());
        assertEquals("Network maintenance", plain(checks.refusalFor(steve, permission -> false).orElseThrow()));
        Set<String> maintenanceBypass = Set.of(ProxyChecks.BYPASS_MAINTENANCE);
        assertEquals("Not whitelisted", plain(checks.refusalFor(steve, maintenanceBypass::contains).orElseThrow()));
        Set<String> both = Set.of(ProxyChecks.BYPASS_MAINTENANCE, ProxyChecks.BYPASS_NETWORK_WHITELIST);
        assertTrue(checks.refusalFor(steve, both::contains).isEmpty());

        // On the maintenance allow-list and the network whitelist: nothing to refuse.
        assertTrue(checks.check(ADMIN, "Nick", "10.0.0.2").isEmpty());
        assertTrue(checks.ping().isPresent());
    }

    private static String plain(ProxyChecks.Refusal refusal) {
        return PlainTextComponentSerializer.plainText().serialize(refusal.message());
    }
}
