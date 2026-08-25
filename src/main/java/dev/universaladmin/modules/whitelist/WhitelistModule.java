package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.ActionValidator;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryAction;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryInput;
import dev.universaladmin.modules.whitelist.action.DisableWhitelistAction;
import dev.universaladmin.modules.whitelist.action.EnableWhitelistAction;
import dev.universaladmin.modules.whitelist.action.RemoveWhitelistEntryAction;
import dev.universaladmin.modules.whitelist.gui.WhitelistGuiContext;
import dev.universaladmin.modules.whitelist.gui.WhitelistHomePage;
import dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistEntryRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wraps Paper's native whitelist (enable/disable/list/add/remove) with
 * UniversalAdmin's own metadata (added-by, added-at, reason, notes,
 * expiration) and optional temporary access - see {@link WhitelistService}'s
 * javadoc for how the native list and this module's own table stay in sync,
 * and {@link WhitelistSource}'s javadoc for the ownership rule that keeps
 * automatic expiry from ever touching an entry this module didn't create.
 */
public final class WhitelistModule implements Module {

    public static final ModuleId ID = ModuleId.core("whitelist");

    /** Mirrors {@code ModerationModule}'s hourly expiry sweep - catches entries whose owner never logs back in. */
    private static final long SWEEP_PERIOD_TICKS = 20L * 60 * 60;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Whitelist")
            .description("Native whitelist plus UniversalAdmin metadata: added-by/at, reason, notes, and optional expiry.")
            .icon(new GuiIcon("name_tag", "Whitelist"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // See PlayersModule#onLoad's javadoc for why registration happens
        // here rather than in onEnable.
        context.platform().storage().migrations().register(new WhitelistMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();

        WhitelistSettings.registerAll(context.platform().settingRegistry());
        WhitelistPermissions.registerAll(context.platform().permissions());

        WhitelistEntryRepository repository =
                new JdbcWhitelistEntryRepository(context.platform().storage().dataSource(), scheduler);
        WhitelistService whitelistService = new DefaultWhitelistService(repository, scheduler);
        context.platform().services().register(WhitelistService.class, whitelistService);

        registerActions(context.platform().actions(), scheduler, whitelistService);

        context.resources().listener(new WhitelistJoinListener(repository, context.platform().actionExecutor(), context.platform().messages()));
        scheduleExpirySweep(context, repository);

        WhitelistGuiContext guiContext = new WhitelistGuiContext(context.platform().guiFramework(), context.platform().messages(),
                scheduler, context.platform().actionExecutor(), context.platform().settings(), whitelistService);
        WhitelistHomePage homePage = new WhitelistHomePage(guiContext);
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(WhitelistHomePage.ID));
    }

    private static void registerActions(ActionRegistry actions, TaskScheduler scheduler, WhitelistService whitelistService) {
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new EnableWhitelistAction(scheduler))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .auditSummary(in -> "Enabled the whitelist")
                .build());
        actions.register(ActionDefinition.builder(new DisableWhitelistAction(scheduler))
                .permission(WhitelistPermissions.TOGGLE)
                .module(module)
                .auditSummary(in -> "Disabled the whitelist")
                .build());

        actions.register(ActionDefinition.builder(new AddWhitelistEntryAction(whitelistService))
                .permission(WhitelistPermissions.ADD)
                .module(module)
                .target(in -> Optional.of(ActionTarget.player(in.playerId(), in.playerName())))
                .validator(temporaryRequiresPermission())
                .auditSummary(in -> "Whitelisted " + in.playerName() + (in.expiresAt() != null ? " until " + in.expiresAt() : ""))
                .auditDetails((in, result) -> AuditDetails.builder()
                        .reason(in.reason())
                        .newValue(in.expiresAt() != null ? "expires " + in.expiresAt() : "permanent")
                        .build())
                .build());

        actions.register(ActionDefinition.builder(new RemoveWhitelistEntryAction(whitelistService))
                .permission(WhitelistPermissions.REMOVE)
                .module(module)
                .target(playerId -> Optional.of(ActionTarget.player(playerId, playerId.toString())))
                .auditSummary(playerId -> "Removed " + playerId + " from the whitelist")
                .build());
    }

    private static ActionValidator<AddWhitelistEntryInput> temporaryRequiresPermission() {
        return (context, input) -> input.expiresAt() != null && !context.actor().hasPermission(WhitelistPermissions.TEMPORARY)
                ? Optional.of(ValidationError.of(ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("whitelist.action.temporary-not-permitted")))
                : Optional.empty();
    }

    private void scheduleExpirySweep(ModuleContext context, WhitelistEntryRepository repository) {
        WhitelistExpirySweeper sweeper = new WhitelistExpirySweeper(repository, context.platform().actionExecutor());
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> sweeper.sweep().exceptionally(error -> {
                    context.logger().warning("Whitelist expiry sweep failed: " + error.getMessage());
                    return 0;
                }),
                SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
        context.resources().task(task);
    }
}
