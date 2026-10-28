package dev.universaladmin.gui;

import java.util.Locale;

/**
 * The stained-glass colours menus are built from, each with one meaning
 * that holds on every page (ADR 0027):
 *
 * <ul>
 *   <li>{@link #BLACK} - frame and unused space,</li>
 *   <li>{@link #LIGHT_GRAY} - a divider between two areas,</li>
 *   <li>{@link #LIME} - on, confirm, the selected tab; {@link #RED} - off,
 *       dangerous, cancel,</li>
 *   <li>{@link #YELLOW} - loading; {@link #WHITE} - nothing here,</li>
 *   <li>the other colours - a module's accent (the header of its pages) and
 *       the start of a group of buttons.</li>
 * </ul>
 *
 * Bukkit-independent on purpose, like {@link dev.universaladmin.module.GuiIcon}:
 * a module descriptor names its colour without depending on the server API.
 *
 * @since 1.7
 */
public enum GuiGlass {
    BLACK,
    GRAY,
    LIGHT_GRAY,
    WHITE,
    RED,
    LIME,
    GREEN,
    YELLOW,
    ORANGE,
    CYAN,
    LIGHT_BLUE,
    BLUE,
    PURPLE,
    MAGENTA,
    PINK,
    BROWN;

    /** The Minecraft item key of the pane, e.g. {@code light_blue_stained_glass_pane}. */
    public String paneKey() {
        return name().toLowerCase(Locale.ROOT) + "_stained_glass_pane";
    }
}
