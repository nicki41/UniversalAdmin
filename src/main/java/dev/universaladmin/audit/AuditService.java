package dev.universaladmin.audit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Application-level entry point for audit logging. {@link dev.universaladmin.action.ActionExecutor}
 * calls {@link #record} after a successful (or, if opted in, a failed)
 * action; nothing else writes to {@link AuditEventRepository} directly - see
 * docs/architecture/actions.md#audit-hook.
 */
public interface AuditService {

    /** Persists {@code entry}. Failure to write never propagates as a reason to fail the action that triggered it. */
    CompletableFuture<Void> record(AuditEvent entry);

    /** Filtered, paginated audit history - see {@link AuditQuery}. */
    CompletableFuture<AuditPage> query(AuditQuery query);

    /** Most recent events first, capped at {@code limit}, no filters - shorthand for a common {@link #query} shape. */
    CompletableFuture<List<AuditEvent>> recent(int limit);

    CompletableFuture<Optional<AuditEvent>> findById(Long id);

    /**
     * Deletes entries older than the configured {@code audit.retention-days}
     * setting; a no-op returning {@code 0} if retention is unlimited
     * ({@code 0} days) or disabled. Returns the number of entries deleted.
     * Called periodically by {@link dev.universaladmin.modules.auditlog.AuditLogModule},
     * never on every server tick - see docs/user/audit-log.md#retention.
     */
    CompletableFuture<Integer> cleanupExpired();
}
