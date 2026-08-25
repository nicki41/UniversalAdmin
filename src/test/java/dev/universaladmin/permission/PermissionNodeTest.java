package dev.universaladmin.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PermissionNodeTest {

    @Test
    void coreShorthandPrefixesWithUniversaladmin() {
        assertEquals("universaladmin.players.view", PermissionNode.core("players.view").value());
    }

    @Test
    void rejectsUppercaseAndWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> PermissionNode.of("Players.View"));
        assertThrows(IllegalArgumentException.class, () -> PermissionNode.of("players view"));
    }
}
