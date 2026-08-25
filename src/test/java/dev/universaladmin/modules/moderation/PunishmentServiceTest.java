package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PunishmentService} against an in-memory {@link PunishmentRepository}
 * fake instead of a real database - this is the pattern every module's
 * business logic should be testable with, see {@code PlayerServiceTest}.
 * The two-concurrent-revokes-racing-on-one-row guarantee is instead tested
 * against the real JDBC implementation in {@code JdbcPunishmentRepositoryTest} -
 * it's a real concurrency property this synchronous fake can't meaningfully prove.
 */
class PunishmentServiceTest {

    private final Map<Long, Punishment> store = new ConcurrentHashMap<>();
    private final PunishmentService service = new PunishmentService(new InMemoryPunishmentRepository(store));

    @Test
    void issuesAPermanentBan() {
        UUID target = UUID.randomUUID();
        Punishment ban = service.issue(PunishmentType.BAN, target, "Notch", null,
                UUID.randomUUID(), "Admin", "Cheating", null).join();

        assertTrue(ban.permanent());
        assertTrue(ban.active());
        Optional<Punishment> active = service.activeBan(target).join();
        assertTrue(active.isPresent());
        assertEquals(ban.id(), active.get().id());
    }

    @Test
    void issuesATempBanAndFindsItActiveBeforeExpiry() {
        UUID target = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        service.issue(PunishmentType.TEMP_BAN, target, "Notch", null,
                UUID.randomUUID(), "Admin", "Griefing", expiresAt).join();

        Optional<Punishment> active = service.activeBan(target).join();
        assertTrue(active.isPresent());
        assertFalse(active.get().permanent());
    }

    @Test
    void anExpiredTempBanIsNoLongerActiveEvenThoughTheActiveColumnStillSaysSo() {
        UUID target = UUID.randomUUID();
        Instant expiresAt = Instant.now().minusSeconds(60);
        Punishment expired = service.issue(PunishmentType.TEMP_BAN, target, "Notch", null,
                UUID.randomUUID(), "Admin", "Spam", expiresAt).join();

        // The "active" column is still true - only the query-time expiry
        // check (Punishment.isActiveAt / the repository's WHERE clause)
        // decides this, see PunishmentRepository's class javadoc.
        assertTrue(expired.active());
        assertTrue(service.activeBan(target).join().isEmpty());
    }

    @Test
    void unbanRevokesAllActiveBanTypesForTheTarget() {
        UUID target = UUID.randomUUID();
        service.issue(PunishmentType.BAN, target, "Notch", null, UUID.randomUUID(), "Admin", "Cheating", null).join();
        service.issue(PunishmentType.IP_BAN, target, "Notch", "1.2.3.4", UUID.randomUUID(), "Admin", "Cheating", null).join();

        List<Punishment> revoked = service.unban(target, "Moderator").join();

        assertEquals(2, revoked.size());
        assertTrue(service.activeBan(target).join().isEmpty());
        assertTrue(service.activeIpBan("1.2.3.4").join().isEmpty());
    }

    @Test
    void unbanIsANoOpWhenNothingIsActive() {
        UUID target = UUID.randomUUID();
        assertTrue(service.unban(target, "Moderator").join().isEmpty());
    }

    @Test
    void unmuteRevokesActiveMutesOnly() {
        UUID target = UUID.randomUUID();
        service.issue(PunishmentType.TEMP_MUTE, target, "Notch", null, UUID.randomUUID(), "Admin", "Spam", Instant.now().plusSeconds(60)).join();
        service.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null).join();

        List<Punishment> revoked = service.unmute(target, "Moderator").join();

        assertEquals(1, revoked.size());
        assertTrue(service.activeMute(target).join().isEmpty());
    }

    @Test
    void removeWarnRevokesExactlyOneWarningById() {
        UUID target = UUID.randomUUID();
        Punishment first = service.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null).join();
        Punishment second = service.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Spam", null).join();

        Optional<Punishment> removed = service.removeWarn(first.id(), "Moderator").join();

        assertTrue(removed.isPresent());
        assertFalse(removed.get().active());
        // The second warning is untouched.
        assertTrue(store.get(second.id()).active());
    }

    @Test
    void removingAnAlreadyRemovedWarningReturnsEmpty() {
        UUID target = UUID.randomUUID();
        Punishment warn = service.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null).join();
        service.removeWarn(warn.id(), "Moderator").join();

        assertTrue(service.removeWarn(warn.id(), "Moderator").join().isEmpty());
    }

    @Test
    void recordKickPersistsAnAlreadyInactiveRow() {
        UUID target = UUID.randomUUID();
        Punishment kick = service.recordKick(target, "Notch", UUID.randomUUID(), "Admin", "Test").join();

        assertFalse(kick.active());
        assertEquals(PunishmentType.KICK, kick.type());
    }

    @Test
    void issuesAFreezeAndUnfreezeRevokesIt() {
        UUID target = UUID.randomUUID();
        Punishment freeze = service.issue(PunishmentType.FREEZE, target, "Notch", null,
                UUID.randomUUID(), "Admin", "Staff Freeze Tool", null).join();

        assertTrue(freeze.permanent());
        assertTrue(service.activeFreeze(target).join().isPresent());

        List<Punishment> revoked = service.unfreeze(target, "Moderator").join();

        assertEquals(1, revoked.size());
        assertTrue(service.activeFreeze(target).join().isEmpty());
    }

    @Test
    void unfreezeIsANoOpWhenNotFrozen() {
        UUID target = UUID.randomUUID();
        assertTrue(service.unfreeze(target, "Moderator").join().isEmpty());
    }

    @Test
    void historyFiltersByTypeAndTarget() {
        UUID target = UUID.randomUUID();
        service.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null).join();
        service.issue(PunishmentType.BAN, target, "Notch", null, UUID.randomUUID(), "Admin", "Cheating", null).join();
        service.issue(PunishmentType.WARN, UUID.randomUUID(), "OtherPlayer", null, UUID.randomUUID(), "Admin", "Spam", null).join();

        List<Punishment> warnings = service.history(new PunishmentQuery(target, Set.of(PunishmentType.WARN), null, 100)).join();

        assertEquals(1, warnings.size());
        assertEquals(PunishmentType.WARN, warnings.get(0).type());
        assertEquals(target, warnings.get(0).targetId());
    }

    @Test
    void expireOverdueFlipsTheActiveColumnForPastExpiries() {
        UUID target = UUID.randomUUID();
        Punishment overdue = service.issue(PunishmentType.TEMP_BAN, target, "Notch", null,
                UUID.randomUUID(), "Admin", "Griefing", Instant.now().minusSeconds(5)).join();
        assertTrue(overdue.active());

        int affected = service.expireOverdue().join();

        assertEquals(1, affected);
        assertFalse(store.get(overdue.id()).active());
    }

    /** Minimal, correctness-focused in-memory fake - not concerned with SQL-dialect concerns the JDBC test covers. */
    private static final class InMemoryPunishmentRepository implements PunishmentRepository {

        private final Map<Long, Punishment> store;
        private final AtomicLong nextId = new AtomicLong(1);

        private InMemoryPunishmentRepository(Map<Long, Punishment> store) {
            this.store = store;
        }

        @Override
        public CompletableFuture<Optional<Punishment>> findById(Long id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletableFuture<List<Punishment>> findAll() {
            return CompletableFuture.completedFuture(new ArrayList<>(store.values()));
        }

        @Override
        public CompletableFuture<Punishment> save(Punishment entity) {
            Punishment persisted = entity.id() == 0 ? entity.withId(nextId.getAndIncrement()) : entity;
            store.put(persisted.id(), persisted);
            return CompletableFuture.completedFuture(persisted);
        }

        @Override
        public CompletableFuture<Void> deleteById(Long id) {
            store.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<Punishment>> findActiveBan(UUID targetId, Instant now) {
            return findActiveOne(p -> p.targetId().equals(targetId) && (p.type() == PunishmentType.BAN || p.type() == PunishmentType.TEMP_BAN), now);
        }

        @Override
        public CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip, Instant now) {
            return findActiveOne(p -> p.type() == PunishmentType.IP_BAN && ip.equals(p.targetIp()), now);
        }

        @Override
        public CompletableFuture<Optional<Punishment>> findActiveMute(UUID targetId, Instant now) {
            return findActiveOne(p -> p.targetId().equals(targetId) && (p.type() == PunishmentType.MUTE || p.type() == PunishmentType.TEMP_MUTE), now);
        }

        @Override
        public CompletableFuture<Optional<Punishment>> findActiveFreeze(UUID targetId, Instant now) {
            return findActiveOne(p -> p.targetId().equals(targetId) && p.type() == PunishmentType.FREEZE, now);
        }

        private CompletableFuture<Optional<Punishment>> findActiveOne(java.util.function.Predicate<Punishment> filter, Instant now) {
            return CompletableFuture.completedFuture(store.values().stream()
                    .filter(filter)
                    .filter(p -> p.isActiveAt(now))
                    .max(java.util.Comparator.comparing(Punishment::createdAt)));
        }

        @Override
        public CompletableFuture<List<Punishment>> findByQuery(PunishmentQuery query) {
            List<Punishment> results = store.values().stream()
                    .filter(p -> query.targetId() == null || p.targetId().equals(query.targetId()))
                    .filter(p -> query.types() == null || query.types().contains(p.type()))
                    .filter(p -> query.active() == null || query.active() == p.isActiveAt(Instant.now()))
                    .sorted(java.util.Comparator.comparing(Punishment::createdAt).reversed())
                    .limit(query.limit())
                    .toList();
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public synchronized CompletableFuture<List<Punishment>> revokeActiveByTarget(
                UUID targetId, Set<PunishmentType> types, Instant revokedAt, String revokedBy) {
            List<Punishment> revoked = new ArrayList<>();
            for (Punishment p : store.values()) {
                if (p.targetId().equals(targetId) && types.contains(p.type()) && p.active()) {
                    Punishment updated = p.revoke(revokedAt, revokedBy);
                    store.put(updated.id(), updated);
                    revoked.add(updated);
                }
            }
            return CompletableFuture.completedFuture(revoked);
        }

        @Override
        public synchronized CompletableFuture<Optional<Punishment>> revokeById(long id, Instant revokedAt, String revokedBy) {
            Punishment existing = store.get(id);
            if (existing == null || !existing.active()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Punishment updated = existing.revoke(revokedAt, revokedBy);
            store.put(id, updated);
            return CompletableFuture.completedFuture(Optional.of(updated));
        }

        @Override
        public CompletableFuture<Integer> expireOverdue(Instant now) {
            int count = 0;
            for (Punishment p : new ArrayList<>(store.values())) {
                if (p.active() && p.expiresAt() != null && !p.expiresAt().isAfter(now)) {
                    store.put(p.id(), new Punishment(p.id(), p.type(), p.targetId(), p.targetLastKnownName(), p.targetIp(),
                            p.actorId(), p.actorName(), p.reason(), p.createdAt(), p.expiresAt(), false, p.revokedAt(), p.revokedBy(), p.metadata()));
                    count++;
                }
            }
            return CompletableFuture.completedFuture(count);
        }
    }
}
