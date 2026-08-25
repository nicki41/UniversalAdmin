package dev.universaladmin.gui;

import org.bukkit.entity.Player;

/**
 * Passed to a {@link GuiButton.ClickHandler} - the live {@link Player} who
 * clicked, which kind of click it was, their {@link GuiSession}, and the
 * navigation operations every button needs instead of touching Bukkit
 * inventory APIs itself (see docs/architecture/gui.md - a click handler
 * still must not contain business logic beyond calling into
 * navigation/a service/an action).
 *
 * <p>{@link #open}/{@link #back} are the entire navigation model: opening a
 * page is always {@link GuiPage#open(Player)}, "back" is replaying however
 * the previous page got itself onto the screen, and "forward" is recording
 * that replay before opening the next page. No separate stack of page
 * identifiers to keep in sync - see docs/development/gui-framework.md.
 */
public final class GuiClickContext {

    private final Player viewer;
    private final GuiClickType clickType;
    private final GuiSession session;
    private final GuiView view;

    GuiClickContext(Player viewer, GuiClickType clickType, GuiSession session, GuiView view) {
        this.viewer = viewer;
        this.clickType = clickType;
        this.session = session;
        this.view = view;
    }

    public Player viewer() {
        return viewer;
    }

    public GuiClickType clickType() {
        return clickType;
    }

    public GuiSession session() {
        return session;
    }

    /** Navigates forward: remembers how to redraw the current page, then opens {@code next}. */
    public void open(GuiPage next) {
        GuiPage current = view.page();
        session.pushHistory(() -> current.open(viewer));
        next.open(viewer);
    }

    /** Replays the previous page's reopen callback, or closes the inventory if there is no history. */
    public void back() {
        session.popHistory().ifPresentOrElse(Runnable::run, this::close);
    }

    public void close() {
        viewer.closeInventory();
    }
}
