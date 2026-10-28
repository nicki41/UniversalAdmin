package dev.universaladmin.gui;

import java.util.Optional;
import org.bukkit.Material;

/**
 * What every page's frame needs from outside the page (ADR 0027): the
 * accent colour of the module a page belongs to - the header row is drawn
 * in it - and the main menu the "Start" button leads to.
 *
 * @since 1.7
 */
public interface GuiStyle {

    /** The accent of the module {@code page} belongs to. */
    GuiGlass accent(GuiPageId page);

    /** The icon in the middle of the header of {@code page} - its module's icon. */
    default Material icon(GuiPageId page) {
        return Material.BOOK;
    }

    /** The main menu, if there is one. */
    default Optional<GuiPage> home() {
        return Optional.empty();
    }

    /** Grey headers, no main menu - for tests and tools. */
    static GuiStyle plain() {
        return page -> GuiGlass.GRAY;
    }
}
