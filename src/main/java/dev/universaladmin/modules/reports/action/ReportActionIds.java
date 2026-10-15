package dev.universaladmin.modules.reports.action;

import dev.universaladmin.action.ActionId;

/** Every action the Reports module registers - stable ids, also used in the audit log. */
public final class ReportActionIds {

    public static final ActionId CREATE = ActionId.core("reports.create");
    public static final ActionId CLAIM = ActionId.core("reports.claim");
    public static final ActionId RESOLVE = ActionId.core("reports.resolve");
    public static final ActionId REJECT = ActionId.core("reports.reject");
    public static final ActionId TELEPORT = ActionId.core("reports.teleport");

    private ReportActionIds() {
    }
}
