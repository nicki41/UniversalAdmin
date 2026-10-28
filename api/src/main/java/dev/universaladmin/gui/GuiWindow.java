package dev.universaladmin.gui;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The chest a player has open while they move through UniversalAdmin's
 * pages. A page switch renders the next {@link GuiView} into the same
 * window and only changes the title - the client keeps its screen and the
 * mouse stays where it was, instead of the close-and-reopen flicker of a
 * new inventory per page (ADR 0027). The inventory's holder, so
 * {@code GuiListener} recognises it.
 *
 * <p>Only one view is {@link #current()} at a time; a view that is not
 * current ignores every write, so a page that finishes loading after the
 * player already moved on cannot paint into the next page.
 */
final class GuiWindow implements InventoryHolder {

    private final UUID viewerId;
    private final int rows;
    private final Inventory inventory;
    private volatile GuiView current;
    private Component title;

    GuiWindow(UUID viewerId, int rows, Component title) {
        this.viewerId = viewerId;
        this.rows = rows;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, rows * GuiLayout.COLUMNS, title);
    }

    UUID viewerId() {
        return viewerId;
    }

    int rows() {
        return rows;
    }

    Component title() {
        return title;
    }

    void title(Component title) {
        this.title = title;
    }

    GuiView current() {
        return current;
    }

    void current(GuiView view) {
        this.current = view;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
