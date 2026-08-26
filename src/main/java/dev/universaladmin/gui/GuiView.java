package dev.universaladmin.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * One rendered instance of a {@link GuiPage} for one player - the live
 * Bukkit {@link Inventory} plus the slot-&gt;{@link GuiButton} table
 * {@link GuiListener} dispatches clicks against. Implements
 * {@link InventoryHolder} itself (rather than a separate wrapper) so
 * {@code inventory.getHolder()} is how {@link GuiListener} recognizes "this
 * is one of ours" - no separate {@code Map<Inventory, GuiView>} to leak if
 * cleanup is ever missed; the view lives exactly as long as the
 * {@link Inventory} Bukkit already tracks for the open {@code InventoryView}.
 *
 * <p>Deliberately holds only a {@link UUID}, never a {@link Player}
 * reference - see docs/development/gui-framework.md. {@link GuiListener}
 * always re-derives the live {@code Player} from the triggering event.
 */
public final class GuiView implements InventoryHolder {

    private final GuiPage page;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private boolean editable;
    private Consumer<GuiView> onClose;
    private Consumer<GuiView> onChange;

    GuiView(GuiPage page, UUID viewerId, int rows, Component title) {
        this.page = page;
        this.viewerId = viewerId;
        this.inventory = Bukkit.createInventory(this, rows * GuiLayout.COLUMNS, title);
    }

    public GuiPage page() {
        return page;
    }

    public GuiPageId pageId() {
        return page.id();
    }

    public UUID viewerId() {
        return viewerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Whether clicks/drags outside {@link GuiButton} slots are left alone
     * instead of cancelled. {@code false} for every page today - this is a
     * hook for a future editable inventory view (e.g. a bulk item editor),
     * not a fully worked-out feature yet. See docs/development/gui-framework.md.
     */
    public boolean editable() {
        return editable;
    }

    public void editable(boolean editable) {
        this.editable = editable;
    }

    /**
     * Registers a callback {@link GuiListener#onClose} runs when this view
     * closes for a "real" reason (not {@code OPEN_NEW}, same distinction the
     * session cleanup already makes) - the seam an {@code editable(true)}
     * page uses to persist its final state without a separate Save button,
     * since the close event fires only once every drag/click the player made
     * has already been applied to {@link #getInventory()}. Not called for
     * every {@code editable} view - only a page that registers one gets it;
     * default is a no-op.
     */
    public void onClose(Consumer<GuiView> callback) {
        this.onClose = callback;
    }

    /** Invoked by {@link GuiListener} once this view genuinely closes. */
    void handleClose() {
        if (onClose != null) {
            onClose.accept(this);
        }
    }

    /**
     * Registers a callback {@link GuiListener} runs, one tick after a click
     * or drag it let through (i.e. {@link #editable()} was {@code true} and
     * the click wasn't on a {@link GuiButton} slot) - the seam a live-sync
     * page (e.g. an inventory editor that must mirror onto the real target
     * immediately, not only on close) uses. One tick later because Bukkit
     * only applies the vanilla click/drag result to {@link #getInventory()}
     * after the triggering event's handlers return, in the same server
     * tick - by the next tick it's guaranteed to be visible here. Not called
     * for every {@code editable} view - only a page that registers one
     * gets it; default is a no-op. Independent of {@link #onClose}: a page
     * can register either, neither, or both (e.g. live-sync per change plus
     * a final flush on close as a safety net).
     */
    public void onChange(Consumer<GuiView> callback) {
        this.onChange = callback;
    }

    /** Invoked by {@link GuiListener}, one tick after a click/drag it let through. */
    void handleChange() {
        if (onChange != null) {
            onChange.accept(this);
        }
    }

    /** Places a purely visual item - clears any button previously at {@code slot}. */
    public void place(int slot, GuiItem item) {
        inventory.setItem(slot, item.itemStack());
        buttons.remove(slot);
    }

    /**
     * Places a clickable button, unless {@code viewer} lacks its
     * {@link GuiButton#permission()} - in which case the slot is left empty
     * ("hide by default", see {@link GuiButton}) and nothing is placed at all.
     */
    public void place(int slot, GuiButton button, Player viewer) {
        if (!button.isVisibleTo(viewer)) {
            return;
        }
        inventory.setItem(slot, button.item().itemStack());
        buttons.put(slot, button);
    }

    public void clear(int slot) {
        inventory.setItem(slot, null);
        buttons.remove(slot);
    }

    /** Clears every slot in {@link GuiLayout}'s content area (rows 1-4), buttons included. */
    public void clearContentArea() {
        for (int slot = GuiLayout.CONTENT_START_SLOT; slot <= GuiLayout.CONTENT_END_SLOT; slot++) {
            clear(slot);
        }
    }

    Optional<GuiButton> buttonAt(int slot) {
        return Optional.ofNullable(buttons.get(slot));
    }
}
