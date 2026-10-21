package dev.universaladmin.modules.moderation.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ImportServiceTest {

    private static final UUID STEVE = UUID.randomUUID();

    @Test
    void previewCountsAndWritesNothing() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        ImportService service = service(new FixedImporter(entries(3)), repository, new RecordingDirectory());

        ImportSummary preview = service.preview(service.importer("fixed").orElseThrow());

        assertEquals(3, preview.totalPunishments());
        assertEquals(1, preview.count(PunishmentType.TEMP_BAN));
        assertEquals(1, preview.activeCount(PunishmentType.BAN));
        assertEquals(1, preview.notes());
        assertEquals(1, preview.totalSkipped());
        assertTrue(repository.written.isEmpty());
    }

    @Test
    void importsInBatchesAndSkipsWhatAnEarlierRunImported() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        RecordingDirectory directory = new RecordingDirectory();
        FixedImporter importer = new FixedImporter(entries(ImportService.BATCH_SIZE + 10));
        ImportService service = service(importer, repository, directory);
        List<Integer> progress = new ArrayList<>();

        ImportSummary first = service.run(importer, progress::add);

        assertEquals(ImportService.BATCH_SIZE + 10, first.totalPunishments());
        assertEquals(1, first.notes());
        assertEquals(2, repository.batches); // one full batch, then the rest
        assertEquals(List.of(ImportService.BATCH_SIZE, ImportService.BATCH_SIZE + 11), progress);
        assertEquals(1, first.players()); // every entry is Steve
        assertEquals("Steve", directory.added.get(STEVE));
        assertEquals(first.totalPunishments() + 1, importer.afterImport.size());

        ImportSummary second = service.run(importer, count -> { });

        assertEquals(0, second.totalPunishments());
        assertEquals(ImportService.BATCH_SIZE + 11, second.alreadyImported());
        assertEquals(ImportService.BATCH_SIZE + 11, repository.written.size());
    }

    @Test
    void refusesASecondImportWhileOneRuns() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        BlockingImporter importer = new BlockingImporter();
        ImportService service = service(importer, repository, new RecordingDirectory());
        Thread running = new Thread(() -> {
            try {
                service.run(importer, count -> { });
            } catch (ImportException e) {
                throw new IllegalStateException(e);
            }
        });
        running.start();
        importer.started.join();

        ImportException error = assertThrows(ImportException.class, () -> service.run(importer, count -> { }));

        assertEquals("moderation.import.error.running", error.messageKey().value());
        importer.release.complete(null);
        running.join();
    }

    @Test
    void reportsASourceThatIsNotThere() {
        ImportService service = service(new FixedImporter(List.of()) {
            @Override
            public Optional<String> locate() {
                return Optional.empty();
            }
        }, new InMemoryRepository(), new RecordingDirectory());

        ImportException error = assertThrows(ImportException.class, () -> service.preview(service.importer("fixed").orElseThrow()));

        assertEquals("moderation.import.error.not-found", error.messageKey().value());
    }

    private static ImportService service(PunishmentImporter importer, ImportRepository repository, PlayerDirectory directory) {
        return new ImportService(List.of(importer), repository, () -> Optional.of(directory), Logger.getLogger("test"));
    }

    /** {@code count} punishments for Steve (one in force, one temporary), a note, a skipped entry. */
    private static List<Object> entries(int count) {
        List<Object> entries = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            boolean temporary = i == 1;
            entries.add(new ImportedPunishment("ban:" + i, PunishmentType.BAN, STEVE, "Steve", null, null, "Admin", "griefing",
                    now.minusSeconds(1000 - i), temporary ? now.plusSeconds(3600) : null, i == 0, null, null, null));
        }
        entries.add(new ImportedNote("note:1", STEVE, "Steve", "Admin", "watch him", now));
        entries.add(ImportSink.SkipReason.NO_PLAYER);
        return entries;
    }

    private static class FixedImporter implements PunishmentImporter {

        private final List<Object> entries;
        final Set<String> afterImport = new HashSet<>();

        FixedImporter(List<Object> entries) {
            this.entries = entries;
        }

        @Override
        public String id() {
            return "fixed";
        }

        @Override
        public String displayName() {
            return "Fixed";
        }

        @Override
        public Optional<String> locate() {
            return Optional.of("memory");
        }

        @Override
        public void read(ImportSink sink) {
            for (Object entry : entries) {
                switch (entry) {
                    case ImportedPunishment punishment -> sink.punishment(punishment);
                    case ImportedNote note -> sink.note(note);
                    case ImportSink.SkipReason reason -> sink.skip(reason);
                    default -> throw new IllegalArgumentException(String.valueOf(entry));
                }
            }
        }

        @Override
        public void afterImport(Set<String> importedSourceIds) {
            afterImport.addAll(importedSourceIds);
        }
    }

    private static final class BlockingImporter extends FixedImporter {

        final CompletableFuture<Void> started = new CompletableFuture<>();
        final CompletableFuture<Void> release = new CompletableFuture<>();

        BlockingImporter() {
            super(List.of());
        }

        @Override
        public void read(ImportSink sink) {
            started.complete(null);
            release.join();
        }
    }

    private static final class InMemoryRepository implements ImportRepository {

        final Map<String, String> written = new HashMap<>();
        int batches;

        @Override
        public CompletableFuture<Set<String>> importedIds(String source) {
            return CompletableFuture.completedFuture(new HashSet<>(written.keySet()));
        }

        @Override
        public CompletableFuture<Integer> write(String source, List<ImportedPunishment> punishments, List<ImportedNote> notes) {
            batches++;
            punishments.forEach(punishment -> written.put(punishment.sourceId(), source));
            notes.forEach(note -> written.put(note.sourceId(), source));
            return CompletableFuture.completedFuture(punishments.size() + notes.size());
        }
    }

    private static final class RecordingDirectory implements PlayerDirectory {

        final Map<UUID, String> added = new HashMap<>();

        @Override
        public CompletableFuture<Optional<PlayerRef>> findByName(String name) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<PlayerRef>> findById(UUID playerId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Integer> addIfAbsent(Map<PlayerRef, Instant> lastSeen) {
            int before = added.size();
            lastSeen.keySet().forEach(player -> added.putIfAbsent(player.id(), player.name()));
            return CompletableFuture.completedFuture(added.size() - before);
        }
    }
}
