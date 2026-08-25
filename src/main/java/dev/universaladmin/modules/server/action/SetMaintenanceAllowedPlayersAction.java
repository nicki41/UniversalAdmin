package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Replaces the maintenance-mode allow-list wholesale - see {@link MaintenanceService#setAllowedPlayers}. */
public final class SetMaintenanceAllowedPlayersAction implements Action<Set<String>, MaintenanceState> {

    public static final ActionId ID = ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS;

    private final MaintenanceService maintenanceService;

    public SetMaintenanceAllowedPlayersAction(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, Set<String> input) {
        return maintenanceService.setAllowedPlayers(input, context.actor()).thenApply(ActionResult::success);
    }
}
