package dev.universaladmin.modules.auditlog;

import dev.universaladmin.audit.AuditService;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.auditlog.gui.AuditLogListPage;
import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * User-facing module over the core {@link AuditService} (already wired up in
 * {@link dev.universaladmin.bootstrap.UniversalAdminPlugin} since every
 * module can write to it). Owns the GUI surface for browsing audit history
 * ({@link AuditLogListPage}/{@code AuditLogDetailPage}) and the periodic
 * retention cleanup - see docs/user/audit-log.md.
 */
public final class AuditLogModule implements Module {

    public static final ModuleId ID = ModuleId.core("audit-log");

    /** Runs {@link AuditService#cleanupExpired()} once an hour - not on every tick, see docs/user/audit-log.md#retention. */
    private static final long CLEANUP_PERIOD_TICKS = 20L * 60 * 60;

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Audit Log")
            .description("Browses the audit history recorded by AuditService.")
            .permissionNamespace("audit")
            .settingsNamespace("audit")
            .icon(new GuiIcon("written_book", "Audit Log"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        PermissionNode viewPermission = PermissionNode.core("audit.view");
        PermissionNode detailsPermission = PermissionNode.core("audit.details");
        context.platform().permissions().register(new PermissionDefinition(
                viewPermission, "View the audit log", PermissionDefault.OP));
        context.platform().permissions().register(new PermissionDefinition(
                detailsPermission, "View full detail (old/new values, metadata) of an audit entry", PermissionDefault.OP));

        AuditService auditService = context.platform().auditService();
        context.platform().guiPages().register(new AuditLogListPage(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                auditService, detailsPermission));
        context.resources().closeable(() -> context.platform().guiPages().unregister(AuditLogListPage.ID));

        scheduleRetentionCleanup(context, auditService);
    }

    private void scheduleRetentionCleanup(ModuleContext context, AuditService auditService) {
        Plugin plugin = context.platform().plugin();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> auditService.cleanupExpired().exceptionally(error -> {
                    context.logger().warning("Audit retention cleanup failed: " + error.getMessage());
                    return 0;
                }),
                CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
        context.resources().task(task);
    }
}
