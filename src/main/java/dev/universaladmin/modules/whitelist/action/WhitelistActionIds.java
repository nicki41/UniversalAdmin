package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.ActionId;

/** Every {@link ActionId} the Whitelist module registers, in one place - mirrors {@code ServerActionIds}/{@code WorldActionIds}. */
public final class WhitelistActionIds {

    private WhitelistActionIds() {
    }

    public static final ActionId ENABLE = ActionId.core("whitelist.enable");
    public static final ActionId DISABLE = ActionId.core("whitelist.disable");
    public static final ActionId ADD = ActionId.core("whitelist.add");
    public static final ActionId REMOVE = ActionId.core("whitelist.remove");
    public static final ActionId NETWORK_ENABLE = ActionId.core("whitelist.network.enable");
    public static final ActionId NETWORK_DISABLE = ActionId.core("whitelist.network.disable");
    public static final ActionId NETWORK_ADD = ActionId.core("whitelist.network.add");
    public static final ActionId NETWORK_REMOVE = ActionId.core("whitelist.network.remove");
}
