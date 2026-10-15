package dev.universaladmin.bootstrap;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.command.CommandRegistry;
import dev.universaladmin.core.ComponentStatus;
import dev.universaladmin.core.PluginStatus;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.extension.ExtensionManager;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.gui.PlayerMenuRegistry;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.storage.StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * The one {@link UniversalAdmin} implementation - a plain value holder built
 * by hand in {@link UniversalAdminPlugin#onEnable()}, see
 * docs/architecture/decisions/0001-modular-core.md.
 */
public record UniversalAdminPlatform(
        Plugin plugin,
        String version,
        Instant startedAt,
        Logger logger,
        SettingRegistry settingRegistry,
        SettingsService settings,
        TaskScheduler scheduler,
        StorageService storage,
        ServiceRegistry services,
        ActionRegistry actions,
        ActionExecutor actionExecutor,
        GuiRegistry guiPages,
        GuiFramework guiFramework,
        PlayerMenuRegistry playerMenu,
        CommandRegistry commands,
        ExtensionManager extensions,
        PermissionRegistry permissions,
        ModuleRegistry moduleRegistry,
        AuditService auditService,
        MessageService messages,
        NotificationService notifications) implements UniversalAdmin {

    public UniversalAdminPlatform {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(settingRegistry, "settingRegistry");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(actionExecutor, "actionExecutor");
        Objects.requireNonNull(guiPages, "guiPages");
        Objects.requireNonNull(guiFramework, "guiFramework");
        Objects.requireNonNull(playerMenu, "playerMenu");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(moduleRegistry, "moduleRegistry");
        Objects.requireNonNull(auditService, "auditService");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(notifications, "notifications");
    }

    @Override
    public PluginStatus status() {
        List<ModuleId> active = moduleRegistry.withState(ModuleState.ENABLED).stream()
                .map(module -> module.descriptor().id())
                .toList();
        List<ModuleId> failed = moduleRegistry.withState(ModuleState.FAILED).stream()
                .map(module -> module.descriptor().id())
                .toList();
        // Database reflects the tracked health; web is not built yet - see
        // ComponentStatus and PluginStatus javadoc.
        return new PluginStatus(
                version, startedAt, moduleRegistry.all().size(), active, failed, databaseStatus(), ComponentStatus.NOT_IMPLEMENTED);
    }

    private ComponentStatus databaseStatus() {
        return switch (storage.health()) {
            case READY -> ComponentStatus.ONLINE;
            case CONNECTING -> ComponentStatus.DEGRADED;
            case FAILED, DISCONNECTED -> ComponentStatus.OFFLINE;
        };
    }
}
