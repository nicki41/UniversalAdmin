package dev.universaladmin.core;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.command.CommandRegistry;
import dev.universaladmin.extension.ExtensionManager;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.gui.PlayerMenuRegistry;
import dev.universaladmin.gui.StatusToggleRegistry;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.storage.StorageService;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * The platform: every shared service and registry a
 * {@link dev.universaladmin.module.Module} - built-in or extension - may use.
 * A module receives it through {@link dev.universaladmin.module.ModuleContext#platform()};
 * code outside a module (another plugin) gets it from
 * {@code UniversalAdminProvider.get()}.
 *
 * <p>This is the single "God object" UniversalAdmin allows on purpose - it
 * exists so that dependencies are threaded through constructors explicitly
 * instead of being pulled from static state; see
 * docs/architecture/decisions/0001-modular-core.md. There is no
 * dependency-injection framework: the plugin's bootstrap builds exactly one
 * implementation by hand and passes it down.
 */
public interface UniversalAdmin {

    /**
     * The Bukkit plugin instance of UniversalAdmin itself. Needed for the
     * handful of things that must go through Bukkit's own APIs (registering
     * listeners, scheduling Bukkit tasks). Business logic must not reach for
     * this - it exists for the framework-glue layer, not for services/actions.
     */
    Plugin plugin();

    /** UniversalAdmin's own version, e.g. {@code 1.0.0}. */
    String version();

    Logger logger();

    /**
     * Where settings get registered - by {@code CoreSettings} for platform
     * settings, and by a module under its own
     * {@code ModuleDescriptor.settingsNamespace()}. Most code wants
     * {@link #settings()} instead, to <i>read</i> a value.
     */
    SettingRegistry settingRegistry();

    SettingsService settings();

    TaskScheduler scheduler();

    StorageService storage();

    ServiceRegistry services();

    ActionRegistry actions();

    /**
     * The single entry point for running an {@link dev.universaladmin.action.Action} -
     * see docs/architecture/actions.md. Frontends (GUI, commands, later the
     * web API) call this, not {@code Action.execute} directly, so
     * authorization/validation/audit are never duplicated per frontend.
     */
    ActionExecutor actionExecutor();

    GuiRegistry guiPages();

    /** GUI-framework-scoped machinery (sessions, icons) every {@code AbstractGuiPage} needs - see {@link GuiFramework}. */
    GuiFramework guiFramework();

    /** Buttons other modules contribute to a player's profile page - see {@link PlayerMenuRegistry}. */
    PlayerMenuRegistry playerMenu();

    /**
     * The switches of the main menu's "My status" row - see {@link StatusToggleRegistry}.
     *
     * @since 1.7
     */
    StatusToggleRegistry statusToggles();

    /** Every {@code /admin} subcommand - see {@link CommandRegistry}. */
    CommandRegistry commands();

    /** Where other plugins register their modules - see {@link ExtensionManager}. */
    ExtensionManager extensions();

    PermissionRegistry permissions();

    ModuleRegistry moduleRegistry();

    AuditService auditService();

    MessageService messages();

    NotificationService notifications();

    /**
     * UniversalAdmin across a proxy network - which servers exist, who is
     * where, and messages between servers. Always present; with network
     * mode off {@link NetworkService#enabled()} is {@code false}.
     */
    NetworkService network();

    /**
     * A fresh snapshot of plugin health - see {@link PluginStatus}. Reads
     * the module registry live, so two calls a minute apart can show
     * different active/failed modules.
     */
    PluginStatus status();
}
