package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreezeRuntimeStateTest {

    @Test
    void defaultsToNotFrozen() {
        assertFalse(new FreezeRuntimeState().isFrozen(UUID.randomUUID()));
    }

    @Test
    void togglingIsIdempotentAndReversible() {
        FreezeRuntimeState state = new FreezeRuntimeState();
        UUID player = UUID.randomUUID();

        state.setFrozen(player, true);
        assertTrue(state.isFrozen(player));
        state.setFrozen(player, true);
        assertTrue(state.isFrozen(player));

        state.setFrozen(player, false);
        assertFalse(state.isFrozen(player));
    }
}
