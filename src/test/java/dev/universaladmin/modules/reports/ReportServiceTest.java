package dev.universaladmin.modules.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.reports.ReportService.CreateResult;
import dev.universaladmin.modules.reports.ReportService.UpdateResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final UUID REPORTER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final FakeReportRepository repository = new FakeReportRepository();
    private final ReportService service = new ReportService(repository, clock);

    @Test
    void acceptsAReportAndStoresItAsOpen() {
        CreateResult result = service.create(draft(REPORTER, TARGET), Duration.ofSeconds(60), 3).join();

        Report report = assertInstanceOf(CreateResult.Created.class, result).report();
        assertTrue(report.id() > 0);
        assertEquals(ReportStatus.OPEN, report.status());
        assertEquals(1, service.openCount().join());
    }

    @Test
    void rejectsReportingYourself() {
        CreateResult result = service.create(draft(REPORTER, REPORTER), Duration.ZERO, 0).join();

        assertEquals(ReportService.Rejection.SELF, assertInstanceOf(CreateResult.Rejected.class, result).reason());
    }

    @Test
    void enforcesTheCooldownBetweenTwoReportsOfTheSamePlayer() {
        service.create(draft(REPORTER, TARGET), Duration.ofSeconds(60), 0).join();
        clock.advance(Duration.ofSeconds(20));

        CreateResult tooSoon = service.create(draft(REPORTER, UUID.randomUUID()), Duration.ofSeconds(60), 0).join();
        CreateResult.Rejected rejected = assertInstanceOf(CreateResult.Rejected.class, tooSoon);
        assertEquals(ReportService.Rejection.COOLDOWN, rejected.reason());
        assertEquals(Duration.ofSeconds(40), rejected.retryAfter());

        clock.advance(Duration.ofSeconds(40));
        assertInstanceOf(CreateResult.Created.class, service.create(draft(REPORTER, TARGET), Duration.ofSeconds(60), 0).join());
    }

    @Test
    void limitsHowManyOpenReportsOnePlayerMayHave() {
        service.create(draft(REPORTER, TARGET), Duration.ZERO, 2).join();
        service.create(draft(REPORTER, TARGET), Duration.ZERO, 2).join();

        CreateResult third = service.create(draft(REPORTER, TARGET), Duration.ZERO, 2).join();

        assertEquals(ReportService.Rejection.TOO_MANY_OPEN, assertInstanceOf(CreateResult.Rejected.class, third).reason());
    }

    @Test
    void aClosedReportNoLongerCountsTowardsTheLimit() {
        Report first = ((CreateResult.Created) service.create(draft(REPORTER, TARGET), Duration.ZERO, 1).join()).report();
        service.close(first.id(), ReportStatus.RESOLVED, STAFF, "Mod", "handled").join();

        assertInstanceOf(CreateResult.Created.class, service.create(draft(REPORTER, TARGET), Duration.ZERO, 1).join());
    }

    @Test
    void claimThenResolveRecordsTheHandlerAndNote() {
        Report report = ((CreateResult.Created) service.create(draft(REPORTER, TARGET), Duration.ZERO, 0).join()).report();

        Report claimed = assertInstanceOf(UpdateResult.Updated.class, service.claim(report.id(), STAFF, "Mod").join()).report();
        assertEquals(ReportStatus.CLAIMED, claimed.status());
        assertEquals("Mod", claimed.handlerName());

        Report resolved = assertInstanceOf(UpdateResult.Updated.class,
                service.close(report.id(), ReportStatus.RESOLVED, STAFF, "Mod", "Warned the player").join()).report();
        assertEquals(ReportStatus.RESOLVED, resolved.status());
        assertEquals("Warned the player", resolved.resolution());
        assertEquals(0, service.openCount().join());
    }

    @Test
    void aClosedReportCannotBeClaimedOrClosedAgain() {
        Report report = ((CreateResult.Created) service.create(draft(REPORTER, TARGET), Duration.ZERO, 0).join()).report();
        service.close(report.id(), ReportStatus.REJECTED, STAFF, "Mod", null).join();

        assertInstanceOf(UpdateResult.AlreadyClosed.class, service.claim(report.id(), STAFF, "Mod").join());
        assertInstanceOf(UpdateResult.AlreadyClosed.class, service.close(report.id(), ReportStatus.RESOLVED, STAFF, "Mod", null).join());
    }

    @Test
    void unknownReportsAreReportedAsNotFound() {
        assertInstanceOf(UpdateResult.NotFound.class, service.claim(999, STAFF, "Mod").join());
    }

    private Report draft(UUID reporter, UUID target) {
        return Report.open(reporter, "Reporter", target, "Target", "griefing", "world", 1.0, 64.0, 1.0, clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** In-memory {@link ReportRepository} - see docs/development/testing.md for why a fake, not a mock. */
    private static final class FakeReportRepository implements ReportRepository {
        private final Map<Long, Report> reports = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public CompletableFuture<Optional<Report>> findById(Long id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(reports.get(id)));
        }

        @Override
        public CompletableFuture<List<Report>> findAll() {
            return CompletableFuture.completedFuture(sorted(reports.values().stream().toList()));
        }

        @Override
        public CompletableFuture<Report> save(Report report) {
            Report saved = report.id() == 0 ? report.withId(ids.incrementAndGet()) : report;
            reports.put(saved.id(), saved);
            return CompletableFuture.completedFuture(saved);
        }

        @Override
        public CompletableFuture<Void> deleteById(Long id) {
            reports.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Report>> findByStatus(Set<ReportStatus> statuses, int limit) {
            return CompletableFuture.completedFuture(sorted(reports.values().stream()
                    .filter(report -> statuses.contains(report.status())).limit(limit).toList()));
        }

        @Override
        public CompletableFuture<List<Report>> findByTarget(UUID targetId, int limit) {
            return CompletableFuture.completedFuture(sorted(reports.values().stream()
                    .filter(report -> report.targetId().equals(targetId)).limit(limit).toList()));
        }

        @Override
        public CompletableFuture<Integer> countOpenByReporter(UUID reporterId) {
            return CompletableFuture.completedFuture((int) reports.values().stream()
                    .filter(report -> report.reporterId().equals(reporterId) && report.status().isOpen()).count());
        }

        @Override
        public CompletableFuture<Integer> countByStatus(Set<ReportStatus> statuses) {
            return CompletableFuture.completedFuture((int) reports.values().stream()
                    .filter(report -> statuses.contains(report.status())).count());
        }

        private static List<Report> sorted(List<Report> list) {
            List<Report> copy = new ArrayList<>(list);
            copy.sort(Comparator.comparing(Report::createdAt).reversed());
            return copy;
        }
    }
}
