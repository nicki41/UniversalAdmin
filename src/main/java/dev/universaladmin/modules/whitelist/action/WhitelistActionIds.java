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
}
