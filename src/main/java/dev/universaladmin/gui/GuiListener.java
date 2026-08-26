package dev.universaladmin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * The single Bukkit listener for every UniversalAdmin GUI - registered once
 * in {@code UniversalAdminPlugin#bootstrapCore}, not per module or per page.
 * See the "Click Handling" section of docs/development/gui-framework.md for
 * the reasoning: one place decides "is this our inventory, and which
 * {@link GuiButton} was clicked", so no feature ever writes its own
 * {@code InventoryClickEvent} handler.
 *
 * <p>Recognizes a UniversalAdmin inventory via {@link GuiView} being its
 * {@link org.bukkit.inventory.InventoryHolder} - not by matching the
 * inventory title, which would silently break the moment a title is
 * localized differently per player.
 */
public final class GuiListener implements Listener {

    private final GuiSessionManager sessions;
    private final Plugin plugin;

    public GuiListener(GuiSessionManager sessions, Plugin plugin) {
        this.sessions = sessions;
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiView view)) {
            return;
        }
        boolean clickedButtonSlot = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory())
                && view.buttonAt(event.getSlot()).isPresent();
        // Plain admin GUI by default: nothing can be taken out, dropped, or
        // shifted in from the player's own inventory - see GuiView#editable.
        // A registered GuiButton's slot (chrome, or a page's own locked
        // filler) stays protected even in an editable view: editable() only
        // ever promised freedom for the *non-button* slots, see its javadoc.
        boolean allowed = view.editable() && !clickedButtonSlot;
        if (!allowed) {
            event.setCancelled(true);
        } else {
            scheduleChange(view);
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            // Click landed in the player's own inventory (or nowhere) - already
            // handled by the cancel above; nothing of ours to dispatch to.
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        view.buttonAt(event.getSlot()).ifPresent(button -> button.handle(new GuiClickContext(
                player, GuiClickType.from(event.getClick()), sessions.sessionFor(player.getUniqueId()), view)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiView view)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesButtonSlot = event.getRawSlots().stream()
                .anyMatch(rawSlot -> rawSlot < topSize && view.buttonAt(rawSlot).isPresent());
        boolean allowed = view.editable() && !touchesButtonSlot;
        if (!allowed) {
            event.setCancelled(true);
        } else {
            scheduleChange(view);
        }
    }

    /**
     * Runs {@link GuiView#handleChange()} one tick after a click/drag this
     * listener let through - see that method's javadoc for why the delay is
     * necessary. Guards against the view having closed in the meantime
     * (e.g. the same tick's close already ran) by re-checking the top
     * inventory still holds this exact view.
     */
    private void scheduleChange(GuiView view) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(view.viewerId());
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() == view) {
                view.handleChange();
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiView view)) {
            return;
        }
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
            // We closed this inventory ourselves to open the next page -
            // the session (navigation history, attributes) must survive.
            return;
        }
        view.handleClose();
        sessions.remove(view.viewerId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Defensive: DISCONNECT already triggers InventoryCloseEvent in the
        // normal case, but a session must never outlive the player either way.
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
