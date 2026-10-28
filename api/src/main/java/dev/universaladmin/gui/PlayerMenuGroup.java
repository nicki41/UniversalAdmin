package dev.universaladmin.gui;

/**
 * The rows of a player's profile, each behind a coloured label pane - see
 * {@link PlayerMenuEntry#group()}. A row holds six buttons; what does not
 * fit moves on to {@link #MORE}.
 *
 * @since 1.7
 */
public enum PlayerMenuGroup {
    /** Warn, mute, kick, ban, freeze - red. */
    MODERATE(GuiGlass.RED),
    /** Inventory, history, reports - looking without changing anything; blue. */
    VIEW(GuiGlass.BLUE),
    /** Teleports, heal, feed, flight - helping the player; green. */
    HELP(GuiGlass.LIME),
    /** Everything else, extensions by default - purple. */
    MORE(GuiGlass.PURPLE);

    private final GuiGlass glass;

    PlayerMenuGroup(GuiGlass glass) {
        this.glass = glass;
    }

    /** The colour of the row's label pane. */
    public GuiGlass glass() {
        return glass;
    }
}
