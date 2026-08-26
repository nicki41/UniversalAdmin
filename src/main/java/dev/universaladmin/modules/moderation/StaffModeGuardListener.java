package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.UnfreezeInput;
import dev.universaladmin.modules.moderation.gui.EnderChestInspectorPage;
import dev.universaladmin.modules.moderation.gui.InventoryInspectorPage;
import dev.universaladmin.modules.moderation.gui.ModerationGuiContext;
import dev.universaladmin.modules.moderation.gui.ModeratePlayerPage;
import dev.universaladmin.modules.moderation.gui.PunishmentListPage;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.TeleportInput;
import dev.universaladmin.modules.players.action.TeleportKind;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Dispatches held Staff Mode tools and blocks the unconditional Staff Mode
 * protections (item pickup, block breaking - damage is {@code
 * Entity#setInvulnerable}, no listener needed, see {@code
 * StaffModeService#applyEntry}). Every tool-item branch cancels the
 * triggering event <b>before</b> dispatching, regardless of what was
 * clicked - "a tool interaction must never accidentally trigger a normal
 * world action" applies even when a Player-Inspector-holding staff member
 * right-clicks a cow, not just when they right-click another player.
 */
public final class StaffModeGuardListener implements Listener {

    private final StaffModeState staffModeState;
    private final StaffToolItems toolItems;
    private final FreezeRuntimeState freezeState;
    private final ActionExecutor actionExecutor;
    private final ModerationGuiContext guiContext;

    public StaffModeGuardListener(
            StaffModeState staffModeState, StaffToolItems toolItems, FreezeRuntimeState freezeState,
            ActionExecutor actionExecutor, ModerationGuiContext guiContext) {
        this.staffModeState = staffModeState;
        this.toolItems = toolItems;
        this.freezeState = freezeState;
        this.actionExecutor = actionExecutor;
        this.guiContext = guiContext;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!staffModeState.isActive(player.getUniqueId())) {
            return;
        }
        toolItems.toolOf(event.getItem()).ifPresent(tool -> {
            event.setCancelled(true);
            switch (tool) {
                case TELEPORT_PICKER -> openTeleportPicker(player);
                case VANISH_TOGGLE -> runSelfAction(player, ModerationActionIds.VANISH);
                case EXIT_STAFF_MODE -> runSelfAction(player, ModerationActionIds.STAFF_MODE_EXIT);
                default -> {
                    // PLAYER_INSPECTOR/FREEZE_TOOL/INVENTORY_INSPECTOR/
                    // ENDERCHEST_INSPECTOR/MODERATE_TOOL/TELEPORT_TOOL need
                    // an entity target - handled in onInteractEntity.
                }
            }
        });
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // Bukkit fires this event once per hand the client reports for a
        // single physical right-click; without this guard, every tool below
        // (Freeze in particular) runs twice per click. Only the main hand
        // ever holds a tool item, so the off-hand firing is always a no-op
        // duplicate here.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!staffModeState.isActive(player.getUniqueId())) {
            return;
        }
        toolItems.toolOf(player.getInventory().getItemInMainHand()).ifPresent(tool -> {
            event.setCancelled(true);
            if (!(event.getRightClicked() instanceof Player target)) {
                return;
            }
            switch (tool) {
                case PLAYER_INSPECTOR -> PunishmentListPage.forTarget(guiContext, target.getUniqueId()).open(player);
                case INVENTORY_INSPECTOR -> new InventoryInspectorPage(guiContext, target.getUniqueId(), target.getName()).open(player);
                case ENDERCHEST_INSPECTOR -> new EnderChestInspectorPage(guiContext, target.getUniqueId(), target.getName()).open(player);
                case FREEZE_TOOL -> toggleFreeze(player, target);
                case MODERATE_TOOL -> new ModeratePlayerPage(guiContext, target.getUniqueId(), target.getName()).open(player);
                case TELEPORT_TOOL -> player.teleportAsync(target.getLocation());
                default -> {
                    // TELEPORT_PICKER/VANISH_TOGGLE/EXIT_STAFF_MODE don't need a target - handled in onInteract.
                }
            }
        });
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && staffModeState.isActive(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (staffModeState.isActive(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (staffModeState.isActive(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Locks the entire hotbar/inventory while in Staff Mode - it's nothing
     * but the tool kit and otherwise empty (see {@code StaffModeService#applyEntry}),
     * so there is never a legitimate reason to move, swap, or shift-click
     * anything in it. Does not touch a click/drag inside one of
     * UniversalAdmin's own {@link GuiView}-backed pages (Player Inspector,
     * Inventory Inspector, Moderate) - {@code GuiListener} already owns
     * those entirely.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !staffModeState.isActive(player.getUniqueId())) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiView) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !staffModeState.isActive(player.getUniqueId())) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiView) {
            return;
        }
        event.setCancelled(true);
    }

    /** Opens a picker over every other online player, then {@link #openTeleportActionChoice} for the one chosen. */
    private void openTeleportPicker(Player staff) {
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(staff.getUniqueId()))
                .<Player>map(p -> p)
                .toList();
        SelectionDialog.open(staff, guiContext.framework(), guiContext.messages(), guiContext.scheduler(),
                ComponentMessages.render(guiContext.messages().get(MessageKey.of("moderation.gui.staffmode.teleport-picker.select-player"))),
                candidates,
                p -> GuiItem.playerHead(p, Component.text(p.getName()), List.of()),
                (clickCtx, target) -> openTeleportActionChoice(clickCtx.viewer(), target));
    }

    /** Opens the "teleport to them" vs. "bring them" choice for {@code target}, then runs {@code PlayerActionIds#TELEPORT}. */
    private void openTeleportActionChoice(Player staff, Player target) {
        SelectionDialog.open(staff, guiContext.framework(), guiContext.messages(), guiContext.scheduler(),
                ComponentMessages.render(guiContext.messages().get(
                        MessageKey.of("moderation.gui.staffmode.teleport-picker.select-action"), target.getName())),
                List.of(TeleportChoice.TO_TARGET, TeleportChoice.BRING_TARGET),
                choice -> GuiItem.of(choice.material, ComponentMessages.render(guiContext.messages().get(MessageKey.of(choice.labelKey)))),
                (clickCtx, choice) -> actionExecutor.<TeleportInput, Object>execute(
                        PlayerActionIds.TELEPORT, contextFor(staff), TeleportInput.of(choice.kind, target.getUniqueId())));
    }

    private enum TeleportChoice {
        TO_TARGET(TeleportKind.ADMIN_TO_PLAYER, Material.ENDER_PEARL, "moderation.gui.staffmode.teleport-picker.to-target"),
        BRING_TARGET(TeleportKind.BRING_TO_ADMIN, Material.LEAD, "moderation.gui.staffmode.teleport-picker.bring-target");

        private final TeleportKind kind;
        private final Material material;
        private final String labelKey;

        TeleportChoice(TeleportKind kind, Material material, String labelKey) {
            this.kind = kind;
            this.material = material;
            this.labelKey = labelKey;
        }
    }

    private void toggleFreeze(Player player, Player target) {
        ActionContext context = contextFor(player);
        if (freezeState.isFrozen(target.getUniqueId())) {
            actionExecutor.<UnfreezeInput, Object>execute(ModerationActionIds.UNFREEZE, context, new UnfreezeInput(target.getUniqueId()));
        } else {
            actionExecutor.<FreezeInput, Object>execute(
                    ModerationActionIds.FREEZE, context, new FreezeInput(target.getUniqueId(), "Staff Freeze Tool"));
        }
    }

    private void runSelfAction(Player player, ActionId id) {
        actionExecutor.<Void, Object>execute(id, contextFor(player), null);
    }

    private ActionContext contextFor(Player player) {
        return new ActionContext(Actor.player(player.getUniqueId(), player.getName(), new PermissiblePermissionEvaluator(player)), Source.GUI);
    }
}
