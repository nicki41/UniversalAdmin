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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Ender-chest counterpart of {@link SetPlayerInventoryContentsAction} - same offline-during-save handling. */
public final class SetPlayerEnderChestContentsAction implements Action<SetEnderChestContentsInput, InventoryChangeSummary> {

    public static final ActionId ID = PlayerActionIds.ENDERCHEST_SET;

    private final TaskScheduler scheduler;

    public SetPlayerEnderChestContentsAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<InventoryChangeSummary>> execute(
            ActionContext context, SetEnderChestContentsInput input) {
        CompletableFuture<ActionResult<InventoryChangeSummary>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(input.targetId());
            if (player == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.CONFLICT, MessageKey.of("players.action.offline-during-save")));
                return;
            }
            Inventory enderChest = player.getEnderChest();
            ItemStack[] before = enderChest.getContents();
            enderChest.setContents(input.contents().toArray(new ItemStack[0]));
            future.complete(ActionResult.success(InventoryChangeSummary.of(before, enderChest.getContents())));
        });
        return future;
    }
}
