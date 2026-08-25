package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The navigation stack and attribute bag {@link GuiClickContext#open}/
 * {@link AbstractListGuiPage} build on - see the "Navigation" and "Player
 * Session" sections of docs/development/gui-framework.md.
 */
class GuiSessionTest {

    private final GuiSession session = new GuiSession(UUID.randomUUID());

    @Test
    void hasNoHistoryInitially() {
        assertFalse(session.hasHistory());
        assertTrue(session.popHistory().isEmpty());
    }

    @Test
    void popHistoryReturnsTheMostRecentlyPushedEntryFirst() {
        AtomicInteger order = new AtomicInteger();
        session.pushHistory(() -> order.compareAndSet(0, 1));
        session.pushHistory(() -> order.compareAndSet(0, 2));

        assertTrue(session.hasHistory());
        session.popHistory().orElseThrow().run();
        assertEquals(2, order.get(), "the second (most recent) push must run first - LIFO");

        assertTrue(session.hasHistory());
        session.popHistory().orElseThrow().run();
        assertEquals(2, order.get(), "the first push must not overwrite an already-run marker");
        assertFalse(session.hasHistory());
    }

    @Test
    void clearHistoryDropsEveryEntry() {
        session.pushHistory(() -> { });
        session.pushHistory(() -> { });

        session.clearHistory();

        assertFalse(session.hasHistory());
    }

    @Test
    void attributesRoundTripByKey() {
        assertEquals(42, session.intAttribute("page", 42), "default is returned when unset");

        session.setAttribute("page", 3);

        assertEquals(3, session.intAttribute("page", 42));
        assertEquals(3, session.attribute("page").orElseThrow());
    }

    @Test
    void intAttributeFallsBackToDefaultForANonIntegerValue() {
        session.setAttribute("page", "not-a-number");

        assertEquals(7, session.intAttribute("page", 7));
    }

    @Test
    void clearAttributesDropsEveryAttributeButNotHistory() {
        session.setAttribute("page", 3);
        session.pushHistory(() -> { });

        session.clearAttributes();

        assertTrue(session.attribute("page").isEmpty());
        assertTrue(session.hasHistory(), "clearing attributes must not touch navigation history");
    }
}
