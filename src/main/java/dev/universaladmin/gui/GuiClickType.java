package dev.universaladmin.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * The click distinctions a {@link GuiButton} handler can reasonably act on -
 * a small, framework-owned enum instead of every page switching on Bukkit's
 * much larger {@link ClickType} (which includes creative-mode and
 * drag-related values that never apply to a click inside a cancelled admin
 * GUI).
 */
public enum GuiClickType {
    LEFT,
    SHIFT_LEFT,
    RIGHT,
    SHIFT_RIGHT,
    MIDDLE,
    /** Anything not distinguished above (number-key swap, double-click, ...). */
    OTHER;

    static GuiClickType from(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> LEFT;
            case SHIFT_LEFT -> SHIFT_LEFT;
            case RIGHT -> RIGHT;
            case SHIFT_RIGHT -> SHIFT_RIGHT;
            case MIDDLE -> MIDDLE;
            default -> OTHER;
        };
    }
}
