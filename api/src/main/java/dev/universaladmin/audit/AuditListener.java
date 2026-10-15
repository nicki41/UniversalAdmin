package dev.universaladmin.audit;

/** Receives every recorded {@link AuditEvent} - see {@link AuditService#subscribe}. */
@FunctionalInterface
public interface AuditListener {

    /** Called off the main thread, after {@code event} was stored. */
    void onAuditEvent(AuditEvent event);
}
