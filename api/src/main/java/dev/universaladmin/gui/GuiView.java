package dev.universaladmin.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * One rendered instance of a {@link GuiPage} for one player - the
 * slot-&gt;{@link GuiButton} table {@code GuiListener} dispatches clicks
 * against, drawn into the player's {@link GuiWindow}. Every page switch
 * renders a new view into the same window; only the newest one is
 * {@linkplain #isOpenFor shown}, and a view that is no longer shown ignores
 * every write (ADR 0027). So a page that loads asynchronously checks
 * {@link #isOpenFor} before it draws - or simply draws, and nothing happens
 * if the player moved on.
 *
 * <p>Deliberately holds only a {@link UUID}, never a {@link Player}
 * reference - see docs/development/gui-framework.md. {@code GuiListener}
 * always re-derives the live {@code Player} from the triggering event.
 */
public final class GuiView implements InventoryHolder {

    private final GuiPage page;
    private final GuiWindow window;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private boolean editable;
    private final ClickThrottle clickThrottle = new ClickThrottle(MIN_BUTTON_CLICK_INTERVAL_NANOS);
    private Consumer<GuiView> onClose;
    private Consumer<GuiView> onChange;

    GuiView(GuiPage page, GuiWindow window) {
        this.page = page;
        this.window = window;
    }

    /** The view shown in {@code inventory}, if it is one of UniversalAdmin's menus. @since 1.7 */
    public static Optional<GuiView> of(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof GuiWindow window) {
            return Optional.ofNullable(window.current());
        }
        return Optional.empty();
    }

    /** Whether {@code viewer} has this exact view open right now - not an earlier or a later page. @since 1.7 */
    public boolean isOpenFor(Player viewer) {
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == window && isCurrent();
    }

    public GuiPage page() {
        return page;
    }

    /**
     * Minimum time between two dispatched button clicks in the same view -
     * a double click (or a lagging client re-sending a click) must never run
     * a "ban"/"clear inventory"/"shutdown" button twice.
     */
    static final long MIN_BUTTON_CLICK_INTERVAL_NANOS = 150_000_000L;

    /**
     * Records a button click at {@code nowNanos} ({@link System#nanoTime()})
     * and returns whether it should be dispatched - {@code false} for a click
     * that follows the previous dispatched one within
     * {@link #MIN_BUTTON_CLICK_INTERVAL_NANOS}.
     */
    boolean acceptButtonClick(long nowNanos) {
        return clickThrottle.tryAcquire(nowNanos);
    }

    public GuiPageId pageId() {
        return page.id();
    }

    public UUID viewerId() {
        return window.viewerId();
    }

    GuiWindow window() {
        return window;
    }

    boolean isCurrent() {
        return window.current() == this;
    }

    @Override
    public Inventory getInventory() {
        return window.getInventory();
    }

    /**
     * Whether clicks/drags outside {@link GuiButton} slots are left alone
     * instead of cancelled - the inventory editors mirror a real inventory
     * this way. An editable view gets no filling panes in its empty slots.
     * See docs/development/gui-framework.md.
     */
    public boolean editable() {
        return editable;
    }

    public void editable(boolean editable) {
        this.editable = editable;
    }

    /**
     * Registers a callback {@code GuiListener#onClose} runs when this view
     * closes for a "real" reason (not {@code OPEN_NEW}, same distinction the
     * session cleanup already makes) - the seam an {@code editable(true)}
     * page uses to persist its final state without a separate Save button,
     * since the close event fires only once every drag/click the player made
     * has already been applied to {@link #getInventory()}. Not called for
     * every {@code editable} view - only a page that registers one gets it;
     * default is a no-op. A view with a close callback is never replaced in
     * place: the next page opens a new window, so the callback runs.
     */
    public void onClose(Consumer<GuiView> callback) {
        this.onClose = callback;
    }

    boolean hasCloseCallback() {
        return onClose != null;
    }

    /** Invoked by {@code GuiListener} once this view genuinely closes. */
    void handleClose() {
        if (onClose != null) {
            onClose.accept(this);
        }
    }

    /**
     * Registers a callback {@code GuiListener} runs, one tick after a click
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

    /** Invoked by {@code GuiListener}, one tick after a click/drag it let through. */
    void handleChange() {
        if (onChange != null) {
            onChange.accept(this);
        }
    }

    /** Places a purely visual item - clears any button previously at {@code slot}. */
    public void place(int slot, GuiItem item) {
        if (!isCurrent()) {
            return;
        }
        window.getInventory().setItem(slot, item.itemStack());
        buttons.remove(slot);
    }

    /**
     * Places a clickable button, unless {@code viewer} lacks its
     * {@link GuiButton#permission()} - in which case the slot is left empty
     * ("hide by default", see {@link GuiButton}) and nothing is placed at all.
     */
    public void place(int slot, GuiButton button, Player viewer) {
        if (!isCurrent() || !button.isVisibleTo(viewer)) {
            return;
        }
        window.getInventory().setItem(slot, button.item().itemStack());
        buttons.put(slot, button);
    }

    /** Empties {@code slot} - in a menu that means the black pane of unused space, in an editable view a real empty slot. */
    public void clear(int slot) {
        if (!isCurrent()) {
            return;
        }
        window.getInventory().setItem(slot, editable ? null : GuiItem.pane(GuiGlass.BLACK).itemStack());
        buttons.remove(slot);
    }

    /** Clears every slot in {@link GuiLayout}'s content area (rows 1-4), buttons included. */
    public void clearContentArea() {
        for (int slot = GuiLayout.CONTENT_START_SLOT; slot <= GuiLayout.CONTENT_END_SLOT; slot++) {
            clear(slot);
        }
    }

    /** Fills every slot nothing was drawn into with the black pane of unused space - not in an editable view. */
    void fillEmpty() {
        if (editable || !isCurrent()) {
            return;
        }
        Inventory inventory = window.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, GuiItem.pane(GuiGlass.BLACK).itemStack());
            }
        }
    }

    Optional<GuiButton> buttonAt(int slot) {
        return Optional.ofNullable(buttons.get(slot));
    }
}
