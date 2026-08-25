package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.permission.PermissionNode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * The "hide by default" permission rule from {@link GuiButton} - see the
 * "Permissions" section of docs/development/gui-framework.md. Tests only
 * {@link GuiButton#isVisibleTo}, not {@link GuiView#place}, so this stays a
 * pure unit test: no live Bukkit inventory/item factory needed (see
 * docs/development/testing.md on why this project doesn't mock a whole
 * Paper server) - {@code item} is {@code null} here since visibility never
 * looks at it.
 */
class GuiButtonVisibilityTest {

    @Test
    void aButtonWithNoPermissionRequirementIsAlwaysVisible() {
        GuiButton button = GuiButton.of(null, ctx -> { });
        Player viewer = mock(Player.class);

        assertTrue(button.isVisibleTo(viewer));
    }

    @Test
    void aButtonIsVisibleWhenTheViewerHoldsItsPermission() {
        PermissionNode permission = PermissionNode.core("players.view");
        GuiButton button = new GuiButton(null, permission, ctx -> { });
        Player viewer = mock(Player.class);
        when(viewer.hasPermission(permission.value())).thenReturn(true);

        assertTrue(button.isVisibleTo(viewer));
    }

    @Test
    void aButtonIsHiddenWhenTheViewerLacksItsPermission() {
        PermissionNode permission = PermissionNode.core("players.view");
        GuiButton button = new GuiButton(null, permission, ctx -> { });
        Player viewer = mock(Player.class);
        when(viewer.hasPermission(permission.value())).thenReturn(false);

        assertFalse(button.isVisibleTo(viewer));
    }
}
