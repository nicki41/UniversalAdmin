package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.SelfTargetPolicy;
import dev.universaladmin.action.Source;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.UnbanInput;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Exercises the permission/module/validator wiring {@link ModerationModule#registerActions}
 * attaches to every {@link ActionDefinition} - without ever calling {@link
 * dev.universaladmin.action.Action#execute}, which would touch {@code
 * Bukkit.getPlayer(...)} and needs a running server (see {@code
 * PlayerActionRegistrarTest}, the identical pattern this mirrors).
 */
class ModerationActionsWiringTest {

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

    private static final MessageService NOOP_MESSAGES = (key, args) -> key.value();
    private static final ActionContext CONTEXT = new ActionContext(Actor.console(), Source.SYSTEM);

    private final ActionRegistry registry = new ActionRegistry();

    ModerationActionsWiringTest() {
        // Every dependency below is only ever *stored* by registerActions
        // (building Action instances), never called - constructors don't
        // dereference their fields, so null/never-invoked fakes are safe
        // here the same way NEVER_INVOKED/punishmentServiceNeverInvoked()
        // already were before this test grew Vanish/Staff-Mode coverage.
        VanishService vanishServiceNeverInvoked = new VanishService(null, null, null, null, null, NOOP_MESSAGES, null, null);
        StaffModeService staffModeServiceNeverInvoked =
                new StaffModeService(null, new StaffModeState(), new GodmodeState(), new CollisionState(), vanishServiceNeverInvoked, null, null, NEVER_INVOKED);
        ModerationModule.registerActions(registry, NEVER_INVOKED, punishmentServiceNeverInvoked(), NOOP_MESSAGES, ModerationPolicy.allowAll(),
                new FreezeRuntimeState(), vanishServiceNeverInvoked, new GodmodeState(), new CollisionState(), new StaffModeState(),
                null, staffModeServiceNeverInvoked, null);
    }

    @Test
    void everyRegisteredActionHasItsPermissionAndModule() {
        var kick = registry.<KickInput, Object>get(ModerationActionIds.KICK).orElseThrow();
        assertEquals(ModerationPermissions.KICK, kick.permission());
        assertEquals("moderation", kick.module());

        var ban = registry.<BanInput, Object>get(ModerationActionIds.BAN).orElseThrow();
        assertEquals(ModerationPermissions.BAN, ban.permission());
        var tempBan = registry.<BanInput, Object>get(ModerationActionIds.TEMP_BAN).orElseThrow();
        assertEquals(ModerationPermissions.TEMPBAN, tempBan.permission());

        var unban = registry.<UnbanInput, Object>get(ModerationActionIds.UNBAN).orElseThrow();
        assertEquals(ModerationPermissions.UNBAN, unban.permission());
    }

    @Test
    void punishingActionsForbidSelfTarget() {
        var kick = registry.<KickInput, Object>get(ModerationActionIds.KICK).orElseThrow();
        assertEquals(SelfTargetPolicy.FORBIDDEN, kick.selfTargetPolicy());

        var ban = registry.<BanInput, Object>get(ModerationActionIds.BAN).orElseThrow();
        assertEquals(SelfTargetPolicy.FORBIDDEN, ban.selfTargetPolicy());

        // Revoking (unban) has no self-target restriction - nothing stops an
        // admin from removing their own (e.g. IP-inherited) ban.
        var unban = registry.<UnbanInput, Object>get(ModerationActionIds.UNBAN).orElseThrow();
        assertEquals(SelfTargetPolicy.ALLOWED, unban.selfTargetPolicy());
    }

    @Test
    void kickRejectsABlankReason() {
        var kick = registry.<KickInput, Object>get(ModerationActionIds.KICK).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(kick.validator().validate(CONTEXT, new KickInput(target, "")).isPresent());
        assertTrue(kick.validator().validate(CONTEXT, new KickInput(target, "   ")).isPresent());
        assertTrue(kick.validator().validate(CONTEXT, new KickInput(target, null)).isPresent());
        assertFalse(kick.validator().validate(CONTEXT, new KickInput(target, "Cheating")).isPresent());
    }

    @Test
    void banRejectsABlankReasonButDoesNotRequireADuration() {
        var ban = registry.<BanInput, Object>get(ModerationActionIds.BAN).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(ban.validator().validate(CONTEXT, new BanInput(target, "", null)).isPresent());
        assertFalse(ban.validator().validate(CONTEXT, new BanInput(target, "Cheating", null)).isPresent());
    }

    @Test
    void tempBanRequiresBothAReasonAndADuration() {
        var tempBan = registry.<BanInput, Object>get(ModerationActionIds.TEMP_BAN).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(tempBan.validator().validate(CONTEXT, new BanInput(target, "", Instant.now().plusSeconds(60))).isPresent());
        assertTrue(tempBan.validator().validate(CONTEXT, new BanInput(target, "Griefing", null)).isPresent());
        assertFalse(tempBan.validator().validate(CONTEXT, new BanInput(target, "Griefing", Instant.now().plusSeconds(60))).isPresent());
    }

    @Test
    void tempMuteRequiresBothAReasonAndADuration() {
        var tempMute = registry.<MuteInput, Object>get(ModerationActionIds.TEMP_MUTE).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(tempMute.validator().validate(CONTEXT, new MuteInput(target, "Spam", null)).isPresent());
        assertFalse(tempMute.validator().validate(CONTEXT, new MuteInput(target, "Spam", Instant.now().plusSeconds(60))).isPresent());
    }

    @Test
    void warnRequiresAReason() {
        var warn = registry.<WarnInput, Object>get(ModerationActionIds.WARN).orElseThrow();
        UUID target = UUID.randomUUID();

        assertTrue(warn.validator().validate(CONTEXT, new WarnInput(target, "")).isPresent());
        assertFalse(warn.validator().validate(CONTEXT, new WarnInput(target, "Rude")).isPresent());
    }

    @Test
    void unbanRequiresNoValidation() {
        var unban = registry.<UnbanInput, Object>get(ModerationActionIds.UNBAN).orElseThrow();
        assertFalse(unban.validator().validate(CONTEXT, new UnbanInput(UUID.randomUUID())).isPresent());
    }

    @Test
    void freezeRequiresAReasonAndForbidsSelfTarget() {
        var freeze = registry.<FreezeInput, Object>get(ModerationActionIds.FREEZE).orElseThrow();
        assertEquals(ModerationPermissions.FREEZE, freeze.permission());
        assertEquals(SelfTargetPolicy.FORBIDDEN, freeze.selfTargetPolicy());
        UUID target = UUID.randomUUID();
        assertTrue(freeze.validator().validate(CONTEXT, new FreezeInput(target, "")).isPresent());
        assertFalse(freeze.validator().validate(CONTEXT, new FreezeInput(target, "Griefing")).isPresent());
    }

    @Test
    void selfDirectedTogglesAreRegisteredWithNoTargetRestriction() {
        var vanish = registry.<Void, Object>get(ModerationActionIds.VANISH).orElseThrow();
        assertEquals(ModerationPermissions.VANISH, vanish.permission());
        assertEquals(SelfTargetPolicy.ALLOWED, vanish.selfTargetPolicy());

        var godmode = registry.<Void, Object>get(ModerationActionIds.GODMODE).orElseThrow();
        assertEquals(ModerationPermissions.GODMODE, godmode.permission());

        var staffModeEnter = registry.<Void, Object>get(ModerationActionIds.STAFF_MODE_ENTER).orElseThrow();
        assertEquals(ModerationPermissions.STAFFMODE, staffModeEnter.permission());
    }

    /** Only ever used to build {@link ActionDefinition}s in this test - {@code registerActions} never invokes it during registration. */
    private static PunishmentService punishmentServiceNeverInvoked() {
        return new PunishmentService(null);
    }
}
