package dev.universaladmin.modules.moderation.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AdvancedBan's HSQLDB storage rebuilt as a fixture, read with the HSQLDB
 * driver found inside a stand-in "AdvancedBan" jar - as on a real server.
 */
class AdvancedBanImporterTest {

    private static final String NOTCH = "069a79f444e94726a5befca90e38aaf5";
    private static final NoteText NOTE_TEXT = (key, args) -> key + Arrays.toString(args);

    private static final String TABLE = """
            (id INTEGER IDENTITY PRIMARY KEY, name VARCHAR(16), uuid VARCHAR(35), reason VARCHAR(255),
             operator VARCHAR(16), punishmentType VARCHAR(16), start BIGINT, end BIGINT, calculation VARCHAR(50))
            """;

    @Test
    void readsHistoryAndMarksWhatIsStillInForce(@TempDir Path server) throws Exception {
        Path plugins = fixture(server);
        AdvancedBanImporter importer = new AdvancedBanImporter(plugins, false, NOTE_TEXT);
        RecordingSink sink = new RecordingSink();

        assertTrue(importer.locate().orElseThrow().startsWith("HSQLDB"));
        importer.read(sink);

        UUID notch = ImportTargets.uuid(NOTCH).orElseThrow();
        ImportedPunishment ban = sink.bySourceId("history:0");
        assertEquals(PunishmentType.BAN, ban.type());
        assertEquals(notch, ban.targetId());
        assertTrue(ban.active());
        assertNull(ban.expiresAt());

        ImportedPunishment mute = sink.bySourceId("history:1");
        assertEquals(PunishmentType.TEMP_MUTE, mute.type());
        assertFalse(mute.active());

        ImportedPunishment ipBan = sink.bySourceId("history:2");
        assertEquals(PunishmentType.IP_BAN, ipBan.type());
        assertEquals("1.2.3.4", ipBan.targetIp());
        assertEquals(notch, ipBan.targetId()); // the name is known from Notch's other entries
        assertTrue(ipBan.active());

        ImportedPunishment strangerIp = sink.bySourceId("history:3");
        assertEquals(ImportTargets.forIp("5.6.7.8"), strangerIp.targetId());
        assertFalse(strangerIp.active()); // not in Punishments

        assertEquals(PunishmentType.WARN, sink.bySourceId("history:4").type());
        assertEquals(PunishmentType.KICK, sink.bySourceId("history:5").type());
        assertEquals(1, sink.notes.size());
        assertEquals("moderation.import.note.advancedban[watch him]", sink.notes.get(0).text());
        assertEquals(1, sink.skipped(ImportSink.SkipReason.NO_PLAYER)); // "Ghost": a name only, on an online-mode server
    }

    @Test
    void derivesOfflineUuidsWhenAdvancedBanStoredNames(@TempDir Path server) throws Exception {
        AdvancedBanImporter importer = new AdvancedBanImporter(fixture(server), true, NOTE_TEXT);
        RecordingSink sink = new RecordingSink();

        importer.read(sink);

        ImportedPunishment ghost = sink.bySourceId("history:7");
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Ghost".getBytes(StandardCharsets.UTF_8)), ghost.targetId());
        assertEquals(0, sink.skipped(ImportSink.SkipReason.NO_PLAYER));
    }

    private static Path fixture(Path server) throws Exception {
        Path plugins = Files.createDirectories(server.resolve("plugins"));
        Path data = Files.createDirectories(plugins.resolve("AdvancedBan/data"));
        Files.writeString(plugins.resolve("AdvancedBan/config.yml"), "UseMySQL: false\n");
        Path hsqldbJar = Path.of(org.hsqldb.jdbc.JDBCDriver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.copy(hsqldbJar, plugins.resolve("AdvancedBan-2.3.0.jar"), StandardCopyOption.REPLACE_EXISTING);

        long now = System.currentTimeMillis();
        long day = 86_400_000L;
        try (Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + data.resolve("storage").toAbsolutePath() + ";hsqldb.lock_file=false", "SA", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE Punishments " + TABLE);
            statement.execute("CREATE TABLE PunishmentHistory " + TABLE);
            String[] history = {
                row("Notch", NOTCH, "hacking", "Admin", "BAN", now - 5 * day, -1),
                row("Notch", NOTCH, "spam", "Admin", "TEMP_MUTE", now - 4 * day, now - 3 * day),
                row("Notch", "1.2.3.4", "alts", "Admin", "IP_BAN", now - 2 * day, -1),
                row("Stranger", "5.6.7.8", "alts", "CONSOLE", "TEMP_IP_BAN", now - day, now + day),
                row("Notch", NOTCH, "be nice", "Mod", "WARNING", now - day, -1),
                row("Notch", NOTCH, "afk", "Mod", "KICK", now - day, -1),
                row("Notch", NOTCH, "watch him", "Mod", "NOTE", now - day, -1),
                row("Ghost", "Ghost", "?", "Mod", "BAN", now - day, -1),
            };
            for (String values : history) {
                statement.execute("INSERT INTO PunishmentHistory (name, uuid, reason, operator, punishmentType, start, end, calculation) VALUES " + values);
            }
            // In force: the permanent ban, the IP ban, the warning.
            statement.execute("INSERT INTO Punishments (name, uuid, reason, operator, punishmentType, start, end, calculation) VALUES " + history[0]);
            statement.execute("INSERT INTO Punishments (name, uuid, reason, operator, punishmentType, start, end, calculation) VALUES " + history[2]);
            statement.execute("INSERT INTO Punishments (name, uuid, reason, operator, punishmentType, start, end, calculation) VALUES " + history[4]);
            statement.execute("SHUTDOWN");
        }
        return plugins;
    }

    private static String row(String name, String uuid, String reason, String operator, String type, long start, long end) {
        return "('" + name + "', '" + uuid + "', '" + reason + "', '" + operator + "', '" + type + "', " + start + ", " + end + ", NULL)";
    }
}
