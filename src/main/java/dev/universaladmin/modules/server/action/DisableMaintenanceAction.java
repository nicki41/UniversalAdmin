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

/** Disables maintenance mode for one scope - see {@link MaintenanceService#disable}. */
public final class DisableMaintenanceAction implements Action<Void, MaintenanceState> {

    private final MaintenanceService maintenanceService;
    private final MaintenanceScope scope;

    public DisableMaintenanceAction(MaintenanceService maintenanceService, MaintenanceScope scope) {
        this.maintenanceService = maintenanceService;
        this.scope = scope;
    }

    @Override
    public ActionId id() {
        return scope == MaintenanceScope.NETWORK ? ServerActionIds.NETWORK_MAINTENANCE_DISABLE : ServerActionIds.MAINTENANCE_DISABLE;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, Void input) {
        String key = scope == MaintenanceScope.NETWORK ? "server.result.network-maintenance-off" : "server.result.maintenance-off";
        return maintenanceService.disable(scope, context.actor())
                .thenApply(state -> ActionResult.success(state, MessageKey.of(key)));
    }
}
