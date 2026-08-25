package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
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

    public static final ActionId ID = WhitelistActionIds.REMOVE;

    private final WhitelistService whitelistService;

    public RemoveWhitelistEntryAction(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, UUID playerId) {
        return whitelistService.remove(playerId).thenApply(ActionResult::success);
    }
}
