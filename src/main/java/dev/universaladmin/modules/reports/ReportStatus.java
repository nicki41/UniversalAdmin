package dev.universaladmin.modules.reports;

/** Lifecycle of a {@link Report}: open → claimed by a staff member → resolved or rejected. */
public enum ReportStatus {
    OPEN,
    CLAIMED,
    RESOLVED,
    REJECTED;

    /** Still waiting for (or being worked on by) staff. */
    public boolean isOpen() {
        return this == OPEN || this == CLAIMED;
    }
}
