package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * The one fully-generic gamerule action - covers every current and future
 * Minecraft gamerule with no per-rule code, via {@link
 * World#setGameRuleValue(String, String)}. That untyped, string-based
 * setter (alongside its {@code getGameRuleValue(String)}/{@code
 * GameRule.getByName} counterparts used by the GUI layer) is flagged
 * deprecated-for-removal on the target Paper API build, but its typed
 * replacement chain ({@code Registry#GAME_RULE.match}/{@code
 * GameRule#getName()}) is *also* flagged deprecated-for-removal on the same
 * build with no published documentation yet for whatever eventually
 * replaces both - see docs/user/modules/worlds.md's "Gamerules" section.
 * Rather than guess at undocumented pre-release replacement semantics and
 * risk a silent correctness bug, this deliberately keeps using the
 * still-functional, well-documented string API, suppressed rather than
 * chased.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class SetGameRuleAction implements Action<SetGameRuleInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_GAME_RULE;

    private final TaskScheduler scheduler;

    public SetGameRuleAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetGameRuleInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            boolean applied = world.setGameRuleValue(input.ruleName(), input.value());
            future.complete(applied
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.VALIDATION, MessageKey.of("worlds.action.invalid-gamerule-value")));
        });
        return future;
    }
}
