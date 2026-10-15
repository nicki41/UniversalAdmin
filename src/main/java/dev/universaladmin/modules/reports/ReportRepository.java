package dev.universaladmin.modules.reports;

import dev.universaladmin.storage.Repository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence for {@link Report}s - see {@code JdbcReportRepository}. Newest first everywhere. */
public interface ReportRepository extends Repository<Report, Long> {

    CompletableFuture<List<Report>> findByStatus(Set<ReportStatus> statuses, int limit);

    CompletableFuture<List<Report>> findByTarget(UUID targetId, int limit);

    /** How many reports filed by {@code reporterId} are still open - for the per-player limit. */
    CompletableFuture<Integer> countOpenByReporter(UUID reporterId);

    CompletableFuture<Integer> countByStatus(Set<ReportStatus> statuses);
}
