package dev.universaladmin.modules.moderation;

import dev.universaladmin.gui.GuiView;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Enforces a FREEZE punishment's configurable blocks - each check reads
 * only {@link FreezeRuntimeState} (in-memory), never {@code
 * PunishmentService}, since {@link PlayerMoveEvent} fires every tick a
 * player moves. {@link PlayerTeleportEvent} needs its own handler despite
 * extending {@code PlayerMoveEvent} - same "subclass has its own {@code
 * HandlerList}" Bukkit dispatch quirk as {@code EntityTargetLivingEntityEvent}
 * (see {@link VanishEnforcementListener}).
 */
public final class FreezeGuardListener implements Listener {

    private final FreezeRuntimeState freezeState;
    private final SettingsService settings;

    public FreezeGuardListener(FreezeRuntimeState freezeState, SettingsService settings) {
        this.freezeState = freezeState;
        this.settings = settings;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() && frozen(event.getPlayer(), ModerationSettings.FREEZE_BLOCK_MOVEMENT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (frozen(event.getPlayer(), ModerationSettings.FREEZE_BLOCK_TELEPORT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (frozen(event.getPlayer(), ModerationSettings.FREEZE_BLOCK_INTERACTION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // A frozen player who is also staff shouldn't be locked out of the
        // UniversalAdmin GUI itself - only vanilla inventories are blocked.
        if (event.getView().getTopInventory().getHolder() instanceof GuiView) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player && frozen(player, ModerationSettings.FREEZE_BLOCK_INVENTORY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (frozen(event.getPlayer(), ModerationSettings.FREEZE_BLOCK_COMMANDS)) {
            event.setCancelled(true);
        }
    }

    private boolean frozen(Player player, SettingKey<Boolean> setting) {
        return freezeState.isFrozen(player.getUniqueId()) && settings.get(setting);
    }
}
