package dev.universaladmin.audit;

import dev.universaladmin.storage.Repository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    /** Most recent events first, capped at {@code limit}. */
    CompletableFuture<List<AuditEvent>> recent(int limit);

    /** Filtered, paginated read - see {@link AuditQuery}. */
    CompletableFuture<AuditPage> query(AuditQuery query);

    /** Deletes every entry with {@code timestamp} before {@code cutoff}. Returns the number of rows deleted. */
    CompletableFuture<Integer> deleteOlderThan(Instant cutoff);
}
