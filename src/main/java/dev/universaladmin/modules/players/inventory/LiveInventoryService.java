package dev.universaladmin.modules.players.inventory;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.action.InventoryEditRecord;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Keeps every open inventory/ender chest editor live: once per tick each
 * editor's slots are merged with the player's real inventory (see {@link
 * SlotSync}), so the staff member sees the player's own moves immediately
 * and the player gets the staff member's edits immediately.
 *
 * <p>An editor ends when its view is no longer open (closed, or navigated
 * away from) or the player leaves. Whatever the staff member changed during
 * the session is then recorded once, through {@link ActionExecutor} - one
 * audit entry per editing session ({@link PlayerActionIds#INVENTORY_EDIT} /
 * {@link PlayerActionIds#ENDERCHEST_EDIT}), not one per click.
 *
 * <p>Main thread only: {@link #tick()} runs from a repeating Bukkit task.
 */
public final class LiveInventoryService {

    private final Map<UUID, LiveInventorySession> sessions = new HashMap<>();
    private final ActionExecutor actionExecutor;
    private final TaskScheduler scheduler;
    private final MessageService messages;

    public LiveInventoryService(ActionExecutor actionExecutor, TaskScheduler scheduler, MessageService messages) {
        this.actionExecutor = actionExecutor;
        this.scheduler = scheduler;
        this.messages = messages;
    }

    /**
     * Starts keeping {@code slots} of {@code view} equal to {@code target}'s
     * inventory/ender chest, filling them with its current contents right
     * away. An earlier editor of the same viewer ends first.
     */
    public void start(GuiView view, Player viewer, Player target, LiveInventoryKind kind, boolean editable, List<LiveInventorySlot> slots) {
        LiveInventorySession previous = sessions.remove(viewer.getUniqueId());
        if (previous != null) {
            finish(previous, viewer);
        }
        sessions.put(viewer.getUniqueId(), new LiveInventorySession(view, viewer.getUniqueId(), target, kind, editable, slots));
    }

    /** One sync pass over every open editor - called every tick. */
    public void tick() {
        Iterator<LiveInventorySession> iterator = sessions.values().iterator();
        List<Runnable> afterwards = new ArrayList<>();
        while (iterator.hasNext()) {
            LiveInventorySession session = iterator.next();
            Player viewer = Bukkit.getPlayer(session.viewerId());
            Player target = Bukkit.getPlayer(session.targetId());
            boolean open = viewer != null && session.view().isOpenFor(viewer);
            if (target != null) {
                // Also once more after the view closed: a click in the same
                // tick as the close is already in the view's inventory.
                session.sync(target);
            }
            if (open && target != null) {
                continue;
            }
            iterator.remove();
            if (open) {
                afterwards.add(() -> {
                    viewer.closeInventory();
                    viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("command.prefix"))
                            + messages.get(MessageKey.of("players.gui.inventory.target-left"), session.targetName())));
                });
            }
            afterwards.add(() -> finish(session, viewer));
        }
        // Outside the loop: closing an inventory fires events that must not
        // run while this map is being iterated.
        afterwards.forEach(Runnable::run);
    }

    /** Ends every editor - on module disable. */
    public void closeAll() {
        List<LiveInventorySession> open = new ArrayList<>(sessions.values());
        sessions.clear();
        for (LiveInventorySession session : open) {
            Player viewer = Bukkit.getPlayer(session.viewerId());
            Player target = Bukkit.getPlayer(session.targetId());
            if (target != null) {
                session.sync(target);
            }
            if (viewer != null && session.view().isOpenFor(viewer)) {
                viewer.closeInventory();
            }
            finish(session, viewer);
        }
    }

    /** Records the session's changes (if any) and tells the staff member what changed. */
    private void finish(LiveInventorySession session, Player viewer) {
        if (!session.editable() || !session.changedAnything()) {
            return;
        }
        ActionId id = session.kind() == LiveInventoryKind.INVENTORY ? PlayerActionIds.INVENTORY_EDIT : PlayerActionIds.ENDERCHEST_EDIT;
        InventoryEditRecord record = new InventoryEditRecord(session.targetId(), session.targetName(), session.summary());
        actionExecutor.execute(id, contextFor(session, viewer), record).whenComplete((result, error) -> scheduler.runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(session.viewerId());
            if (online != null && error == null) {
                GuiActions.report(online, messages, result, MessageKey.of("players.gui.action.success"));
            }
        }));
    }

    /**
     * The staff member as actor. If they already left, their permissions are
     * gone with them - the session was opened as editable, which is exactly
     * the permission the record needs.
     */
    private static ActionContext contextFor(LiveInventorySession session, Player viewer) {
        PermissionEvaluator permissions = viewer != null && viewer.isOnline()
                ? new PermissiblePermissionEvaluator(viewer)
                : node -> session.editable();
        String name = viewer != null ? PlayerNames.real(viewer) : Bukkit.getOfflinePlayer(session.viewerId()).getName();
        return new ActionContext(Actor.player(session.viewerId(), name == null ? session.viewerId().toString() : name, permissions), Source.GUI);
    }
}
