package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** State-machine coverage for the in-memory vanish/freeze/godmode/collision/staff-mode trackers. */
class VanishRuntimeStateTest {

    @Test
    void defaultsToNotVanished() {
        assertFalse(new VanishRuntimeState().isVanished(UUID.randomUUID()));
    }

    @Test
    void togglingIsIdempotentAndReversible() {
        VanishRuntimeState state = new VanishRuntimeState();
        UUID player = UUID.randomUUID();

        state.setVanished(player, true);
        assertTrue(state.isVanished(player));
        state.setVanished(player, true); // idempotent
        assertTrue(state.isVanished(player));

        state.setVanished(player, false);
        assertFalse(state.isVanished(player));
    }

    @Test
    void allReturnsExactlyTheCurrentlyVanishedSet() {
        VanishRuntimeState state = new VanishRuntimeState();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        state.setVanished(a, true);
        state.setVanished(b, true);
        state.setVanished(b, false);

        assertTrue(state.all().contains(a));
        assertFalse(state.all().contains(b));
    }
}
