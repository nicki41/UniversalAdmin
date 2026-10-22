package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.MaintenanceScope;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Replaces one scope's maintenance allow-list wholesale - see {@link MaintenanceService#setAllowedPlayers}. */
public final class SetMaintenanceAllowedPlayersAction implements Action<Set<String>, MaintenanceState> {

    private final MaintenanceService maintenanceService;
    private final MaintenanceScope scope;

    public SetMaintenanceAllowedPlayersAction(MaintenanceService maintenanceService, MaintenanceScope scope) {
        this.maintenanceService = maintenanceService;
        this.scope = scope;
    }

    @Override
    public ActionId id() {
        return scope == MaintenanceScope.NETWORK
                ? ServerActionIds.NETWORK_MAINTENANCE_SET_ALLOWED_PLAYERS : ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS;
    }

    @Override
    public CompletableFuture<ActionResult<MaintenanceState>> execute(ActionContext context, Set<String> input) {
        String key = scope == MaintenanceScope.NETWORK
                ? "server.result.network-maintenance-allowed" : "server.result.maintenance-allowed";
        return maintenanceService.setAllowedPlayers(scope, input, context.actor())
                .thenApply(state -> ActionResult.success(state, MessageKey.of(key), Integer.toString(state.allowedPlayers().size())));
    }
}
