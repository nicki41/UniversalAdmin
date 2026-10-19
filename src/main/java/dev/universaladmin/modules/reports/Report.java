package dev.universaladmin.modules.reports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's report about another player. Immutable - every status change
 * produces a new record via {@link #claim}/{@link #close}.
 *
 * @param id           database id; {@code 0} until saved
 * @param reporterId   who filed the report
 * @param targetId     who was reported
 * @param reason       what the reporter wrote
 * @param handlerId    staff member who claimed/closed it, or {@code null}
 * @param resolution   staff note written when closing, or {@code null}
 * @param world        where the reporter stood when filing, or {@code null} (e.g. filed from the console)
 */
public record Report(
        long id,
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        String reason,
        ReportStatus status,
        UUID handlerId,
        String handlerName,
        String resolution,
        String world,
        Double x,
        Double y,
        Double z,
        String server,
        Instant createdAt,
        Instant updatedAt) {

    public Report {
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(reporterName, "reporterName");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * A new, unsaved, {@link ReportStatus#OPEN} report.
     *
     * @param server the network server it was filed on, {@code null} outside network mode
     */
    public static Report open(
            UUID reporterId, String reporterName, UUID targetId, String targetName, String reason,
            String world, Double x, Double y, Double z, String server, Instant now) {
        return new Report(0, reporterId, reporterName, targetId, targetName, reason, ReportStatus.OPEN,
                null, null, null, world, x, y, z, server, now, now);
    }

    public Report withId(long newId) {
        return new Report(newId, reporterId, reporterName, targetId, targetName, reason, status,
                handlerId, handlerName, resolution, world, x, y, z, server, createdAt, updatedAt);
    }

    public Report claim(UUID staffId, String staffName, Instant now) {
        return new Report(id, reporterId, reporterName, targetId, targetName, reason, ReportStatus.CLAIMED,
                staffId, staffName, resolution, world, x, y, z, server, createdAt, now);
    }

    /** Resolves or rejects the report. */
    public Report close(ReportStatus closedStatus, UUID staffId, String staffName, String note, Instant now) {
        if (closedStatus.isOpen()) {
            throw new IllegalArgumentException("Not a closing status: " + closedStatus);
        }
        return new Report(id, reporterId, reporterName, targetId, targetName, reason, closedStatus,
                staffId, staffName, note, world, x, y, z, server, createdAt, now);
    }

    public boolean hasLocation() {
        return world != null && x != null && y != null && z != null;
    }
}
