package dev.universaladmin.modules.moderation.importing;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Storage side of an import: which entries of a source were already
 * imported ({@code ua_import_records}), and writing one batch of
 * punishments and notes together with those records in a single
 * transaction - so a batch is either fully imported or not at all, and a
 * repeated run skips exactly what earlier runs wrote.
 */
public interface ImportRepository {

    /** Every {@code sourceId} of {@code source} imported so far. */
    CompletableFuture<Set<String>> importedIds(String source);

    /** Writes one batch and its import records; completes with the number of rows written. */
    CompletableFuture<Integer> write(String source, List<ImportedPunishment> punishments, List<ImportedNote> notes);
}
