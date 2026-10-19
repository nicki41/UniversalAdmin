package dev.universaladmin.modules.moderation.notes;

import dev.universaladmin.storage.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence for {@link PlayerNote}s - see {@code JdbcPlayerNoteRepository}. Newest first. */
public interface PlayerNoteRepository extends Repository<PlayerNote, Long> {

    CompletableFuture<List<PlayerNote>> findByTarget(UUID targetId, int limit);

    CompletableFuture<Integer> countByTarget(UUID targetId);
}
