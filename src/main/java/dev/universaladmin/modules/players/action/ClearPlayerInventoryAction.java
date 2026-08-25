package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The dangerous "Clear Inventory" action - clears main storage, armor, and
 * offhand (matching vanilla {@code /clear} semantics), never the ender
 * chest. The GUI gates this behind {@code ConfirmationDialog} before ever
 * calling it; this class has no confirmation concept of its own (that's a
 * GUI-layer concern, see docs/development/gui-framework.md).
 */
public final class ClearPlayerInventoryAction implements Action<PlayerTargetInput, InventoryChangeSummary> {

    public static final ActionId ID = PlayerActionIds.INVENTORY_CLEAR;

    private final TaskScheduler scheduler;

    public ClearPlayerInventoryAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<InventoryChangeSummary>> execute(ActionContext context, PlayerTargetInput input) {
        CompletableFuture<ActionResult<InventoryChangeSummary>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(input.targetId());
            if (player == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.action.offline")));
                return;
            }
            PlayerInventory inventory = player.getInventory();
            ItemStack[] before = fullContents(inventory);
            inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
            inventory.setHelmet(null);
            inventory.setChestplate(null);
            inventory.setLeggings(null);
            inventory.setBoots(null);
            inventory.setItemInOffHand(null);
            future.complete(ActionResult.success(InventoryChangeSummary.of(before, fullContents(inventory))));
        });
        return future;
    }

    private ItemStack[] fullContents(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        ItemStack[] all = new ItemStack[storage.length + 5];
        System.arraycopy(storage, 0, all, 0, storage.length);
        all[storage.length] = inventory.getHelmet();
        all[storage.length + 1] = inventory.getChestplate();
        all[storage.length + 2] = inventory.getLeggings();
        all[storage.length + 3] = inventory.getBoots();
        all[storage.length + 4] = inventory.getItemInOffHand();
        return all;
    }
}
