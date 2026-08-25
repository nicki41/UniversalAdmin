package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Online-only IP lookup, gated on {@code universaladmin.players.ip}. Kept
 * off {@link dev.universaladmin.modules.players.PlayerSnapshot} entirely
 * (see that record's javadoc) so a viewer without the permission never has
 * the address in hand at all - the GUI only calls this action after
 * confirming the viewer holds the permission.
 */
public final class GetPlayerIpAddressAction implements Action<PlayerTargetInput, String> {

    public static final ActionId ID = PlayerActionIds.GET_IP_ADDRESS;

    private final TaskScheduler scheduler;

    public GetPlayerIpAddressAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<String>> execute(ActionContext context, PlayerTargetInput input) {
        CompletableFuture<ActionResult<String>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(input.targetId());
            if (player == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.action.offline")));
                return;
            }
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.action.no-ip-address")));
                return;
            }
            future.complete(ActionResult.success(address.getAddress().getHostAddress()));
        });
        return future;
    }
}
