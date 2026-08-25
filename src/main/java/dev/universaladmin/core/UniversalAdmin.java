package dev.universaladmin.core;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.storage.DatabaseHealth;
import dev.universaladmin.storage.StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * The composition root: one instance per running plugin, holding every
 * shared service and registry that a {@link dev.universaladmin.module.Module}
 * or {@link dev.universaladmin.action.Action} may need.
 *
 * <p>This is the single "God object" UniversalAdmin allows on purpose - it
 * exists so that dependencies are threaded through constructors explicitly
 * instead of being pulled from static state. Nothing else in the codebase
 * should hold mutable global state; see
 * docs/architecture/decisions/0001-modular-core.md.
 *
 * <p>There is no dependency-injection framework: {@link dev.universaladmin.bootstrap.UniversalAdminPlugin}
 * builds one {@code UniversalAdmin} instance by hand in {@code onEnable} and
 * passes it down. For a project this size that is simpler to read and debug
 * than a DI container, and easy to introduce later if it stops being true.
 */
public final class UniversalAdmin {

    private final Plugin plugin;
    private final String version;
    private final Instant startedAt;
    private final Logger logger;
    private final SettingRegistry settingRegistry;
    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final StorageService storage;
    private final ServiceRegistry services;
    private final ActionRegistry actions;
    private final ActionExecutor actionExecutor;
    private final GuiRegistry guiPages;
    private final GuiFramework guiFramework;
    private final PermissionRegistry permissions;
    private final ModuleRegistry moduleRegistry;
    private final AuditService auditService;
    private final MessageService messages;
    private final NotificationService notifications;

    public UniversalAdmin(
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
            PermissionRegistry permissions,
            ModuleRegistry moduleRegistry,
            AuditService auditService,
            MessageService messages,
            NotificationService notifications) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.version = Objects.requireNonNull(version, "version");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.settingRegistry = Objects.requireNonNull(settingRegistry, "settingRegistry");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.services = Objects.requireNonNull(services, "services");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor");
        this.guiPages = Objects.requireNonNull(guiPages, "guiPages");
        this.guiFramework = Objects.requireNonNull(guiFramework, "guiFramework");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.moduleRegistry = Objects.requireNonNull(moduleRegistry, "moduleRegistry");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    /**
     * The Bukkit plugin instance. Needed for the handful of things that must
     * go through Bukkit's own APIs (registering listeners via
     * {@link dev.universaladmin.module.ModuleResources}, running tasks on the
     * main thread). Business logic must not reach for this - it exists for
     * the framework-glue layer, not for services/actions.
     */
    public Plugin plugin() {
        return plugin;
    }

    public Logger logger() {
        return logger;
    }

    /**
     * Where settings get registered - by {@code CoreSettings} for platform
     * settings, and by a module (via its own {@code ModuleDescriptor.settingsNamespace()})
     * for its own. See docs/development/settings.md. Most code wants
     * {@link #settings()} instead, to *read* a value; this is for
     * *registering* one.
     */
    public SettingRegistry settingRegistry() {
        return settingRegistry;
    }

    public SettingsService settings() {
        return settings;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public StorageService storage() {
        return storage;
    }

    public ServiceRegistry services() {
        return services;
    }

    public ActionRegistry actions() {
        return actions;
    }

    /**
     * The single entry point for running an {@link dev.universaladmin.action.Action} -
     * see docs/architecture/actions.md. Frontends (GUI, commands, later the
     * web API) call this, not {@code Action.execute} directly, so
     * authorization/validation/audit are never duplicated per frontend.
     */
    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }

    public GuiRegistry guiPages() {
        return guiPages;
    }

    /** GUI-framework-scoped machinery (sessions, icons) every {@code AbstractGuiPage} needs - see {@link GuiFramework}. */
    public GuiFramework guiFramework() {
        return guiFramework;
    }

    public PermissionRegistry permissions() {
        return permissions;
    }

    public ModuleRegistry moduleRegistry() {
        return moduleRegistry;
    }

    public AuditService auditService() {
        return auditService;
    }

    public MessageService messages() {
        return messages;
    }

    public NotificationService notifications() {
        return notifications;
    }

    /**
     * A fresh snapshot of plugin health - see {@link PluginStatus}. Reads
     * {@link #moduleRegistry} live, so two calls a minute apart can show
     * different active/failed modules.
     */
    public PluginStatus status() {
        List<ModuleId> active = moduleRegistry.withState(ModuleState.ENABLED).stream()
                .map(module -> module.descriptor().id())
                .toList();
        List<ModuleId> failed = moduleRegistry.withState(ModuleState.FAILED).stream()
                .map(module -> module.descriptor().id())
                .toList();
        // Both components reflect "did bootstrap construct this successfully",
        // not a live health probe - see ComponentStatus and PluginStatus javadoc.
        return new PluginStatus(
                version, startedAt, moduleRegistry.all().size(), active, failed, mapDatabaseStatus(), ComponentStatus.NOT_IMPLEMENTED);
    }

    /** Maps {@link StorageService}'s tracked {@link DatabaseHealth} onto the coarser {@link ComponentStatus}. */
    private ComponentStatus mapDatabaseStatus() {
        return switch (storage.health()) {
            case READY -> ComponentStatus.ONLINE;
            case CONNECTING -> ComponentStatus.DEGRADED;
            case FAILED, DISCONNECTED -> ComponentStatus.OFFLINE;
        };
    }
}
