package dev.universaladmin.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The switches of the main menu's "My status" row - see {@link StatusToggle}.
 * The ones that are on are also the status line in the action bar
 * ({@code gui.status-line}), which steps aside for anything else shown there:
 * call {@link #pauseStatusLine} before sending your own action bar.
 *
 * @since 1.7
 */
public final class StatusToggleRegistry {

    private final Map<String, StatusToggle> toggles = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> pausedUntil = new ConcurrentHashMap<>();

    /** @throws IllegalStateException if a toggle with the same id is already registered */
    public void register(StatusToggle toggle) {
        if (toggles.putIfAbsent(toggle.id(), toggle) != null) {
            throw new IllegalStateException("Status toggle already registered: " + toggle.id());
        }
    }

    public void unregister(String id) {
        toggles.remove(id);
    }

    /** Keeps the status line out of {@code playerId}'s action bar for {@code duration}, so another message stays readable. */
    public void pauseStatusLine(UUID playerId, Duration duration) {
        Instant until = Instant.now().plus(duration);
        pausedUntil.merge(playerId, until, (current, next) -> next.isAfter(current) ? next : current);
    }

    /** Whether {@link #pauseStatusLine} holds the status line back for {@code playerId} right now. */
    public boolean statusLinePaused(UUID playerId) {
        Instant until = pausedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until.isAfter(Instant.now())) {
            return true;
        }
        pausedUntil.remove(playerId, until);
        return false;
    }

    /** Every toggle, sorted by {@link StatusToggle#order()} and then id. */
    public List<StatusToggle> toggles() {
        return toggles.values().stream()
                .sorted(Comparator.comparingInt(StatusToggle::order).thenComparing(StatusToggle::id))
                .toList();
    }
}
