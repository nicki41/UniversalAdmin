package dev.universaladmin.modules.players;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "when did this player log in" tracker backing {@link
 * PlayerSnapshot#sessionDuration()}. Deliberately not a {@link
 * dev.universaladmin.storage.Repository} - a current session's start time is
 * exactly the kind of ephemeral, process-lifetime-only state docs/development/architecture-rules.md's
 * "Repositories nur wenn persistente Daten nötig sind" rule says shouldn't
 * be persisted; it's meaningless after a restart anyway (the player would
 * already be offline).
 */
public final class PlayerSessionTracker {

    private final Map<UUID, Instant> loginTimes = new ConcurrentHashMap<>();

    public void recordLogin(UUID playerId) {
        loginTimes.put(playerId, Instant.now());
    }

    public void recordLogout(UUID playerId) {
        loginTimes.remove(playerId);
    }

    /** Empty if {@code playerId} isn't currently tracked as logged in (offline, or never recorded). */
    public Optional<Duration> sessionDuration(UUID playerId) {
        Instant loginTime = loginTimes.get(playerId);
        return loginTime == null ? Optional.empty() : Optional.of(Duration.between(loginTime, Instant.now()));
    }
}
