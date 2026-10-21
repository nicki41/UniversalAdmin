package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Imports punishments from other plugins: a {@link #preview} that only
 * counts, and a {@link #run} that writes in batches of {@value #BATCH_SIZE}.
 * Both stream the source through an {@link ImportSink}, skip what earlier
 * runs already imported (see {@link ImportRepository}) and blocking - call
 * them off the main thread, as {@link ImportAction} does. Only one import
 * runs at a time.
 *
 * <p>Players the source knows but this database has never seen are added
 * to the player directory, so {@code /unban <name>} finds them.
 */
public final class ImportService {

    static final int BATCH_SIZE = 500;

    private final Map<String, PunishmentImporter> importers = new LinkedHashMap<>();
    private final ImportRepository repository;
    private final Supplier<Optional<PlayerDirectory>> directory;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    public ImportService(Collection<PunishmentImporter> importers, ImportRepository repository,
            Supplier<Optional<PlayerDirectory>> directory, Logger logger) {
        importers.forEach(importer -> this.importers.put(importer.id(), importer));
        this.repository = repository;
        this.directory = directory;
        this.logger = logger;
    }

    public List<PunishmentImporter> importers() {
        return List.copyOf(importers.values());
    }

    public Optional<PunishmentImporter> importer(String id) {
        return Optional.ofNullable(id == null ? null : importers.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean running() {
        return running.get();
    }

    /** Counts what {@link #run} would write - writes nothing. Blocking. */
    public ImportSummary preview(PunishmentImporter importer) throws ImportException {
        return process(importer, false, count -> { });
    }

    /** Imports everything new from {@code importer}; {@code progress} gets the running total after each batch. Blocking. */
    public ImportSummary run(PunishmentImporter importer, IntConsumer progress) throws ImportException {
        if (!running.compareAndSet(false, true)) {
            throw new ImportException("moderation.import.error.running");
        }
        try {
            return process(importer, true, progress);
        } finally {
            running.set(false);
        }
    }

    private ImportSummary process(PunishmentImporter importer, boolean write, IntConsumer progress) throws ImportException {
        Instant started = Instant.now();
        String location = importer.locate()
                .orElseThrow(() -> new ImportException("moderation.import.error.not-found", importer.displayName()));
        Set<String> alreadyImported = join(repository.importedIds(importer.id()));
        Batch batch = new Batch(importer.id(), write, alreadyImported, progress, started);
        try {
            importer.read(batch);
            batch.flush();
        } catch (StorageFailure failure) {
            throw new ImportException("moderation.import.error.storage", failure.getCause());
        }
        int addedPlayers = batch.players.size();
        if (write) {
            // Entries an earlier run imported count too: their source copy may
            // still be there if that run stopped before this step.
            Set<String> inUniversalAdmin = new HashSet<>(batch.written);
            inUniversalAdmin.addAll(batch.previouslyImported);
            if (!inUniversalAdmin.isEmpty()) {
                importer.afterImport(Set.copyOf(inUniversalAdmin));
            }
            addedPlayers = rememberPlayers(batch.players);
        }
        return batch.tally.toSummary(importer.displayName(), location, addedPlayers, Duration.between(started, Instant.now()));
    }

    /** Adds the imported players this database does not know yet; returns how many were added (all known players for a preview). */
    private int rememberPlayers(Map<UUID, Seen> players) {
        Optional<PlayerDirectory> found = directory.get();
        if (found.isEmpty() || players.isEmpty()) {
            return 0;
        }
        Map<PlayerRef, Instant> lastSeen = new HashMap<>();
        players.forEach((id, seen) -> lastSeen.put(new PlayerRef(id, seen.name), seen.at));
        try {
            return found.get().addIfAbsent(lastSeen).join();
        } catch (CompletionException e) {
            // The punishments are in; a player list that is not updated only
            // means /unban needs the UUID for players who never joined.
            logger.log(Level.WARNING, "Imported punishments, but could not add the imported players to the player list", e.getCause());
            return 0;
        }
    }

    private static <T> T join(java.util.concurrent.CompletableFuture<T> future) throws ImportException {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw new ImportException("moderation.import.error.storage", e.getCause());
        }
    }

    private record Seen(String name, Instant at) {
    }

    /** Carries a storage failure out of an {@link ImportSink} callback, which cannot throw checked exceptions. */
    private static final class StorageFailure extends RuntimeException {
        StorageFailure(Throwable cause) {
            super(cause);
        }
    }

    /** The sink both modes share: dedupes, tallies, and - when writing - buffers and flushes batches. */
    private final class Batch implements ImportSink {

        private final String source;
        private final boolean write;
        private final Set<String> alreadyImported;
        private final IntConsumer progress;
        private final Instant now;
        private final Set<String> seen = new HashSet<>();
        private final Set<String> written = new HashSet<>();
        private final Set<String> previouslyImported = new HashSet<>();
        private final Map<UUID, Seen> players = new HashMap<>();
        private final ImportSummary.Tally tally = new ImportSummary.Tally();
        private final List<ImportedPunishment> punishments = new ArrayList<>();
        private final List<ImportedNote> notes = new ArrayList<>();

        Batch(String source, boolean write, Set<String> alreadyImported, IntConsumer progress, Instant now) {
            this.source = source;
            this.write = write;
            this.alreadyImported = alreadyImported;
            this.progress = progress;
            this.now = now;
        }

        @Override
        public void punishment(ImportedPunishment punishment) {
            if (!isNew(punishment.sourceId())) {
                return;
            }
            tally.punishment(punishment, punishment.activeAt(now));
            if (!isStandIn(punishment) && !ImportTargets.UNKNOWN_NAME.equals(punishment.targetName())) {
                players.merge(punishment.targetId(), new Seen(punishment.targetName(), punishment.createdAt()),
                        (old, fresh) -> fresh.at.isAfter(old.at) ? fresh : old);
            }
            if (write) {
                punishments.add(punishment);
                flushIfFull();
            }
        }

        @Override
        public void note(ImportedNote note) {
            if (!isNew(note.sourceId())) {
                return;
            }
            tally.note();
            if (write) {
                notes.add(note);
                flushIfFull();
            }
        }

        @Override
        public void skip(SkipReason reason) {
            tally.skip(reason);
        }

        private boolean isNew(String sourceId) {
            if (alreadyImported.contains(sourceId)) {
                if (previouslyImported.add(sourceId)) {
                    tally.alreadyImported();
                }
                return false;
            }
            return seen.add(sourceId);
        }

        private boolean isStandIn(ImportedPunishment punishment) {
            return punishment.targetIp() != null && punishment.targetId().equals(ImportTargets.forIp(punishment.targetIp()));
        }

        private void flushIfFull() {
            if (punishments.size() + notes.size() >= BATCH_SIZE) {
                flush();
            }
        }

        void flush() {
            if (!write || (punishments.isEmpty() && notes.isEmpty())) {
                return;
            }
            try {
                repository.write(source, List.copyOf(punishments), List.copyOf(notes)).join();
            } catch (CompletionException e) {
                throw new StorageFailure(e.getCause());
            }
            punishments.forEach(punishment -> written.add(punishment.sourceId()));
            notes.forEach(note -> written.add(note.sourceId()));
            punishments.clear();
            notes.clear();
            progress.accept(written.size());
        }
    }
}
