package dev.universaladmin.modules.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business rules for player reports: who may file one (not about yourself,
 * not faster than the cooldown, not more than the open-report limit), and
 * the claim → resolve/reject lifecycle. Shared by the actions behind
 * {@code /report}, {@code /admin reports} and the reports GUI.
 *
 * <p>The per-player cooldown is kept in memory on purpose - it is spam
 * protection, not history, and resetting it on restart is harmless.
 */
public final class ReportService {

    /** Why a report was not accepted. */
    public enum Rejection {
        SELF,
        COOLDOWN,
        TOO_MANY_OPEN
    }

    /** Outcome of {@link #create}. */
    public sealed interface CreateResult {
        record Created(Report report) implements CreateResult {
        }

        record Rejected(Rejection reason, Duration retryAfter) implements CreateResult {
        }
    }

    /** Outcome of {@link #claim}/{@link #close}. */
    public sealed interface UpdateResult {
        record Updated(Report report) implements UpdateResult {
        }

        record NotFound() implements UpdateResult {
        }

        record AlreadyClosed(Report report) implements UpdateResult {
        }
    }

    private static final Set<ReportStatus> OPEN = EnumSet.of(ReportStatus.OPEN, ReportStatus.CLAIMED);

    private final ReportRepository repository;
    private final Clock clock;
    private final Map<UUID, Instant> lastReportAt = new ConcurrentHashMap<>();

    public ReportService(ReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ReportService(ReportRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * Files {@code draft} unless a rule forbids it. The cooldown starts only
     * for an accepted report.
     *
     * @param cooldown minimum time between two reports by the same player; zero disables it
     * @param maxOpen  maximum open reports per reporter; zero or less disables the limit
     */
    public CompletableFuture<CreateResult> create(Report draft, Duration cooldown, int maxOpen) {
        if (draft.reporterId().equals(draft.targetId())) {
            return CompletableFuture.completedFuture(new CreateResult.Rejected(Rejection.SELF, Duration.ZERO));
        }
        Instant now = clock.instant();
        Instant last = lastReportAt.get(draft.reporterId());
        if (last != null && !cooldown.isZero()) {
            Duration elapsed = Duration.between(last, now);
            if (elapsed.compareTo(cooldown) < 0) {
                return CompletableFuture.completedFuture(new CreateResult.Rejected(Rejection.COOLDOWN, cooldown.minus(elapsed)));
            }
        }
        CompletableFuture<Integer> openCount = maxOpen > 0
                ? repository.countOpenByReporter(draft.reporterId())
                : CompletableFuture.completedFuture(0);
        return openCount.thenCompose(open -> {
            if (maxOpen > 0 && open >= maxOpen) {
                return CompletableFuture.<CreateResult>completedFuture(new CreateResult.Rejected(Rejection.TOO_MANY_OPEN, Duration.ZERO));
            }
            lastReportAt.put(draft.reporterId(), now);
            return repository.save(draft).thenApply(CreateResult.Created::new);
        });
    }

    /** Marks an open report as being handled by a staff member. Re-claiming a claimed report hands it over. */
    public CompletableFuture<UpdateResult> claim(long id, UUID staffId, String staffName) {
        return repository.findById(id).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.<UpdateResult>completedFuture(new UpdateResult.NotFound());
            }
            Report report = found.get();
            if (!report.status().isOpen()) {
                return CompletableFuture.<UpdateResult>completedFuture(new UpdateResult.AlreadyClosed(report));
            }
            return repository.save(report.claim(staffId, staffName, clock.instant())).thenApply(UpdateResult.Updated::new);
        });
    }

    /** Resolves or rejects an open report with an optional staff note. */
    public CompletableFuture<UpdateResult> close(long id, ReportStatus status, UUID staffId, String staffName, String note) {
        return repository.findById(id).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.<UpdateResult>completedFuture(new UpdateResult.NotFound());
            }
            Report report = found.get();
            if (!report.status().isOpen()) {
                return CompletableFuture.<UpdateResult>completedFuture(new UpdateResult.AlreadyClosed(report));
            }
            return repository.save(report.close(status, staffId, staffName, note, clock.instant())).thenApply(UpdateResult.Updated::new);
        });
    }

    public CompletableFuture<Optional<Report>> find(long id) {
        return repository.findById(id);
    }

    public CompletableFuture<List<Report>> open(int limit) {
        return repository.findByStatus(OPEN, limit);
    }

    public CompletableFuture<List<Report>> recent(int limit) {
        return repository.findByStatus(EnumSet.allOf(ReportStatus.class), limit);
    }

    public CompletableFuture<List<Report>> about(UUID targetId, int limit) {
        return repository.findByTarget(targetId, limit);
    }

    public CompletableFuture<Integer> openCount() {
        return repository.countByStatus(OPEN);
    }
}
