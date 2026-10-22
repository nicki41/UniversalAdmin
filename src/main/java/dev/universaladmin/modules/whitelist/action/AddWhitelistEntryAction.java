package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.WhitelistEntry;
import dev.universaladmin.modules.whitelist.WhitelistScope;
import dev.universaladmin.modules.whitelist.WhitelistService;
import java.util.concurrent.CompletableFuture;

/** Adds a player to the whitelist with UniversalAdmin's own metadata - see {@link WhitelistService#add}. */
public final class AddWhitelistEntryAction implements Action<AddWhitelistEntryInput, WhitelistEntry> {

    private final WhitelistService whitelistService;
    private final WhitelistScope scope;

    /** @param whitelistService the service of {@code scope}: this server's or the network's */
    public AddWhitelistEntryAction(WhitelistService whitelistService, WhitelistScope scope) {
        this.whitelistService = whitelistService;
        this.scope = scope;
    }

    @Override
    public ActionId id() {
        return scope == WhitelistScope.NETWORK ? WhitelistActionIds.NETWORK_ADD : WhitelistActionIds.ADD;
    }

    @Override
    public CompletableFuture<ActionResult<WhitelistEntry>> execute(ActionContext context, AddWhitelistEntryInput input) {
        return whitelistService.add(input.playerId(), input.playerName(), context.actor(), input.reason(), input.notes(), input.expiresAt())
                .thenApply(entry -> entry.expiresAt() == null
                        ? ActionResult.success(entry, MessageKey.of(key("added")), entry.playerName())
                        : ActionResult.success(entry, MessageKey.of(key("added-temporary")),
                                entry.playerName(), DurationParser.formatRemaining(entry.expiresAt())));
    }

    private String key(String name) {
        return "whitelist.result." + (scope == WhitelistScope.NETWORK ? "network-" : "") + name;
    }
}
