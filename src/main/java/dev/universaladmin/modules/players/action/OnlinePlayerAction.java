package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Reusable {@link Action} for "resolve the online target on the main
 * thread, mutate/read it, fail cleanly if they disconnected between GUI
 * render and click" - the shape shared by most single-target Players
 * actions (heal, feed, extinguish, toggles, numeric sets, effects,
 * gamemode, IP lookup; see {@code PlayerActionRegistrar}). Teleport and
 * inventory-contents actions don't fit this shape (they need a second
 * player, or a before/after diff for the audit entry) and get their own
 * {@link Action} classes instead.
 *
 * @param <I> input type; must expose the target's {@link UUID} via the
 *            {@code targetId} function passed to the constructor
 * @param <R> the action's result type
 */
public final class OnlinePlayerAction<I, R> implements Action<I, R> {

    private final ActionId id;
    private final TaskScheduler scheduler;
    private final Function<I, UUID> targetId;
    private final BiFunction<Player, I, R> work;

    public OnlinePlayerAction(
            ActionId id, TaskScheduler scheduler, Function<I, UUID> targetId, BiFunction<Player, I, R> work) {
        this.id = id;
        this.scheduler = scheduler;
        this.targetId = targetId;
        this.work = work;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public CompletableFuture<ActionResult<R>> execute(ActionContext context, I input) {
        CompletableFuture<ActionResult<R>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(targetId.apply(input));
            if (player == null) {
                // The GUI already rendered this target as online when the
                // button was clicked - this is the race-condition path
                // (player disconnected in between), not a normal NOT_FOUND.
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.action.offline")));
                return;
            }
            try {
                future.complete(ActionResult.success(work.apply(player, input)));
            } catch (IllegalArgumentException e) {
                // Bukkit setters (setHealth above max health, etc.) throw this
                // for an out-of-range value the cheap ActionValidator didn't
                // (or couldn't, without touching live state) catch.
                future.complete(ActionResult.failure(ActionResult.FailureReason.VALIDATION, e.getMessage()));
            } catch (RuntimeException e) {
                future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, e.getMessage()));
            }
        });
        return future;
    }
}
