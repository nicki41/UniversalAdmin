package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** State-machine coverage for the remaining trivial in-memory trackers - {@link GodmodeState}, {@link CollisionState}, {@link StaffModeState}. */
class StaffStateTrackersTest {

    @Test
    void godmodeStateDefaultsToDisabledAndToggles() {
        GodmodeState state = new GodmodeState();
        UUID player = UUID.randomUUID();

        assertFalse(state.isEnabled(player));
        state.setEnabled(player, true);
        assertTrue(state.isEnabled(player));
        state.setEnabled(player, false);
        assertFalse(state.isEnabled(player));
    }

    @Test
    void collisionStateDefaultsToNotManuallyDisabledAndToggles() {
        CollisionState state = new CollisionState();
        UUID player = UUID.randomUUID();

        assertFalse(state.isManuallyDisabled(player));
        state.setManuallyDisabled(player, true);
        assertTrue(state.isManuallyDisabled(player));
        state.setManuallyDisabled(player, false);
        assertFalse(state.isManuallyDisabled(player));
    }

    @Test
    void staffModeStateDefaultsToInactiveAndToggles() {
        StaffModeState state = new StaffModeState();
        UUID player = UUID.randomUUID();

        assertFalse(state.isActive(player));
        state.setActive(player, true);
        assertTrue(state.isActive(player));
        state.setActive(player, false);
        assertFalse(state.isActive(player));
    }
}
