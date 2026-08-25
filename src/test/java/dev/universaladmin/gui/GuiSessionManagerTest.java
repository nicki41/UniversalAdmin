package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The session lifecycle rules behind "no memory leaks" - see the "Player
 * Session" section of docs/development/gui-framework.md. {@link GuiListener}
 * is what actually calls {@link #remove} at the right moments (a close
 * event, a quit event); this only tests the map itself: lazy creation,
 * identity stability, and that removal genuinely drops the session.
 */
class GuiSessionManagerTest {

    private final GuiSessionManager sessions = new GuiSessionManager();

    @Test
    void createsASessionLazilyOnFirstAccess() {
        UUID playerId = UUID.randomUUID();
        assertFalse(sessions.hasSession(playerId));

        GuiSession session = sessions.sessionFor(playerId);

        assertTrue(sessions.hasSession(playerId));
        assertEquals(playerId, session.playerId());
    }

    @Test
    void returnsTheSameSessionInstanceForRepeatedLookups() {
        UUID playerId = UUID.randomUUID();

        GuiSession first = sessions.sessionFor(playerId);
        GuiSession second = sessions.sessionFor(playerId);

        assertSame(first, second);
    }

    @Test
    void removingASessionDropsItEntirely() {
        UUID playerId = UUID.randomUUID();
        sessions.sessionFor(playerId);

        sessions.remove(playerId);

        assertFalse(sessions.hasSession(playerId));
        assertEquals(0, sessions.activeSessionCount());
    }

    @Test
    void aFreshSessionIsHandedOutAfterRemoval() {
        UUID playerId = UUID.randomUUID();
        GuiSession original = sessions.sessionFor(playerId);
        original.setAttribute("page", 5);

        sessions.remove(playerId);
        GuiSession recreated = sessions.sessionFor(playerId);

        assertTrue(recreated.attribute("page").isEmpty(), "a session recreated after removal must start clean");
    }

    @Test
    void removingAnUnknownPlayerIsANoOp() {
        sessions.remove(UUID.randomUUID());

        assertEquals(0, sessions.activeSessionCount());
    }

    @Test
    void sessionCountReflectsOnlyDistinctPlayers() {
        sessions.sessionFor(UUID.randomUUID());
        sessions.sessionFor(UUID.randomUUID());

        assertEquals(2, sessions.activeSessionCount());
    }
}
