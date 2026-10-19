package dev.universaladmin.modules.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chat rules the Chat module enforces: chat lock, slow mode, and who has
 * staff chat switched on. Pure in-memory state on purpose - a lock or slow
 * mode is a moment-to-moment moderation tool and is lifted by a restart.
 * Thread-safe, because Paper's chat event runs off the main thread.
 */
public final class ChatService {

    /** What {@link #check} decided for a public chat message. */
    public sealed interface Decision {
        record Allowed() implements Decision {
        }

        record Locked() implements Decision {
        }

        record SlowMode(Duration retryAfter) implements Decision {
        }
    }

    private final Clock clock;
    private volatile boolean locked;
    private volatile Duration slowMode = Duration.ZERO;
    private final Map<UUID, Instant> lastMessageAt = new ConcurrentHashMap<>();
    private final Set<UUID> staffChatToggled = ConcurrentHashMap.newKeySet();

    public ChatService(Clock clock) {
        this.clock = clock;
    }

    public ChatService() {
        this(Clock.systemUTC());
    }

    public boolean locked() {
        return locked;
    }

    /** @return whether the state actually changed */
    public boolean setLocked(boolean locked) {
        boolean changed = this.locked != locked;
        this.locked = locked;
        return changed;
    }

    public Duration slowMode() {
        return slowMode;
    }

    /** {@link Duration#ZERO} switches slow mode off. */
    public void setSlowMode(Duration delay) {
        this.slowMode = delay.isNegative() ? Duration.ZERO : delay;
        lastMessageAt.clear();
    }

    /**
     * Whether a player may send a public chat message right now. An allowed
     * message starts the player's slow-mode interval; {@code bypass} (staff)
     * is never locked or slowed.
     */
    public Decision check(UUID playerId, boolean bypass) {
        if (bypass) {
            return new Decision.Allowed();
        }
        if (locked) {
            return new Decision.Locked();
        }
        Duration delay = slowMode;
        if (!delay.isZero()) {
            Instant now = clock.instant();
            Instant last = lastMessageAt.get(playerId);
            if (last != null) {
                Duration elapsed = Duration.between(last, now);
                if (elapsed.compareTo(delay) < 0) {
                    return new Decision.SlowMode(delay.minus(elapsed));
                }
            }
            lastMessageAt.put(playerId, now);
        }
        return new Decision.Allowed();
    }

    /** Switches "everything I type goes to staff chat" on or off; returns the new state. */
    public boolean toggleStaffChat(UUID playerId) {
        if (staffChatToggled.remove(playerId)) {
            return false;
        }
        staffChatToggled.add(playerId);
        return true;
    }

    public boolean staffChatToggled(UUID playerId) {
        return staffChatToggled.contains(playerId);
    }

    /** Forgets per-player state when a player leaves. */
    public void forget(UUID playerId) {
        lastMessageAt.remove(playerId);
        staffChatToggled.remove(playerId);
    }
}
