package dev.universaladmin.modules.players.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@link dev.universaladmin.action.ActionValidator}s and
 * permission/module wiring {@link PlayerActionRegistrar} attaches to every
 * {@link ActionDefinition} - without ever calling {@link
 * dev.universaladmin.action.Action#execute}, which would touch {@code
 * Bukkit.getPlayer(...)} and needs a running server this project's unit
 * tests don't stand up (see docs/development/testing.md). {@link
 * #NEVER_INVOKED} exists only to satisfy the constructor; none of these
 * tests exercise the action body.
 */
class PlayerActionRegistrarTest {

    private static final TaskScheduler NEVER_INVOKED = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runOnMainThread(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    };

    private static final ActionContext CONTEXT = new ActionContext(Actor.console(), Source.SYSTEM);

    private final ActionRegistry registry = new ActionRegistry();

    PlayerActionRegistrarTest() {
        PlayerActionRegistrar.registerAll(registry, NEVER_INVOKED);
    }

    @Test
    void everyRegisteredActionHasItsPermissionAndModule() {
        ActionDefinition<SetIntValueInput, Void> setFood = registry.<SetIntValueInput, Void>get(PlayerActionIds.SET_FOOD).orElseThrow();

        assertEquals(PlayerPermissions.SET_FOOD, setFood.permission());
        assertEquals("players", setFood.module());
    }

    @Test
    void setHealthRejectsOutOfRangeValues() {
        var definition = registry.<SetDoubleValueInput, Void>get(PlayerActionIds.SET_HEALTH).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(definition.validator().validate(CONTEXT, new SetDoubleValueInput(target, -1.0)).isPresent());
        assertTrue(definition.validator().validate(CONTEXT, new SetDoubleValueInput(target, 3000.0)).isPresent());
        assertFalse(definition.validator().validate(CONTEXT, new SetDoubleValueInput(target, 10.0)).isPresent());
    }

    @Test
    void setFoodRejectsOutsideZeroToTwenty() {
        var definition = registry.<SetIntValueInput, Void>get(PlayerActionIds.SET_FOOD).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(definition.validator().validate(CONTEXT, new SetIntValueInput(target, -1)).isPresent());
        assertTrue(definition.validator().validate(CONTEXT, new SetIntValueInput(target, 21)).isPresent());
        assertFalse(definition.validator().validate(CONTEXT, new SetIntValueInput(target, 20)).isPresent());
    }

    @Test
    void setXpRejectsOutsideZeroToOne() {
        var definition = registry.<SetFloatValueInput, Void>get(PlayerActionIds.SET_XP).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(definition.validator().validate(CONTEXT, new SetFloatValueInput(target, -0.01f)).isPresent());
        assertTrue(definition.validator().validate(CONTEXT, new SetFloatValueInput(target, 1.01f)).isPresent());
        assertFalse(definition.validator().validate(CONTEXT, new SetFloatValueInput(target, 0.5f)).isPresent());
    }

    @Test
    void setLevelRejectsNegativeValues() {
        var definition = registry.<SetIntValueInput, Void>get(PlayerActionIds.SET_LEVEL).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(definition.validator().validate(CONTEXT, new SetIntValueInput(target, -1)).isPresent());
        assertFalse(definition.validator().validate(CONTEXT, new SetIntValueInput(target, 0)).isPresent());
    }

    @Test
    void flyAndWalkSpeedRejectOutsideMinusOneToOne() {
        var flySpeed = registry.<SetFloatValueInput, Void>get(PlayerActionIds.FLY_SPEED).orElseThrow();
        var walkSpeed = registry.<SetFloatValueInput, Void>get(PlayerActionIds.WALK_SPEED).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(flySpeed.validator().validate(CONTEXT, new SetFloatValueInput(target, 1.5f)).isPresent());
        assertFalse(flySpeed.validator().validate(CONTEXT, new SetFloatValueInput(target, -1.0f)).isPresent());
        assertTrue(walkSpeed.validator().validate(CONTEXT, new SetFloatValueInput(target, -2.0f)).isPresent());
    }

    @Test
    void addEffectRejectsNonPositiveDuration() {
        var definition = registry.<AddEffectInput, Void>get(PlayerActionIds.EFFECTS_ADD).orElseThrow();
        UUID target = UUID.randomUUID();
        // null PotionEffectType is fine here: the validator only looks at
        // durationTicks and referencing a real PotionEffectType constant
        // would touch Bukkit's registry, which needs a running server (see
        // docs/development/testing.md) - not available in this unit test.

        assertTrue(definition.validator().validate(CONTEXT, new AddEffectInput(target, null, 0, 0)).isPresent());
        assertFalse(definition.validator().validate(CONTEXT, new AddEffectInput(target, null, 600, 0)).isPresent());
    }
}
