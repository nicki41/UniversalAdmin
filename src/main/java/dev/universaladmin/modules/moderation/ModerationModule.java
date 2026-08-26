package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.moderation.action.BanAction;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.EnterStaffModeAction;
import dev.universaladmin.modules.moderation.action.ExitStaffModeAction;
import dev.universaladmin.modules.moderation.action.FreezeAction;
import dev.universaladmin.modules.moderation.action.FreezeDisconnectNoticeAction;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.GodmodeAction;
import dev.universaladmin.modules.moderation.action.IpBanAction;
import dev.universaladmin.modules.moderation.action.IpBanInput;
import dev.universaladmin.modules.moderation.action.KickAction;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteAction;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.NoCollisionAction;
import dev.universaladmin.modules.moderation.action.RecoverStaffSnapshotAction;
import dev.universaladmin.modules.moderation.action.RemoveWarnAction;
import dev.universaladmin.modules.moderation.action.UnbanAction;
import dev.universaladmin.modules.moderation.action.UnfreezeAction;
import dev.universaladmin.modules.moderation.action.UnmuteAction;
import dev.universaladmin.modules.moderation.action.VanishAction;
import dev.universaladmin.modules.moderation.action.WarnAction;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.modules.moderation.gui.ModerationGuiContext;
import dev.universaladmin.modules.moderation.gui.ModerationHomePage;
import dev.universaladmin.modules.moderation.gui.ModeratePlayerPage;
import dev.universaladmin.modules.moderation.jdbc.JdbcPunishmentRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcStaffModeSnapshotRepository;
import dev.universaladmin.modules.moderation.jdbc.JdbcVanishRepository;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Standalone punishment and staff-tools system - kick/ban/tempban/ipban/
 * mute/tempmute/warn/freeze plus unban/unmute/removewarn/unfreeze, Vanish,
 * Godmode, No-Collision, and Staff Mode (crash-safe snapshot/recovery,
 * held tool items), with join/chat enforcement, a GUI wizard, and full
 * audit coverage via {@link dev.universaladmin.action.ActionExecutor}. See
 * docs/user/modules/moderation.md and docs/user/modules/staff-tools.md. No
 * dependency on any external moderation/vanish/essentials plugin - this is
 * wiring only, same shape as {@code PlayersModule}.
 */
public final class ModerationModule implements Module {

    public static final ModuleId ID = ModuleId.core("moderation");

    /** Runs {@link PunishmentService#expireOverdue()} once an hour - pure housekeeping, see {@link PunishmentRepository}. */
    private static final long CLEANUP_PERIOD_TICKS = 20L * 60 * 60;

    /** How often {@link StaffModeTargetTracker#tick()} refreshes the Player Inspector tool and checks for tool-kit tampering - twice a second, cheap enough given how few players are ever in Staff Mode at once. */
    private static final long STAFF_MODE_TRACKER_PERIOD_TICKS = 10L;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Moderation")
            .description("Kick/ban/tempban/ipban/mute/tempmute/warn/freeze tooling plus Vanish/Godmode/Staff Mode, with join/chat enforcement.")
            .icon(new GuiIcon("iron_sword", "Moderation"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // See PlayersModule#onLoad's javadoc for why registration happens
        // here rather than in onEnable.
        context.platform().storage().migrations().register(new ModerationPunishmentMigration());
        context.platform().storage().migrations().register(new ModerationPunishmentIndexMigration());
        context.platform().storage().migrations().register(new VanishStateMigration());
        context.platform().storage().migrations().register(new StaffModeSnapshotMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();
        MessageService messages = context.platform().messages();
        Plugin plugin = context.platform().plugin();
        ServiceRegistry services = context.platform().services();

        PunishmentRepository repository = new JdbcPunishmentRepository(context.platform().storage().dataSource(), scheduler);
        PunishmentService punishmentService = new PunishmentService(repository);
        services.register(PunishmentService.class, punishmentService);

        // See ModerationPolicy's javadoc: pick up a policy a dependency module
        // already registered (a future rank/hierarchy extension), or publish
        // the no-op default - either way, every punishing action below reads
        // whatever ends up registered here.
        Optional<ModerationPolicy> existingPolicy = services.get(ModerationPolicy.class);
        ModerationPolicy policy = existingPolicy.orElseGet(ModerationPolicy::allowAll);
        if (existingPolicy.isEmpty()) {
            services.register(ModerationPolicy.class, policy);
        }
        Optional<VanishVisibilityPolicy> existingVisibilityPolicy = services.get(VanishVisibilityPolicy.class);
        VanishVisibilityPolicy visibilityPolicy = existingVisibilityPolicy.orElseGet(VanishVisibilityPolicy::bypassPermissionOnly);
        if (existingVisibilityPolicy.isEmpty()) {
            services.register(VanishVisibilityPolicy.class, visibilityPolicy);
        }

        // In-memory runtime state every hot-path listener reads instead of
        // the database - see VanishRuntimeState/FreezeRuntimeState/
        // GodmodeState/CollisionState/StaffModeState javadocs.
        VanishRuntimeState vanishRuntimeState = new VanishRuntimeState();
        FreezeRuntimeState freezeRuntimeState = new FreezeRuntimeState();
        GodmodeState godmodeState = new GodmodeState();
        CollisionState collisionState = new CollisionState();
        StaffModeState staffModeState = new StaffModeState();

        VanishRepository vanishRepository = new JdbcVanishRepository(context.platform().storage().dataSource(), scheduler);
        VanishService vanishService = new VanishService(vanishRepository, vanishRuntimeState, visibilityPolicy,
                collisionState, staffModeState, messages, context.platform().settings(), plugin);

        StaffModeSnapshotRepository staffModeSnapshotRepository =
                new JdbcStaffModeSnapshotRepository(context.platform().storage().dataSource(), scheduler);
        StaffToolItems toolItems = new StaffToolItems(plugin, messages);
        StaffModeService staffModeService = new StaffModeService(staffModeSnapshotRepository, staffModeState, godmodeState,
                collisionState, vanishService, toolItems, context.platform().settings(), scheduler);

        context.resources().listener(new ModerationJoinListener(punishmentService, freezeRuntimeState, messages, context.platform().settings()));
        context.resources().listener(new ModerationChatListener(punishmentService, messages, context.platform().settings()));
        context.resources().listener(new VanishReconnectListener(vanishService, vanishRuntimeState));
        context.resources().listener(new VanishEnforcementListener(vanishRuntimeState, context.platform().settings()));
        context.resources().listener(new FreezeGuardListener(freezeRuntimeState, context.platform().settings()));
        context.resources().listener(new FreezeDisconnectListener(freezeRuntimeState, context.platform().actionExecutor()));
        context.resources().listener(new StaffModeRecoveryListener(staffModeService, messages));

        ModerationSettings.registerAll(context.platform().settingRegistry());
        ModerationPermissions.registerAll(context.platform().permissions());

        registerActions(context.platform().actions(), scheduler, punishmentService, messages, policy,
                freezeRuntimeState, vanishService, godmodeState, collisionState, staffModeState,
                context.platform().settings(), staffModeService, context.platform().notifications());
        ModerationGuiContext guiContext = registerGui(context, punishmentService, vanishService, godmodeState, collisionState, staffModeState);

        // StaffModeGuardListener needs the GUI context (Player Inspector/
        // Inventory Inspector open GUI pages) - registered after the context exists.
        context.resources().listener(new StaffModeGuardListener(
                staffModeState, toolItems, freezeRuntimeState, context.platform().actionExecutor(), guiContext));

        scheduleExpirySweep(context, punishmentService);
        scheduleStaffModeTracker(context, staffModeState, toolItems, freezeRuntimeState, vanishService, context.platform().settings(), messages);
    }

    /** Package-private (not {@code private}) so {@code ModerationActionsWiringTest} can exercise it without a full {@link ModuleContext}. */
    static void registerActions(
            ActionRegistry actions, TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages,
            ModerationPolicy policy, FreezeRuntimeState freezeRuntimeState, VanishService vanishService, GodmodeState godmodeState,
            CollisionState collisionState, StaffModeState staffModeState, SettingsService settings,
            StaffModeService staffModeService, NotificationService notifications) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new KickAction(scheduler, punishmentService, messages, policy))
                .permission(ModerationPermissions.KICK)
                .module(module)
                .target(ModerationModule::targetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(KickInput::reason))
                .auditSummary(in -> "Kicked " + in.targetId() + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new BanAction(ModerationActionIds.BAN, scheduler, punishmentService, messages, policy, settings))
                .permission(ModerationPermissions.BAN)
                .module(module)
                .target(ModerationModule::banTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(BanInput::reason))
                .auditSummary(in -> "Banned " + in.targetId() + ": " + in.reason())
                .build());
        actions.register(ActionDefinition.builder(new BanAction(ModerationActionIds.TEMP_BAN, scheduler, punishmentService, messages, policy, settings))
                .permission(ModerationPermissions.TEMPBAN)
                .module(module)
                .target(ModerationModule::banTargetOf)
                .forbidSelfTarget()
                .validator(and(reasonRequired(BanInput::reason), durationRequired(BanInput::expiresAt)))
                .auditSummary(in -> "Temp-banned " + in.targetId() + ": " + in.reason())
                .build());
        actions.register(ActionDefinition.builder(new IpBanAction(scheduler, punishmentService, messages, policy, settings))
                .permission(ModerationPermissions.IPBAN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), in.targetId().toString())))
                .forbidSelfTarget()
                .validator(reasonRequired(IpBanInput::reason))
                .auditSummary(in -> "IP-banned " + in.targetId() + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new MuteAction(ModerationActionIds.MUTE, scheduler, punishmentService, messages, policy, settings))
                .permission(ModerationPermissions.MUTE)
                .module(module)
                .target(ModerationModule::muteTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(MuteInput::reason))
                .auditSummary(in -> "Muted " + in.targetId() + ": " + in.reason())
                .build());
        actions.register(ActionDefinition.builder(new MuteAction(ModerationActionIds.TEMP_MUTE, scheduler, punishmentService, messages, policy, settings))
                .permission(ModerationPermissions.TEMPMUTE)
                .module(module)
                .target(ModerationModule::muteTargetOf)
                .forbidSelfTarget()
                .validator(and(reasonRequired(MuteInput::reason), durationRequired(MuteInput::expiresAt)))
                .auditSummary(in -> "Temp-muted " + in.targetId() + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new WarnAction(scheduler, punishmentService, messages, policy))
                .permission(ModerationPermissions.WARN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), in.targetId().toString())))
                .forbidSelfTarget()
                .validator(reasonRequired(WarnInput::reason))
                .auditSummary(in -> "Warned " + in.targetId() + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new FreezeAction(scheduler, punishmentService, freezeRuntimeState, policy, messages))
                .permission(ModerationPermissions.FREEZE)
                .module(module)
                .target(ModerationModule::freezeTargetOf)
                .forbidSelfTarget()
                .validator(reasonRequired(FreezeInput::reason))
                .auditSummary(in -> "Froze " + in.targetId() + ": " + in.reason())
                .build());

        actions.register(ActionDefinition.builder(new UnbanAction(punishmentService))
                .permission(ModerationPermissions.UNBAN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), in.targetId().toString())))
                .auditSummary(in -> "Unbanned " + in.targetId())
                .build());
        actions.register(ActionDefinition.builder(new UnmuteAction(punishmentService))
                .permission(ModerationPermissions.UNMUTE)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), in.targetId().toString())))
                .auditSummary(in -> "Unmuted " + in.targetId())
                .build());
        actions.register(ActionDefinition.builder(new RemoveWarnAction(punishmentService))
                .permission(ModerationPermissions.REMOVE_WARN)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), "warn#" + in.warnId())))
                .auditSummary(in -> "Removed warning #" + in.warnId() + " from " + in.targetId())
                .build());
        actions.register(ActionDefinition.builder(new UnfreezeAction(punishmentService, freezeRuntimeState))
                .permission(ModerationPermissions.UNFREEZE)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.targetId(), in.targetId().toString())))
                .auditSummary(in -> "Unfroze " + in.targetId())
                .build());

        // Self-directed: no target beyond the actor (see the Void-input
        // Action classes' own javadoc for why no wrapper input record exists).
        actions.register(ActionDefinition.builder(new VanishAction(scheduler, vanishService))
                .permission(ModerationPermissions.VANISH)
                .module(module)
                .auditSummary(in -> "Toggled vanish")
                .build());
        actions.register(ActionDefinition.builder(new GodmodeAction(scheduler, godmodeState))
                .permission(ModerationPermissions.GODMODE)
                .module(module)
                .auditSummary(in -> "Toggled godmode")
                .build());
        actions.register(ActionDefinition.builder(new NoCollisionAction(scheduler, collisionState, vanishService, staffModeState, settings))
                .permission(ModerationPermissions.COLLISION)
                .module(module)
                .auditSummary(in -> "Toggled no-collision")
                .build());
        actions.register(ActionDefinition.builder(new EnterStaffModeAction(scheduler, staffModeService))
                .permission(ModerationPermissions.STAFFMODE)
                .module(module)
                .auditSummary(in -> "Entered staff mode")
                .build());
        actions.register(ActionDefinition.builder(new ExitStaffModeAction(scheduler, staffModeService))
                .permission(ModerationPermissions.STAFFMODE)
                .module(module)
                .auditSummary(in -> "Exited staff mode")
                .build());
        actions.register(ActionDefinition.builder(new RecoverStaffSnapshotAction(staffModeService))
                .permission(ModerationPermissions.STAFFMODE_RECOVER)
                .module(module)
                .target(targetId -> Optional.of(ActionTarget.player(targetId, targetId.toString())))
                .auditSummary(targetId -> "Recovered staff-mode snapshot for " + targetId)
                .build());
        actions.register(ActionDefinition.builder(new FreezeDisconnectNoticeAction(notifications, messages))
                .module(module)
                .target(targetId -> Optional.of(ActionTarget.player(targetId, targetId.toString())))
                .auditSummary(targetId -> "Player disconnected while frozen: " + targetId)
                .build());
    }

    private static Optional<ActionTarget> targetOf(KickInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static Optional<ActionTarget> banTargetOf(BanInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static Optional<ActionTarget> muteTargetOf(MuteInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static Optional<ActionTarget> freezeTargetOf(FreezeInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static <I> ActionValidator<I> reasonRequired(java.util.function.Function<I, String> reason) {
        return (context, input) -> {
            String value = reason.apply(input);
            return (value == null || value.isBlank())
                    ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("moderation.action.reason-required")))
                    : Optional.empty();
        };
    }

    private static <I> ActionValidator<I> durationRequired(java.util.function.Function<I, java.time.Instant> expiresAt) {
        return (context, input) -> expiresAt.apply(input) == null
                ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("moderation.action.duration-required")))
                : Optional.empty();
    }

    /** {@link ActionValidator} has no built-in combinator (it's a plain {@code @FunctionalInterface}) - this is the local one. */
    private static <I> ActionValidator<I> and(ActionValidator<I> first, ActionValidator<I> second) {
        return (context, input) -> {
            Optional<ValidationError> firstError = first.validate(context, input);
            return firstError.isPresent() ? firstError : second.validate(context, input);
        };
    }

    private ModerationGuiContext registerGui(
            ModuleContext context, PunishmentService punishmentService, VanishService vanishService,
            GodmodeState godmodeState, CollisionState collisionState, StaffModeState staffModeState) {
        ModerationGuiContext guiContext = new ModerationGuiContext(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                punishmentService, context.platform().actionExecutor(), context.platform().settings(),
                vanishService, godmodeState, collisionState, staffModeState);

        ModerationHomePage homePage = new ModerationHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(ModerationHomePage.ID));

        // Published for the Players module to call into without either
        // module importing the other's internal classes - see
        // ModerationPlayerLink's javadoc.
        context.platform().services().register(ModerationPlayerLink.class,
                (targetId, targetName) -> ModeratePlayerPage.open(guiContext, targetId, targetName));
        return guiContext;
    }

    private void scheduleExpirySweep(ModuleContext context, PunishmentService punishmentService) {
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> punishmentService.expireOverdue().exceptionally(error -> {
                    context.logger().warning("Punishment expiry sweep failed: " + error.getMessage());
                    return 0;
                }),
                CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
        context.resources().task(task);
    }

    private void scheduleStaffModeTracker(
            ModuleContext context, StaffModeState staffModeState, StaffToolItems toolItems,
            FreezeRuntimeState freezeRuntimeState, VanishService vanishService, SettingsService settings, MessageService messages) {
        StaffModeTargetTracker tracker =
                new StaffModeTargetTracker(staffModeState, toolItems, freezeRuntimeState, vanishService, settings, messages);
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, tracker::tick, STAFF_MODE_TRACKER_PERIOD_TICKS, STAFF_MODE_TRACKER_PERIOD_TICKS);
        context.resources().task(task);
    }
}
