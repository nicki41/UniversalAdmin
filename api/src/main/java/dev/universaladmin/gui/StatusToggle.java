package dev.universaladmin.gui;

import dev.universaladmin.permission.PermissionNode;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * One switch in the "My status" row of the main menu - vanish, staff mode,
 * godmode, a nick, staff chat. Shown as a green pane while it is on for the
 * viewer and a red one while it is off; a click runs {@link #toggle}.
 * Registered in {@link StatusToggleRegistry} by the module it belongs to,
 * so the main menu knows none of them by name.
 *
 * @since 1.7
 */
public interface StatusToggle {

    /** Unique, namespaced id such as {@code core:moderation.vanish}. */
    String id();

    /** Position in the row, ascending; built-ins use multiples of 10 below 1000. */
    default int order() {
        return 1000;
    }

    /** Required to see the switch; {@code null} if anyone with menu access may. */
    PermissionNode permission();

    /** Whether it is on for {@code viewer} right now. */
    boolean active(Player viewer);

    /** The label, such as "Vanish" or "Nick: Notch" - the on/off state is added by the menu. */
    Component label(Player viewer);

    /**
     * Switches it for the viewer of {@code context} - typically by running an
     * action - and calls {@code done} on the main thread once the change
     * happened, so the menu shows the new state.
     */
    void toggle(GuiClickContext context, Runnable done);
}
