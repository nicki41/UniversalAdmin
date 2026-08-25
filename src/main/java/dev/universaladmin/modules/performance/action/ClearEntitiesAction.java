package dev.universaladmin.modules.performance.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.performance.EntityClearFilter;
import dev.universaladmin.modules.performance.PerformanceSettings;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * Removes non-player entities matching a caller-selected filter - the one
 * mutating operation of the Performance module. Deliberately narrow, per
 * docs/development/architecture-rules.md's "Entity Clear" rules: never all entities regardless of filter
 * (an empty/all-protected selection is rejected, not silently widened),
 * never players (hard-coded in {@link EntityClearFilter}, not just a GUI
 * convention), and the configured
 * {@link PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES} are stripped from
 * the requested types before anything runs - a caller cannot opt back into
 * removing a protected type by asking for it directly.
 *
 * <p>Preview counts shown before confirmation come from
 * {@link dev.universaladmin.modules.performance.PerformanceSamplingService#previewClearCount},
 * which applies the exact same {@link EntityClearFilter} - so what a player
 * confirms is what actually happens.
 */
public final class ClearEntitiesAction implements Action<ClearEntitiesInput, Integer> {

    public static final ActionId ID = ActionId.core("performance.clear-entities");

    private final TaskScheduler scheduler;
    private final SettingsService settings;
    private final Logger logger;

    public ClearEntitiesAction(TaskScheduler scheduler, SettingsService settings, Logger logger) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Integer>> execute(ActionContext context, ClearEntitiesInput input) {
        Set<EntityType> protectedTypes = EntityClearFilter.resolveProtectedTypes(
                settings.get(PerformanceSettings.ENTITY_CLEAR_PROTECTED_TYPES), logger);
        Set<EntityType> effectiveTargets = EntityClearFilter.effectiveTargets(input.entityTypes(), protectedTypes);
        if (effectiveTargets.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.VALIDATION, MessageKey.of("performance.action.no-clearable-types")));
        }

        CompletableFuture<ActionResult<Integer>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> future.complete(runOnMainThread(input.worldName(), effectiveTargets)));
        return future;
    }

    private ActionResult<Integer> runOnMainThread(String worldName, Set<EntityType> effectiveTargets) {
        List<World> targetWorlds;
        if (worldName == null) {
            targetWorlds = Bukkit.getWorlds();
        } else {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("performance.action.world-not-found"));
            }
            targetWorlds = List.of(world);
        }

        int removed = 0;
        for (World world : targetWorlds) {
            for (Entity entity : world.getEntities()) {
                if (EntityClearFilter.isClearable(entity, effectiveTargets)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return ActionResult.success(removed, Map.of("removed", removed));
    }
}
