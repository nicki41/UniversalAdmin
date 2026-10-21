package dev.universaladmin.modules.moderation.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaImporterTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void readsPlayerAndIpBansWithDatesAndSources(@TempDir Path server) throws Exception {
        Files.writeString(server.resolve("banned-players.json"), """
                [
                  {"uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch", "created": "2024-05-01 18:30:00 +0200",
                   "source": "Server", "expires": "forever", "reason": "Banned by an operator."},
                  {"uuid": "6b2b3a4c-1d2e-4f50-8a7b-9c0d1e2f3a4b", "name": "Griefer", "created": "2024-05-02 10:00:00 +0000",
                   "source": "Moderator", "expires": "2030-01-01 00:00:00 +0000", "reason": "griefing"},
                  {"uuid": "not-a-uuid", "name": "Broken"}
                ]
                """);
        Files.writeString(server.resolve("banned-ips.json"), """
                [{"ip": "10.0.0.7", "created": "2024-05-03 12:00:00 +0000", "source": "Console", "expires": "forever", "reason": "alts"}]
                """);
        VanillaImporter importer = new VanillaImporter(List.of(server), (players, addresses) -> { });
        RecordingSink sink = new RecordingSink();

        importer.read(sink);

        ImportedPunishment notch = sink.bySourceId("player:" + NOTCH);
        assertEquals(PunishmentType.BAN, notch.type());
        assertEquals("Notch", notch.targetName());
        assertEquals("CONSOLE", notch.actorName());
        assertNull(notch.expiresAt());
        assertEquals(ZonedDateTime.parse("2024-05-01T18:30:00+02:00").toInstant(), notch.createdAt());

        ImportedPunishment griefer = sink.bySourceId("player:6b2b3a4c-1d2e-4f50-8a7b-9c0d1e2f3a4b");
        assertEquals(PunishmentType.TEMP_BAN, griefer.type());
        assertEquals("Moderator", griefer.actorName());
        assertEquals(ZonedDateTime.parse("2030-01-01T00:00:00Z").toInstant(), griefer.expiresAt());

        ImportedPunishment ip = sink.bySourceId("ip:10.0.0.7");
        assertEquals(PunishmentType.IP_BAN, ip.type());
        assertEquals("10.0.0.7", ip.targetIp());
        assertEquals("10.0.0.7", ip.targetName());
        assertEquals(ImportTargets.forIp("10.0.0.7"), ip.targetId());

        assertEquals(1, sink.skipped(ImportSink.SkipReason.NO_PLAYER));
        assertEquals("banned-players.json, banned-ips.json", importer.locate().orElseThrow());
    }

    @Test
    void movesImportedEntriesOffTheVanillaLists(@TempDir Path server) throws Exception {
        List<UUID> removedPlayers = new ArrayList<>();
        List<String> removedAddresses = new ArrayList<>();
        VanillaImporter importer = new VanillaImporter(List.of(server), (players, addresses) -> {
            removedPlayers.addAll(players);
            removedAddresses.addAll(addresses);
        });

        importer.afterImport(Set.of("player:" + NOTCH, "ip:10.0.0.7"));

        assertEquals(List.of(NOTCH), removedPlayers);
        assertEquals(List.of("10.0.0.7"), removedAddresses);
    }

    @Test
    void reportsMissingFiles(@TempDir Path server) {
        VanillaImporter importer = new VanillaImporter(List.of(server), (players, addresses) -> { });
        assertTrue(importer.locate().isEmpty());
        ImportException error = assertThrows(ImportException.class, () -> importer.read(new RecordingSink()));
        assertEquals("moderation.import.error.not-found", error.messageKey().value());
    }
}
