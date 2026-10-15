package dev.universaladmin.modules.reports;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;

/** Permission nodes of the Reports module. */
public final class ReportsPermissions {

    /** File a report with {@code /report} - everyone by default. */
    public static final PermissionNode CREATE = PermissionNode.core("reports.create");
    /** See reports (GUI, {@code /admin reports}) and get notified about new ones. */
    public static final PermissionNode VIEW = PermissionNode.core("reports.view");
    /** Claim, resolve and reject reports, and teleport to them. */
    public static final PermissionNode MANAGE = PermissionNode.core("reports.manage");
    /** Not subject to the report cooldown or the open-report limit. */
    public static final PermissionNode BYPASS_LIMITS = PermissionNode.core("reports.bypass-limits");

    private ReportsPermissions() {
    }

    static List<PermissionDefinition> all() {
        return List.of(
                new PermissionDefinition(CREATE, "Report a player with /report", PermissionDefault.TRUE),
                new PermissionDefinition(VIEW, "See player reports and get notified about new ones", PermissionDefault.OP),
                new PermissionDefinition(MANAGE, "Claim, resolve and reject player reports", PermissionDefault.OP),
                new PermissionDefinition(BYPASS_LIMITS, "Report without the cooldown and open-report limit", PermissionDefault.OP));
    }
}
