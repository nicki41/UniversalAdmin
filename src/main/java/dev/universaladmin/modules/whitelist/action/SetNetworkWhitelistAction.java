package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.NetworkWhitelistService;
import java.util.concurrent.CompletableFuture;

/** Switches the network whitelist on or off - registered once for each, see {@link NetworkWhitelistService#setEnabled}. */
public final class SetNetworkWhitelistAction implements Action<Void, Void> {

    private final NetworkWhitelistService networkWhitelist;
    private final boolean on;

    public SetNetworkWhitelistAction(NetworkWhitelistService networkWhitelist, boolean on) {
        this.networkWhitelist = networkWhitelist;
        this.on = on;
    }

    @Override
    public ActionId id() {
        return on ? WhitelistActionIds.NETWORK_ENABLE : WhitelistActionIds.NETWORK_DISABLE;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, Void input) {
        return networkWhitelist.setEnabled(on, context.actor()).thenApply(ignored -> on
                ? ActionResult.success(null, MessageKey.of("whitelist.result.network-enabled"), Integer.toString(networkWhitelist.size()))
                : ActionResult.success(null, MessageKey.of("whitelist.result.network-disabled")));
    }
}
