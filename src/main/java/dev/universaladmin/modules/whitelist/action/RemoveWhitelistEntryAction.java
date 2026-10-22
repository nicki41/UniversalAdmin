package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.WhitelistScope;
import dev.universaladmin.modules.whitelist.WhitelistService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Removes a player from the whitelist - both the admin-initiated GUI/command
 * path and the automatic expiry sweep/join-time cleanup run through this
 * same action (with {@code Actor.system("whitelist-expiry")} for the
 * latter), so every removal - intentional or automatic - gets the exact
 * same audit entry shape. Raw {@code UUID} input, no wrapper record needed.
 */
public final class RemoveWhitelistEntryAction implements Action<UUID, Void> {

    private final WhitelistService whitelistService;
    private final WhitelistScope scope;

    /** @param whitelistService the service of {@code scope}: this server's or the network's */
    public RemoveWhitelistEntryAction(WhitelistService whitelistService, WhitelistScope scope) {
        this.whitelistService = whitelistService;
        this.scope = scope;
    }

    @Override
    public ActionId id() {
        return scope == WhitelistScope.NETWORK ? WhitelistActionIds.NETWORK_REMOVE : WhitelistActionIds.REMOVE;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, UUID playerId) {
        return whitelistService.remove(playerId)
                .thenApply(ignored -> ActionResult.success(null, MessageKey.of(scope == WhitelistScope.NETWORK ? "whitelist.result.network-removed" : "whitelist.result.removed"), PlayerNames.of(playerId)));
    }
}
