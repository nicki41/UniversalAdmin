package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * Registers every "resolve one online player and mutate/read them"
 * {@link ActionDefinition} the Players module offers - everything backed by
 * {@link OnlinePlayerAction} (see that class for why teleport and
 * inventory-contents actions aren't here). One place instead of ~17 nearly
 * identical registration blocks inline in {@code PlayersModule}.
 */
public final class PlayerActionRegistrar {

    private static final String MODULE = "players";

    private PlayerActionRegistrar() {
    }

    public static void registerAll(ActionRegistry actions, TaskScheduler scheduler) {
        actions.register(heal(scheduler));
        actions.register(feed(scheduler));
        actions.register(extinguish(scheduler));
        actions.register(clearEffects(scheduler));
        actions.register(addEffect(scheduler));
        actions.register(removeEffect(scheduler));
        actions.register(setHealth(scheduler));
        actions.register(setFood(scheduler));
        actions.register(setXp(scheduler));
        actions.register(setLevel(scheduler));
        actions.register(toggleFly(scheduler));
        actions.register(setFlySpeed(scheduler));
        actions.register(setWalkSpeed(scheduler));
        actions.register(toggleGlow(scheduler));
        actions.register(toggleGravity(scheduler));
        actions.register(toggleCollision(scheduler));
        actions.register(setGamemode(scheduler));
    }

    private static ActionDefinition<PlayerTargetInput, Void> heal(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.HEAL, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
                    return null;
                });
        return simple(action, PlayerPermissions.HEAL, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<PlayerTargetInput, Void> feed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.FEED, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                    return null;
                });
        return simple(action, PlayerPermissions.FEED, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<PlayerTargetInput, Void> extinguish(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.EXTINGUISH, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    player.setFireTicks(0);
                    return null;
                });
        return simple(action, PlayerPermissions.EXTINGUISH, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<PlayerTargetInput, Void> clearEffects(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Void>(
                PlayerActionIds.EFFECTS_CLEAR, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
                    return null;
                });
        return simple(action, PlayerPermissions.EFFECTS_CLEAR, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<AddEffectInput, Void> addEffect(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<AddEffectInput, Void>(
                PlayerActionIds.EFFECTS_ADD, scheduler, AddEffectInput::targetId, (player, in) -> {
                    player.addPotionEffect(new PotionEffect(in.type(), in.durationTicks(), in.amplifier(), false, true, true));
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.EFFECTS_ADD)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator((context, in) -> in.durationTicks() <= 0
                        ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.invalid-duration")))
                        : Optional.empty())
                .auditSummary(in -> "Added " + in.type().getName() + " " + (in.amplifier() + 1) + " to " + in.targetId())
                .build();
    }

    private static ActionDefinition<RemoveEffectInput, Void> removeEffect(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<RemoveEffectInput, Void>(
                PlayerActionIds.EFFECTS_REMOVE, scheduler, RemoveEffectInput::targetId,
                (player, in) -> {
                    player.removePotionEffect(in.type());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.EFFECTS_REMOVE)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .auditSummary(in -> "Removed " + in.type().getName() + " from " + in.targetId())
                .build();
    }

    private static ActionDefinition<SetDoubleValueInput, Void> setHealth(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetDoubleValueInput, Void>(
                PlayerActionIds.SET_HEALTH, scheduler, SetDoubleValueInput::targetId, (player, in) -> {
                    player.setHealth(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_HEALTH)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(SetDoubleValueInput::value, 0.0, 2048.0))
                .auditSummary(in -> "Set health of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetIntValueInput, Void> setFood(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetIntValueInput, Void>(
                PlayerActionIds.SET_FOOD, scheduler, SetIntValueInput::targetId, (player, in) -> {
                    player.setFoodLevel(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_FOOD)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(intRangeValidator(SetIntValueInput::value, 0, 20))
                .auditSummary(in -> "Set food of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetFloatValueInput, Void> setXp(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.SET_XP, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setExp(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_XP)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), 0.0, 1.0))
                .auditSummary(in -> "Set XP progress of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetIntValueInput, Void> setLevel(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetIntValueInput, Void>(
                PlayerActionIds.SET_LEVEL, scheduler, SetIntValueInput::targetId, (player, in) -> {
                    player.setLevel(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.SET_LEVEL)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(intRangeValidator(SetIntValueInput::value, 0, Integer.MAX_VALUE))
                .auditSummary(in -> "Set level of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleFly(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.FLY_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.getAllowFlight();
                    player.setAllowFlight(next);
                    if (!next) {
                        player.setFlying(false);
                    }
                    return next;
                });
        return simple(action, PlayerPermissions.FLY_TOGGLE, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<SetFloatValueInput, Void> setFlySpeed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.FLY_SPEED, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setFlySpeed(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.FLY_SPEED)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), -1.0, 1.0))
                .auditSummary(in -> "Set fly speed of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<SetFloatValueInput, Void> setWalkSpeed(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetFloatValueInput, Void>(
                PlayerActionIds.WALK_SPEED, scheduler, SetFloatValueInput::targetId, (player, in) -> {
                    player.setWalkSpeed(in.value());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.WALK_SPEED)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .validator(rangeValidator(in -> (double) in.value(), -1.0, 1.0))
                .auditSummary(in -> "Set walk speed of " + in.targetId() + " to " + in.value())
                .build();
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleGlow(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.GLOW_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.isGlowing();
                    player.setGlowing(next);
                    return next;
                });
        return simple(action, PlayerPermissions.GLOW, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleGravity(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.GRAVITY_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.hasGravity();
                    player.setGravity(next);
                    return next;
                });
        return simple(action, PlayerPermissions.GRAVITY, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<PlayerTargetInput, Boolean> toggleCollision(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<PlayerTargetInput, Boolean>(
                PlayerActionIds.COLLISION_TOGGLE, scheduler, PlayerTargetInput::targetId, (player, in) -> {
                    boolean next = !player.isCollidable();
                    player.setCollidable(next);
                    return next;
                });
        return simple(action, PlayerPermissions.COLLISION, PlayerTargetInput::targetId);
    }

    private static ActionDefinition<SetGamemodeInput, Void> setGamemode(TaskScheduler scheduler) {
        var action = new OnlinePlayerAction<SetGamemodeInput, Void>(
                PlayerActionIds.GAMEMODE, scheduler, SetGamemodeInput::targetId, (player, in) -> {
                    player.setGameMode(in.gamemode());
                    return null;
                });
        return ActionDefinition.builder(action)
                .permission(PlayerPermissions.GAMEMODE)
                .module(MODULE)
                .target(in -> Optional.of(target(in.targetId())))
                .auditSummary(in -> "Set gamemode of " + in.targetId() + " to " + in.gamemode())
                .build();
    }

    private static <I, R> ActionDefinition<I, R> simple(
            OnlinePlayerAction<I, R> action, PermissionNode permission, Function<I, UUID> targetId) {
        return ActionDefinition.builder(action)
                .permission(permission)
                .module(MODULE)
                .target(in -> Optional.of(target(targetId.apply(in))))
                .build();
    }

    private static ActionTarget target(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        return ActionTarget.player(playerId, online != null ? online.getName() : playerId.toString());
    }

    private static <I> ActionValidator<I> rangeValidator(ToDoubleFunction<I> value, double min, double max) {
        return (context, input) -> {
            double v = value.applyAsDouble(input);
            return (v < min || v > max)
                    ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.out-of-range"), min, max))
                    : Optional.empty();
        };
    }

    private static <I> ActionValidator<I> intRangeValidator(ToIntFunction<I> value, int min, int max) {
        return (context, input) -> {
            int v = value.applyAsInt(input);
            return (v < min || v > max)
                    ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("players.action.out-of-range"), min, max))
                    : Optional.empty();
        };
    }
}
