package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import java.util.concurrent.CompletableFuture;

/** Disables maintenance mode - see {@link MaintenanceService#disable}. */
public final class DisableMaintenanceAction implements Action<Void, MaintenanceState> {

    public static final ActionId ID = ServerActionIds.MAINTENANCE_DISABLE;

    private final MaintenanceService maintenanceService;

    public DisableMaintenanceAction(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, Void input) {
        return maintenanceService.disable(context.actor()).thenApply(ActionResult::success);
    }
}
