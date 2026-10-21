package dev.universaladmin.modules.moderation.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LiteBans' schema rebuilt as a fixture - in SQLite, and in H2 read through
 * a driver "borrowed" from a copy of the H2 jar in the fixture's LiteBans
 * folder, the way a real server has it.
 */
class LiteBansImporterTest {

    private static final String NOTCH = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String GRIEFER = "6b2b3a4c-1d2e-4f50-8a7b-9c0d1e2f3a4b";
    private static final String STAFF = "11111111-2222-3333-4444-555555555555";

    private static final String COLUMNS = """
            id BIGINT PRIMARY KEY, uuid VARCHAR(36), ip VARCHAR(45), reason VARCHAR(2048),
            banned_by_uuid VARCHAR(36), banned_by_name VARCHAR(128), removed_by_uuid VARCHAR(36),
            removed_by_name VARCHAR(128), removed_by_reason VARCHAR(2048), removed_by_date BIGINT,
            time BIGINT, until BIGINT, server_scope VARCHAR(32), server_origin VARCHAR(32),
            silent BOOLEAN, ipban BOOLEAN, ipban_wildcard BOOLEAN, active BOOLEAN
            """;

    @Test
    void readsEveryKindFromSqlite(@TempDir Path server) throws Exception {
        Path folder = Files.createDirectories(server.resolve("plugins/LiteBans"));
        Files.writeString(folder.resolve("config.yml"), """
                sql:
                  driver: SQLite
                  table_prefix: 'litebans_'
                """);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("litebans.db"))) {
            fill(connection);
        }
        LiteBansImporter importer = new LiteBansImporter(server.resolve("plugins"), server.resolve("libraries"));
        RecordingSink sink = new RecordingSink();

        importer.read(sink);

        assertFixture(sink);
        assertTrue(importer.locate().orElseThrow().startsWith("SQLite"));
    }

    @Test
    void readsTheEmbeddedH2DatabaseWithLiteBansOwnDriver(@TempDir Path server) throws Exception {
        Path folder = Files.createDirectories(server.resolve("plugins/LiteBans"));
        Files.writeString(folder.resolve("config.yml"), """
                sql:
                  driver: H2
                  username: ''
                  password: ''
                  table_prefix: 'litebans_'
                """);
        // The driver as LiteBans ships it: a jar in its own folder.
        Path h2Jar = Path.of(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.createDirectories(folder.resolve("lib"));
        Files.copy(h2Jar, folder.resolve("lib/h2.jar"), StandardCopyOption.REPLACE_EXISTING);
        try (Connection connection = DriverManager.getConnection("jdbc:h2:" + folder.resolve("litebans").toAbsolutePath() + ";MODE=MySQL")) {
            fill(connection);
        }
        LiteBansImporter importer = new LiteBansImporter(server.resolve("plugins"), server.resolve("libraries"));
        RecordingSink sink = new RecordingSink();

        importer.read(sink);

        assertFixture(sink);
        assertTrue(importer.locate().orElseThrow().startsWith("H2"));
    }

    @Test
    void explainsAMissingDriver(@TempDir Path server) throws Exception {
        Path folder = Files.createDirectories(server.resolve("plugins/LiteBans"));
        Files.writeString(folder.resolve("config.yml"), "sql:\n  driver: H2\n");
        Files.writeString(folder.resolve("litebans.mv.db"), "");
        LiteBansImporter importer = new LiteBansImporter(server.resolve("plugins"), server.resolve("libraries"));

        ImportException error = assertThrows(ImportException.class, () -> importer.read(new RecordingSink()));

        assertEquals("moderation.import.error.driver", error.messageKey().value());
    }

    @Test
    void isNotFoundWithoutLiteBans(@TempDir Path server) {
        assertFalse(new LiteBansImporter(server.resolve("plugins"), server.resolve("libraries")).locate().isPresent());
    }

    private static void fill(Connection connection) throws Exception {
        long day = 86_400_000L;
        long now = System.currentTimeMillis();
        try (Statement statement = connection.createStatement()) {
            for (String table : new String[] {"bans", "mutes", "warnings", "kicks"}) {
                statement.execute("CREATE TABLE litebans_" + table + " (" + COLUMNS + ")");
            }
            statement.execute("CREATE TABLE litebans_history (id BIGINT PRIMARY KEY, date BIGINT, name VARCHAR(16), uuid VARCHAR(36), ip VARCHAR(45))");
            statement.execute("INSERT INTO litebans_history VALUES (1, " + (now - 10 * day) + ", 'OldNotch', '" + NOTCH + "', '1.2.3.4')");
            statement.execute("INSERT INTO litebans_history VALUES (2, " + (now - day) + ", 'Notch', '" + NOTCH + "', '1.2.3.4')");
            statement.execute("INSERT INTO litebans_history VALUES (3, " + (now - day) + ", 'Griefer', '" + GRIEFER + "', '5.6.7.8')");

            // 1: permanent, in force   2: temporary, lifted by a moderator   3: ran out ("#expired")
            statement.execute(ban(1, NOTCH, "1.2.3.4", "hacking", now - 5 * day, -1, true, null, false, false, "lobby"));
            statement.execute(ban(2, GRIEFER, "5.6.7.8", "griefing", now - 4 * day, now + day, false, "Moderator", false, false, null));
            statement.execute(ban(3, GRIEFER, "5.6.7.8", "spam", now - 9 * day, now - 8 * day, false, "#expired", false, false, null));
            // 4: IP ban without a player   5: wildcard IP ban (no equivalent)   6: no player at all
            statement.execute(ban(4, "#offline#", "9.9.9.9", "alts", now - day, -1, true, null, true, false, null));
            statement.execute(ban(5, "#offline#", "9.9.*.*", "range", now - day, -1, true, null, true, true, null));
            statement.execute(ban(6, null, null, "?", now - day, -1, true, null, false, false, null));

            statement.execute(insert("mutes", 1, GRIEFER, "5.6.7.8", "caps", now - day, now + day, true, null, false, false, null));
            statement.execute(insert("mutes", 2, "#offline#", "5.6.7.8", "ip mute", now - day, -1, true, null, true, false, null));
            statement.execute(insert("warnings", 1, NOTCH, null, "language", now - 2 * day, -1, true, null, false, false, null));
            statement.execute(insert("kicks", 1, NOTCH, null, "afk", now - 3 * day, 0, false, null, false, false, null));
        }
    }

    private static String ban(long id, String uuid, String ip, String reason, long time, long until, boolean active,
            String removedBy, boolean ipban, boolean wildcard, String server) {
        return insert("bans", id, uuid, ip, reason, time, until, active, removedBy, ipban, wildcard, server);
    }

    private static String insert(String table, long id, String uuid, String ip, String reason, long time, long until,
            boolean active, String removedBy, boolean ipban, boolean wildcard, String server) {
        return "INSERT INTO litebans_" + table + " (id, uuid, ip, reason, banned_by_uuid, banned_by_name, removed_by_name,"
                + " removed_by_date, time, until, server_origin, ipban, ipban_wildcard, active) VALUES ("
                + id + ", " + quote(uuid) + ", " + quote(ip) + ", " + quote(reason) + ", " + quote(STAFF) + ", 'Admin', "
                + quote(removedBy) + ", " + (removedBy == null ? "NULL" : String.valueOf(time + 1000)) + ", "
                + time + ", " + until + ", " + quote(server) + ", " + ipban + ", " + wildcard + ", " + active + ")";
    }

    private static String quote(String value) {
        return value == null ? "NULL" : "'" + value + "'";
    }

    private static void assertFixture(RecordingSink sink) {
        ImportedPunishment permanent = sink.bySourceId("ban:1");
        assertEquals(PunishmentType.BAN, permanent.type());
        assertEquals(UUID.fromString(NOTCH), permanent.targetId());
        assertEquals("Notch", permanent.targetName()); // the newest name in history
        assertEquals(UUID.fromString(STAFF), permanent.actorId());
        assertEquals("Admin", permanent.actorName());
        assertNull(permanent.expiresAt());
        assertTrue(permanent.active());
        assertEquals("lobby", permanent.server());

        ImportedPunishment lifted = sink.bySourceId("ban:2");
        assertEquals(PunishmentType.TEMP_BAN, lifted.type());
        assertFalse(lifted.active());
        assertEquals("Moderator", lifted.revokedBy());

        ImportedPunishment expired = sink.bySourceId("ban:3");
        assertFalse(expired.active());
        assertNull(expired.revokedBy());

        ImportedPunishment ipBan = sink.bySourceId("ban:4");
        assertEquals(PunishmentType.IP_BAN, ipBan.type());
        assertEquals("9.9.9.9", ipBan.targetIp());
        assertEquals(ImportTargets.forIp("9.9.9.9"), ipBan.targetId());

        assertTrue(sink.find("ban:5").isEmpty());
        assertTrue(sink.find("ban:6").isEmpty());
        assertEquals(PunishmentType.TEMP_MUTE, sink.bySourceId("mute:1").type());
        assertTrue(sink.find("mute:2").isEmpty());
        assertEquals(PunishmentType.WARN, sink.bySourceId("warning:1").type());
        ImportedPunishment kick = sink.bySourceId("kick:1");
        assertEquals(PunishmentType.KICK, kick.type());
        assertFalse(kick.active());

        assertEquals(2, sink.skipped(ImportSink.SkipReason.UNSUPPORTED)); // wildcard ban, IP-only mute
        assertEquals(1, sink.skipped(ImportSink.SkipReason.NO_PLAYER));
        assertTrue(sink.bySourceId("ban:1").createdAt().isBefore(Instant.now()));
    }
}
