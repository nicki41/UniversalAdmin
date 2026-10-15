package dev.universaladmin.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every active {@link GuiSession}, keyed by player {@link UUID}.
 *
 * <p><b>Lifecycle, and why this can't leak:</b> a session is created lazily
 * on first {@link #sessionFor}, and removed by {@link GuiListener} the
 * moment a player's GUI inventory closes for a "real" reason (manually
 * closed, disconnected, teleported, ...) - not when navigating between
 * pages, which closes and reopens an inventory internally but must keep the
 * session alive. {@link GuiListener} also removes it unconditionally on
 * {@code PlayerQuitEvent} as a second, defensive cleanup path. A session
 * that is never explicitly removed (e.g. a bug in that cleanup) still
 * cannot accumulate across restarts - this map is in-memory only - but
 * within a single run it would grow unbounded, which is exactly why the
 * close-event cleanup is not optional. See docs/development/gui-framework.md.
 */
public final class GuiSessionManager {

    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiSession sessionFor(UUID playerId) {
        return sessions.computeIfAbsent(playerId, GuiSession::new);
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** For diagnostics/tests - how many sessions are currently tracked. */
    public int activeSessionCount() {
        return sessions.size();
    }
}
