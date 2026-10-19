package dev.universaladmin.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.permission.PermissionNode;
import org.bukkit.entity.Player;

/**
 * One button another module or extension contributes to a player's profile
 * page in the Players module - "Moderate", "Notes", "Reports", or an
 * extension's own "Economy" page. Registered in {@link PlayerMenuRegistry};
 * the Players module renders every entry the viewer may see, without
 * importing (or even knowing about) the contributing module.
 *
 * <p>Like any GUI button, {@link #permission()} only hides the button - the
 * page or action it leads to must authorize on its own.
 */
public interface PlayerMenuEntry {

    /** Unique, namespaced id such as {@code core:moderation.moderate} or {@code myextension:balance}. */
    String id();

    /** Position among the contributed buttons, ascending; built-ins use multiples of 10 below 1000. */
    default int order() {
        return 1000;
    }

    /** Required to see the button, or {@code null} if anyone who can open the profile may. */
    PermissionNode permission();

    /** Whether the button only makes sense while the target is online (it is hidden otherwise). */
    default boolean requiresOnline() {
        return false;
    }

    /**
     * Whether the button shows on {@code target}'s profile at all - e.g. a
     * "go to their server" button only while they are on another server.
     * {@code true} by default; checked after {@link #permission()} and
     * {@link #requiresOnline()}.
     */
    default boolean visible(Player viewer, PlayerRef target) {
        return true;
    }

    /** The button's icon and label for {@code viewer}, looking at {@code target}'s profile. */
    GuiItem icon(Player viewer, PlayerRef target);

    /** Runs when the button is clicked - typically {@code context.open(new MyPage(..., target))}. */
    void onClick(GuiClickContext context, PlayerRef target);
}
