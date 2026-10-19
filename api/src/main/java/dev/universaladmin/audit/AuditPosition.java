package dev.universaladmin.audit;

/** A world position on an {@link AuditEvent}, for actions tied to a physical location (teleport, world edits, ...). */
public record AuditPosition(double x, double y, double z) {
}
