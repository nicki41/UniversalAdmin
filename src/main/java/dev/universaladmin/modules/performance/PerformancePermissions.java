package dev.universaladmin.modules.performance;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/**
 * Every {@link PermissionNode} the Performance module declares - see
 * docs/user/permissions.md for the documented table these mirror.
 */
public final class PerformancePermissions {

    private PerformancePermissions() {
    }

    public static final PermissionNode VIEW = PermissionNode.core("performance.view");

    /** Also the permission {@link dev.universaladmin.notification.NotificationService#notifyStaff} alerts are sent to - see {@link PerformanceSamplingService}. */
    public static final PermissionNode ENTITY_CLEAR = PermissionNode.core("performance.entity-clear");

    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(VIEW, "View performance diagnostics (dashboard, world/entity breakdown)", PermissionDefault.OP),
                new PermissionDefinition(ENTITY_CLEAR, "Clear non-player entities matching a filter, with preview and confirmation", PermissionDefault.OP))
                .forEach(registry::register);
    }
}
