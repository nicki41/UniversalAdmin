package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.modules.whitelist.WhitelistEntry;
import dev.universaladmin.modules.whitelist.WhitelistService;
import java.util.concurrent.CompletableFuture;

/** Adds a player to the whitelist with UniversalAdmin's own metadata - see {@link WhitelistService#add}. */
public final class AddWhitelistEntryAction implements Action<AddWhitelistEntryInput, WhitelistEntry> {

    public static final ActionId ID = WhitelistActionIds.ADD;

    private final WhitelistService whitelistService;

    public AddWhitelistEntryAction(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<WhitelistEntry>> execute(ActionContext context, AddWhitelistEntryInput input) {
        return whitelistService.add(input.playerId(), input.playerName(), context.actor(), input.reason(), input.notes(), input.expiresAt())
                .thenApply(ActionResult::success);
    }
}
