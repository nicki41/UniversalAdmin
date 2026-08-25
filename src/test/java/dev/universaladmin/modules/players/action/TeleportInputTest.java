package dev.universaladmin.modules.players.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeleportInputTest {

    @Test
    void ofBuildsAKindOnlyInput() {
        UUID target = UUID.randomUUID();

        TeleportInput input = TeleportInput.of(TeleportKind.WORLD_SPAWN, target);

        assertEquals(TeleportKind.WORLD_SPAWN, input.kind());
        assertEquals(target, input.targetId());
        assertNull(input.referenceId());
        assertNull(input.worldName());
    }

    @Test
    void toPlayerCarriesBothPlayerIds() {
        UUID target = UUID.randomUUID();
        UUID reference = UUID.randomUUID();

        TeleportInput input = TeleportInput.toPlayer(target, reference);

        assertEquals(TeleportKind.PLAYER_TO_PLAYER, input.kind());
        assertEquals(target, input.targetId());
        assertEquals(reference, input.referenceId());
    }

    @Test
    void toCoordinatesCarriesWorldAndPosition() {
        UUID target = UUID.randomUUID();

        TeleportInput input = TeleportInput.toCoordinates(target, "world_nether", 1.0, 2.0, 3.0);

        assertEquals(TeleportKind.COORDINATES, input.kind());
        assertEquals("world_nether", input.worldName());
        assertEquals(1.0, input.x());
        assertEquals(2.0, input.y());
        assertEquals(3.0, input.z());
    }
}
