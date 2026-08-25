package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Commits the mirrored {@code PlayerInventoryPage}/{@code PlayerEquipmentPage}
 * GUI contents back to the real, online target - see the "Why not just open
 * the live PlayerInventory directly" design note in docs/user/modules/players.md.
 * {@code CONFLICT} (not {@code NOT_FOUND}) on an offline target: this is
 * specifically the "admin clicked Save after the target logged off" race,
 * distinct from a plain lookup miss.
 */
public final class SetPlayerInventoryContentsAction implements Action<SetInventoryContentsInput, InventoryChangeSummary> {

    public static final ActionId ID = PlayerActionIds.INVENTORY_SET;

    private final TaskScheduler scheduler;

    public SetPlayerInventoryContentsAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<InventoryChangeSummary>> execute(
            ActionContext context, SetInventoryContentsInput input) {
        CompletableFuture<ActionResult<InventoryChangeSummary>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(input.targetId());
            if (player == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.CONFLICT, MessageKey.of("players.action.offline-during-save")));
                return;
            }
            PlayerInventory inventory = player.getInventory();
            InventoryChangeSummary summary = switch (input.section()) {
                case MAIN -> applyMain(inventory, input.contents());
                case EQUIPMENT -> applyEquipment(inventory, input.contents());
            };
            future.complete(ActionResult.success(summary));
        });
        return future;
    }

    private InventoryChangeSummary applyMain(PlayerInventory inventory, List<ItemStack> contents) {
        ItemStack[] before = inventory.getStorageContents();
        inventory.setStorageContents(contents.toArray(new ItemStack[0]));
        return InventoryChangeSummary.of(before, inventory.getStorageContents());
    }

    private InventoryChangeSummary applyEquipment(PlayerInventory inventory, List<ItemStack> contents) {
        ItemStack[] before = equipmentOf(inventory);
        inventory.setHelmet(contents.get(0));
        inventory.setChestplate(contents.get(1));
        inventory.setLeggings(contents.get(2));
        inventory.setBoots(contents.get(3));
        inventory.setItemInOffHand(contents.get(4));
        return InventoryChangeSummary.of(before, equipmentOf(inventory));
    }

    private ItemStack[] equipmentOf(PlayerInventory inventory) {
        return new ItemStack[] {
            inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(),
            inventory.getBoots(), inventory.getItemInOffHand()
        };
    }
}
