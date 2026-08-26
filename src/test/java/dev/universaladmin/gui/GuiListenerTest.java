package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

/**
 * {@link GuiListener} is the single click/close/quit handler for every
 * UniversalAdmin GUI - see docs/development/gui-framework.md's "Click
 * Handling" and "Player Session" sections. Every Bukkit event type here is
 * mocked directly rather than driven through a real inventory/server (this
 * project doesn't run a Paper-server-mocking framework, see
 * docs/development/testing.md) - each test wires up only the handful of
 * event accessors {@link GuiListener} actually calls.
 */
class GuiListenerTest {

    private final GuiSessionManager sessions = new GuiSessionManager();
    private final Plugin plugin = mockPlugin();
    private final GuiListener listener = new GuiListener(sessions, plugin);

    /** Just enough of the {@code Plugin -> Server -> BukkitScheduler} chain for {@code GuiListener#scheduleChange} not to NPE. */
    private static Plugin mockPlugin() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        return plugin;
    }

    @Test
    void onCloseRemovesTheSessionForAGenuineClose() {
        UUID playerId = UUID.randomUUID();
        sessions.sessionFor(playerId);
        InventoryCloseEvent event = closeEventFor(playerId, InventoryCloseEvent.Reason.PLAYER);

        listener.onClose(event);

        assertFalse(sessions.hasSession(playerId));
    }

    @Test
    void onCloseKeepsTheSessionWhenNavigatingToAnotherPage() {
        UUID playerId = UUID.randomUUID();
        sessions.sessionFor(playerId);
        InventoryCloseEvent event = closeEventFor(playerId, InventoryCloseEvent.Reason.OPEN_NEW);

        listener.onClose(event);

        assertTrue(sessions.hasSession(playerId), "OPEN_NEW is us navigating, not the player leaving the GUI system");
    }

    @Test
    void onCloseIgnoresAnInventoryThatIsNotOurs() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(null);
        when(event.getInventory()).thenReturn(inventory);

        listener.onClose(event); // must not throw, must not touch sessions

        assertEquals(0, sessions.activeSessionCount());
    }

    @Test
    void onQuitRemovesTheSessionAsADefensiveSecondCleanupPath() {
        UUID playerId = UUID.randomUUID();
        sessions.sessionFor(playerId);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onQuit(event);

        assertFalse(sessions.hasSession(playerId));
    }

    @Test
    void onClickCancelsAndDispatchesToTheButtonAtTheClickedSlot() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(false);
        GuiButton.ClickHandler handler = mock(GuiButton.ClickHandler.class);
        GuiButton button = GuiButton.of(null, handler);
        when(view.buttonAt(4)).thenReturn(java.util.Optional.of(button));

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(4);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(event.getWhoClicked()).thenReturn(player);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(handler).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onClickDoesNotCancelAnEditableView() {
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(true);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getClickedInventory()).thenReturn(null);

        listener.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onDragCancelsADragIntoANonEditableView() {
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(false);
        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(invView);

        listener.onDrag(event);

        verify(event).setCancelled(true);
    }

    /**
     * The fix backing the Players inventory-edit pages: {@link GuiView#editable()}'s
     * javadoc promises freedom for non-button slots only - a registered
     * {@link GuiButton} (chrome, or a page's own locked filler) must stay
     * protected even while the view is otherwise editable.
     */
    @Test
    void onClickStillCancelsARegisteredButtonSlotEvenWhenTheViewIsEditable() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(true);
        GuiButton.ClickHandler handler = mock(GuiButton.ClickHandler.class);
        GuiButton button = GuiButton.of(null, handler);
        when(view.buttonAt(4)).thenReturn(java.util.Optional.of(button));

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(4);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(event.getWhoClicked()).thenReturn(player);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(handler).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onClickLeavesANonButtonSlotAloneInAnEditableView() {
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(true);
        when(view.buttonAt(20)).thenReturn(java.util.Optional.empty());

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(20);

        listener.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onDragStillCancelsADragThatTouchesARegisteredButtonSlotEvenWhenEditable() {
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(true);
        when(view.buttonAt(4)).thenReturn(java.util.Optional.of(GuiButton.of(null, ctx -> { })));

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        when(topInventory.getSize()).thenReturn(54);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getRawSlots()).thenReturn(java.util.Set.of(20, 4));

        listener.onDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onDragLeavesNonButtonSlotsAloneInAnEditableView() {
        GuiView view = mock(GuiView.class);
        when(view.editable()).thenReturn(true);
        when(view.buttonAt(org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.Optional.empty());

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(view);
        when(topInventory.getSize()).thenReturn(54);
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(topInventory);

        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(invView);
        when(event.getRawSlots()).thenReturn(java.util.Set.of(20, 21));

        listener.onDrag(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    private InventoryCloseEvent closeEventFor(UUID playerId, InventoryCloseEvent.Reason reason) {
        GuiView view = mock(GuiView.class);
        when(view.viewerId()).thenReturn(playerId);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(view);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getReason()).thenReturn(reason);
        return event;
    }
}
