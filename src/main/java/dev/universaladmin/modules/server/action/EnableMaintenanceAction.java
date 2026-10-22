package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.MaintenanceScope;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import java.util.concurrent.CompletableFuture;

/**
 * Enables maintenance mode for one scope - see {@link MaintenanceService#enable}.
 * Registered once per scope, each with its own id and permission.
 */
public final class EnableMaintenanceAction implements Action<EnableMaintenanceInput, MaintenanceState> {

    private final MaintenanceService maintenanceService;
    private final MaintenanceScope scope;

    public EnableMaintenanceAction(MaintenanceService maintenanceService, MaintenanceScope scope) {
        this.maintenanceService = maintenanceService;
        this.scope = scope;
    }

    @Override
    public ActionId id() {
        return scope == MaintenanceScope.NETWORK ? ServerActionIds.NETWORK_MAINTENANCE_ENABLE : ServerActionIds.MAINTENANCE_ENABLE;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, EnableMaintenanceInput input) {
        String prefix = scope == MaintenanceScope.NETWORK ? "server.result.network-maintenance" : "server.result.maintenance";
        return maintenanceService.enable(scope, input.reason(), input.message(), input.kickNonBypassPlayers(), context.actor())
                .thenApply(state -> ActionResult.success(state,
                        MessageKey.of(prefix + (input.kickNonBypassPlayers() ? "-on-kick" : "-on")),
                        state.reason() == null || state.reason().isBlank() ? MessageKey.of("common.none") : state.reason(),
                        Integer.toString(state.allowedPlayers().size())));
    }
}
