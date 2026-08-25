package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import java.util.concurrent.CompletableFuture;

/** Enables maintenance mode - see {@link MaintenanceService#enable}. */
public final class EnableMaintenanceAction implements Action<EnableMaintenanceInput, MaintenanceState> {

    public static final ActionId ID = ServerActionIds.MAINTENANCE_ENABLE;

    private final MaintenanceService maintenanceService;

    public EnableMaintenanceAction(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, EnableMaintenanceInput input) {
        return maintenanceService.enable(input.reason(), input.message(), input.kickNonBypassPlayers(), context.actor())
                .thenApply(ActionResult::success);
    }
}
