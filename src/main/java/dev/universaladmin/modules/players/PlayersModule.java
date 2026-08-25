package dev.universaladmin.modules.players;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.players.action.ClearPlayerInventoryAction;
import dev.universaladmin.modules.players.action.GetPlayerIpAddressAction;
import dev.universaladmin.modules.players.action.GetPlayerProfileAction;
import dev.universaladmin.modules.players.action.InventoryChangeSummary;
import dev.universaladmin.modules.players.action.PlayerActionRegistrar;
import dev.universaladmin.modules.players.action.SetEnderChestContentsInput;
import dev.universaladmin.modules.players.action.SetInventoryContentsInput;
import dev.universaladmin.modules.players.action.SetPlayerEnderChestContentsAction;
import dev.universaladmin.modules.players.action.SetPlayerInventoryContentsAction;
import dev.universaladmin.modules.players.action.TeleportInput;
import dev.universaladmin.modules.players.action.TeleportPlayerAction;
import dev.universaladmin.modules.players.gui.PlayerBrowserHomePage;
import dev.universaladmin.modules.players.jdbc.JdbcPlayerProfileRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;

/**
 * Player Browser/Profile/Actions module. See docs/user/modules/players.md
 * for the full feature list; this class is only wiring - migrations,
 * repository/service construction, permission/action/settings registration,
 * the join/quit listener, and the {@code players.home} GUI page.
 */
public final class PlayersModule implements Module {

    public static final ModuleId ID = ModuleId.core("players");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Players")
            .description("Player browser (online/offline/recently seen/search), profile, and admin actions.")
            .icon(new GuiIcon("player_head", "Players"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        // Registered in onLoad, not onEnable: ModuleManager runs loadAll()
        // (every module's onLoad) to completion before enableAll() starts,
        // and UniversalAdminPlugin runs storage.migrations().runPending()
        // in between - so by the time onEnable below constructs the
        // repository, player_profiles is guaranteed to already exist. See
        // docs/architecture/threading.md.
        context.platform().storage().migrations().register(new PlayerProfileMigration());
        context.platform().storage().migrations().register(new PlayerProfileIndexMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        TaskScheduler scheduler = context.platform().scheduler();
        PlayerProfileRepository repository =
                new JdbcPlayerProfileRepository(context.platform().storage().dataSource(), scheduler);
        PlayerSessionTracker sessionTracker = new PlayerSessionTracker();
        PlayerService playerService = new PlayerService(repository, scheduler, sessionTracker);
        context.platform().services().register(PlayerService.class, playerService);

        context.resources().listener(new PlayerActivityListener(playerService, sessionTracker));

        PlayersSettings.registerAll(context.platform().settingRegistry());
        PlayerPermissions.registerAll(context.platform().permissions());

        registerActions(context, playerService, scheduler);
        registerGui(context, playerService);
    }

    private void registerActions(ModuleContext context, PlayerService playerService, TaskScheduler scheduler) {
        ActionRegistry actions = context.platform().actions();
        String module = ID.key().name();

        actions.register(ActionDefinition.builder(new GetPlayerProfileAction(playerService))
                .permission(PlayerPermissions.VIEW)
                .module(module)
                .notAudited()
                .build());
        actions.register(ActionDefinition.builder(new GetPlayerIpAddressAction(scheduler))
                .permission(PlayerPermissions.IP)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString())))
                // Audited (unlike GetPlayerProfileAction) - who looked up
                // whose IP address is exactly the kind of sensitive read an
                // audit trail exists for. The IP itself never goes into the
                // summary/metadata: an audit-log viewer without
                // PlayerPermissions.IP must not learn it that way.
                .auditSummary(input -> "Viewed IP address of " + input.targetId())
                .build());
        actions.register(ActionDefinition.builder(new TeleportPlayerAction(scheduler))
                .permission(PlayerPermissions.TELEPORT)
                .module(module)
                .target(PlayersModule::teleportTarget)
                .auditSummary(input -> "Teleport (" + input.kind() + ") " + input.targetId())
                .build());
        actions.register(ActionDefinition.builder(new SetPlayerInventoryContentsAction(scheduler))
                .permission(PlayerPermissions.INVENTORY_EDIT)
                .module(module)
                .target(PlayersModule::inventoryTarget)
                .auditSummary(input -> "Set " + input.section() + " contents of " + input.targetId())
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());
        actions.register(ActionDefinition.builder(new SetPlayerEnderChestContentsAction(scheduler))
                .permission(PlayerPermissions.ENDERCHEST_EDIT)
                .module(module)
                .target(PlayersModule::enderChestTarget)
                .auditSummary(input -> "Set ender chest contents of " + input.targetId())
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());
        actions.register(ActionDefinition.builder(new ClearPlayerInventoryAction(scheduler))
                .permission(PlayerPermissions.INVENTORY_EDIT)
                .module(module)
                .target(input -> Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString())))
                .auditSummary(input -> "Cleared inventory of " + input.targetId())
                .auditDetails((input, result) -> inventoryDetails(result))
                .build());

        PlayerActionRegistrar.registerAll(actions, scheduler);
    }

    private static Optional<ActionTarget> teleportTarget(TeleportInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static Optional<ActionTarget> inventoryTarget(SetInventoryContentsInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    private static Optional<ActionTarget> enderChestTarget(SetEnderChestContentsInput input) {
        return Optional.of(ActionTarget.player(input.targetId(), input.targetId().toString()));
    }

    /** Coarse before/after slot-count summary, never the actual items - see {@link InventoryChangeSummary}. */
    private static <R> AuditDetails inventoryDetails(ActionResult<R> result) {
        if (!(result instanceof ActionResult.Success<R> success) || !(success.value() instanceof InventoryChangeSummary summary)) {
            return AuditDetails.EMPTY;
        }
        return AuditDetails.builder().oldValue(summary.describeBefore()).newValue(summary.describeAfter()).build();
    }

    private void registerGui(ModuleContext context, PlayerService playerService) {
        PlayerBrowserHomePage homePage = new PlayerBrowserHomePage(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                playerService, context.platform().actionExecutor(), context.platform().settings(), context.platform().services());
        context.platform().guiPages().register(homePage);
        context.resources().closeable(() -> context.platform().guiPages().unregister(PlayerBrowserHomePage.ID));
    }
}
