package dev.universaladmin.modules.moderation.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EssentialsImporterTest {

    private static final NoteText NOTE_TEXT = (key, args) -> key + Arrays.toString(args);

    @Test
    void importsMutesAndTurnsJailsIntoNotes(@TempDir Path server) throws Exception {
        Path userdata = Files.createDirectories(server.resolve("plugins/Essentials/userdata"));
        Files.writeString(userdata.resolve("069a79f4-44e9-4726-a5be-fca90e38aaf5.yml"), """
                last-account-name: Notch
                muted: true
                mute-reason: spam
                timestamps:
                  mute: 4102444800000
                """);
        Files.writeString(userdata.resolve("6b2b3a4c-1d2e-4f50-8a7b-9c0d1e2f3a4b.yml"), """
                last-account-name: Jailbird
                muted: true
                jailed: true
                jail: spawnjail
                timestamps:
                  mute: 0
                  jail: 0
                """);
        Files.writeString(userdata.resolve("11111111-2222-3333-4444-555555555555.yml"), """
                last-account-name: Innocent
                muted: false
                """);
        Files.writeString(userdata.resolve("broken-name.yml"), "muted: true\n");
        EssentialsImporter importer = new EssentialsImporter(server.resolve("plugins"), NOTE_TEXT);
        RecordingSink sink = new RecordingSink();

        importer.read(sink);

        assertEquals(2, sink.punishments.size());
        ImportedPunishment notch = sink.bySourceId("mute:069a79f4-44e9-4726-a5be-fca90e38aaf5:4102444800000");
        assertEquals(PunishmentType.TEMP_MUTE, notch.type());
        assertEquals("Notch", notch.targetName());
        assertEquals("spam", notch.reason());
        assertEquals(Instant.ofEpochMilli(4102444800000L), notch.expiresAt());
        assertEquals("EssentialsX", notch.actorName());

        ImportedPunishment jailbird = sink.bySourceId("mute:6b2b3a4c-1d2e-4f50-8a7b-9c0d1e2f3a4b:0");
        assertEquals(PunishmentType.MUTE, jailbird.type());
        assertNull(jailbird.expiresAt());

        assertEquals(1, sink.notes.size());
        ImportedNote note = sink.notes.get(0);
        assertEquals("Jailbird", note.targetName());
        assertEquals("moderation.import.note.essentials-jail[spawnjail]", note.text());
        assertEquals(1, sink.skipped(ImportSink.SkipReason.NO_PLAYER));
        assertTrue(importer.locate().isPresent());
    }
}
