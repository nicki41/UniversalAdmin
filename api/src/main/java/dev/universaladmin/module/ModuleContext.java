package dev.universaladmin.module;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionEventListener;
import dev.universaladmin.audit.AuditListener;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.GuiPage;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.storage.Migration;
import java.util.logging.Logger;
import org.bukkit.event.Listener;

/**
 * What a {@link Module} receives on {@link Module#onLoad}/{@link Module#onEnable}/{@link Module#onDisable}.
 *
 * <p>A context is a module-scoped view over the shared {@link UniversalAdmin}
 * platform: the same registries and services every module (built-in or
 * extension) uses, plus a logger already prefixed with the module id and a
 * {@link ModuleResources} for anything that needs cleanup on disable.
 *
 * <p>The {@code register*} methods are the recommended way to contribute to
 * the platform: each registers into the shared registry <i>and</i> arranges
 * for the entry to be removed again when the module is disabled, so a module
 * (especially an extension that can be disabled at runtime) never leaves a
 * dangling command, page, action or listener behind.
 *
 * <p>One context is built per module by {@code ModuleManager} and reused
 * across all three lifecycle calls.
 */
public record ModuleContext(ModuleId moduleId, UniversalAdmin platform, Logger logger, ModuleResources resources) {

    static ModuleContext of(ModuleId moduleId, UniversalAdmin platform) {
        Logger moduleLogger = Logger.getLogger(platform.logger().getName() + "." + moduleId);
        ModuleResources resources = new ModuleResources(platform.plugin(), moduleId);
        return new ModuleContext(moduleId, platform, moduleLogger, resources);
    }

    /** Registers a database migration. Call from {@link Module#onLoad} - see that method's javadoc. */
    public void registerMigration(Migration migration) {
        platform.storage().migrations().register(migration);
    }

    public <I, R> void registerAction(ActionDefinition<I, R> definition) {
        platform.actions().register(definition);
        resources.closeable(() -> platform.actions().unregister(definition.id()));
    }

    public void registerPage(GuiPage page) {
        platform.guiPages().register(page);
        resources.closeable(() -> platform.guiPages().unregister(page.id()));
    }

    public void registerCommand(AdminCommand command) {
        platform.commands().register(command);
        resources.closeable(() -> platform.commands().unregister(command.name()));
    }

    public void registerPlayerMenuEntry(PlayerMenuEntry entry) {
        platform.playerMenu().register(entry);
        resources.closeable(() -> platform.playerMenu().unregister(entry.id()));
    }

    /**
     * Registers a permission node. Nodes registered after UniversalAdmin
     * itself finished enabling (an extension) are synced to Bukkit by the
     * extension loader right after the module enables.
     */
    public void registerPermission(PermissionDefinition definition) {
        platform.permissions().register(definition);
        resources.closeable(() -> platform.permissions().unregister(definition.node()));
    }

    public <T> void registerSetting(SettingDefinition<T> definition) {
        platform.settingRegistry().register(definition);
        resources.closeable(() -> platform.settingRegistry().unregister(definition.key()));
    }

    /** Publishes {@code instance} for other modules - see {@link dev.universaladmin.core.ServiceRegistry}. */
    public <T> void registerService(Class<T> type, T instance) {
        platform.services().register(type, instance);
        resources.closeable(() -> platform.services().unregister(type, instance));
    }

    public void registerListener(Listener listener) {
        resources.listener(listener);
    }

    /** Observes every recorded audit event - see {@link dev.universaladmin.audit.AuditService#subscribe}. */
    public void subscribeAudit(AuditListener listener) {
        platform.auditService().subscribe(listener);
        resources.closeable(() -> platform.auditService().unsubscribe(listener));
    }

    /** Observes every action execution - see {@link dev.universaladmin.action.ActionEvent}. */
    public void subscribe(ActionEventListener listener) {
        platform.actionExecutor().subscribe(listener);
        resources.closeable(() -> platform.actionExecutor().unsubscribe(listener));
    }
}
