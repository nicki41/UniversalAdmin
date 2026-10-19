package dev.universaladmin.modules.players.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import org.junit.jupiter.api.Test;

class SlotSyncTest {

    private static SlotSync decide(String view, String target, String last) {
        return SlotSync.decide(view, target, last, Objects::equals);
    }

    @Test
    void nothingToDoWhileBothSidesAgree() {
        assertEquals(SlotSync.NONE, decide("dirt", "dirt", "dirt"));
        assertEquals(SlotSync.NONE, decide(null, null, null));
    }

    @Test
    void aChangeInTheEditorGoesToThePlayer() {
        assertEquals(SlotSync.TO_TARGET, decide("diamond", "dirt", "dirt"));
        // the staff member took the item out of the slot
        assertEquals(SlotSync.TO_TARGET, decide(null, "dirt", "dirt"));
    }

    @Test
    void aChangeByThePlayerShowsUpInTheEditor() {
        assertEquals(SlotSync.TO_VIEW, decide("dirt", "stone", "dirt"));
        // the player dropped or used the item
        assertEquals(SlotSync.TO_VIEW, decide("dirt", null, "dirt"));
    }

    @Test
    void whenBothChangedTheSameSlotThePlayersInventoryWins() {
        // copying the editor's stale copy back could bring back an item the
        // player already used or dropped
        assertEquals(SlotSync.TO_VIEW, decide("diamond", "stone", "dirt"));
        assertEquals(SlotSync.TO_VIEW, decide(null, null, "dirt"));
    }
}
