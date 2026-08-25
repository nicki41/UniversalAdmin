package dev.universaladmin.bootstrap;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditSchemaMigration;
import dev.universaladmin.audit.AuditSchemaMigrationV2;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.audit.DefaultAuditService;
import dev.universaladmin.audit.jdbc.JdbcAuditEventRepository;
import dev.universaladmin.command.UniversalAdminCommand;
import dev.universaladmin.config.ConfigMigrationRunner;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiListener;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.gui.GuiSessionManager;
import dev.universaladmin.gui.MainMenuPage;
import dev.universaladmin.gui.MaterialIconProvider;
import dev.universaladmin.gui.PlaceholderGuiPage;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.localization.YamlLocaleMessageService;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleManager;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.modules.auditlog.AuditLogModule;
import dev.universaladmin.modules.moderation.ModerationModule;
import dev.universaladmin.modules.performance.PerformanceModule;
import dev.universaladmin.modules.players.PlayersModule;
import dev.universaladmin.modules.server.ServerModule;
import dev.universaladmin.modules.settings.SettingsModule;
import dev.universaladmin.modules.whitelist.WhitelistModule;
import dev.universaladmin.modules.worlds.WorldsModule;
import dev.universaladmin.notification.InGameNotificationService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.PaperTaskScheduler;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.ReloadConfigAction;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.StorageService;
import dev.universaladmin.telemetry.PlayerCounts;
import dev.universaladmin.telemetry.TelemetryBootstrap;
import dev.universaladmin.telemetry.TelemetryEnvironment;
import java.time.Instant;
import java.util.random.RandomGenerator;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Bukkit/Paper entry point. Its only job is composition: build every
 * critical core service by hand (there is no DI framework, see
 * {@link UniversalAdmin}), drive the built-in modules through
 * {@link ModuleManager}, and register the handful of things that must be
 * registered with Bukkit itself (the command, permissions).
 *
 * <p>No business logic belongs in this class. If you're tempted to add an
 * {@code if} here that isn't wiring, it almost certainly belongs in a module,
 * service, or action instead.
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li>{@link #onLoad()} - runs before any plugin's {@code onEnable}, per
 *       Bukkit's own plugin lifecycle. UniversalAdmin has nothing that must
 *       happen this early (no service it needs to register with another
 *       plugin before enable), so this is intentionally thin.
 *   <li>{@link #onEnable()} bootstrap phase - constructs every
 *       <b>critical</b> core component (config file + migration, settings,
 *       scheduler, storage + DB migrations, the shared registries,
 *       audit/message/notification services). If any of this throws, startup
 *       aborts: the exception is logged with context and the plugin disables
 *       itself. There is nothing a partially-initialized UniversalAdmin
 *       could safely do. A bad *value* in config.yml is different - see
 *       {@link YamlSettingsService}, which falls back to defaults instead of
 *       throwing, so a typo in {@code gui.page-size} never reaches here.
 *   <li>{@link #onEnable()} module phase - {@link ModuleManager#loadAll()}
 *       then {@link ModuleManager#enableAll()}, for whichever built-in
 *       modules are enabled in {@code modules.*} (see {@link CoreSettings}).
 *       A built-in module is <b>not</b> critical: if one fails, it is
 *       isolated ({@code FAILED}, logged in full) and every other module
 *       still gets to load/enable. See docs/architecture/modules.md for the
 *       full critical/non-critical breakdown.
 *   <li>{@link #onDisable()} - stops telemetry, disables modules (releasing
 *       their resources via {@link dev.universaladmin.module.ModuleResources}
 *       even if a module's own {@code onDisable} throws), then closes storage
 *       and the scheduler. Best-effort: one step failing does not skip the
 *       rest.
 * </ul>
 */
public final class UniversalAdminPlugin extends JavaPlugin {

    private TaskScheduler scheduler;
    private StorageService storage;
    private ModuleManager moduleManager;
    private TelemetryBootstrap telemetry;

    @Override
    public void onLoad() {
        // Nothing needs to happen before other plugins' onEnable today (e.g.
        // registering a Bukkit ServicesManager entry) - heavier init happens
        // in onEnable, which is the Bukkit convention. Kept as an explicit,
        // documented phase rather than omitted, so the lifecycle is
        // deliberate rather than accidental.
        getLogger().fine("UniversalAdmin loading.");
    }

    @Override
    public void onEnable() {
        Instant startedAt = Instant.now();

        UniversalAdmin platform;
        try {
            platform = bootstrapCore(startedAt);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "UniversalAdmin failed to start: a critical core component could not be "
                            + "initialized. The plugin will be disabled.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        moduleManager = new ModuleManager(platform);
        registerBuiltInModules(moduleManager, platform.settings());
        moduleManager.loadAll();

        // Every built-in module registers its own Migration(s) during its
        // onLoad (see adding-module.md step 3) - never onEnable, precisely so
        // this second runPending() call, sitting *between* loadAll() and
        // enableAll(), has already applied every module-owned table
        // (player_profiles, punishments, server_maintenance_state,
        // whitelist_entries, vanish_state, ...) before any module's onEnable
        // runs. That ordering matters: several modules construct a service in
        // onEnable that reads its own table immediately, including
        // asynchronously (e.g. ServerModule's DefaultMaintenanceService) - if
        // migrations were applied only after every module finished enabling,
        // that first read would race the table's own creation and lose.
        // MigrationRunner.runPending() is idempotent (see
        // MigrationRunnerTest), so this only ever applies what's newly
        // registered, never re-applies anything bootstrapCore's earlier call
        // already handled. Still synchronous/main-thread, still before
        // players can join, same justification as the first call.
        storage.migrations().runPending();

        moduleManager.enableAll();

        syncPermissions(platform.permissions());
        registerCommand(platform);
        getServer().getPluginManager().registerEvents(new StartupSummaryListener(platform), this);

        startTelemetry(platform);
        logStartupSummary(platform);
    }

    @Override
    public void onDisable() {
        if (telemetry != null) {
            telemetry.close();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (storage != null) {
            storage.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
    }

    /**
     * Builds every critical core component and the {@link UniversalAdmin}
     * composition root. "Critical" here means: nothing built-in or external
     * can function without it, so a failure here is a whole-plugin failure,
     * not something to isolate per-module. See docs/architecture/modules.md.
     */
    private UniversalAdmin bootstrapCore(Instant startedAt) {
        saveDefaultConfig();
        saveResource("lang/en_US.yml", false);
        saveResource("lang/de_DE.yml", false);

        // config-version migration runs before anything reads the file, so
        // an old user config.yml is upgraded in place rather than silently
        // reinterpreted (or overwritten) under the current schema. Reused by
        // /admin reload - see ReloadConfigAction.
        ConfigMigrationRunner migrationRunner = new ConfigMigrationRunner(getLogger());
        reloadConfig();
        if (migrationRunner.run(getConfig())) {
            saveConfig();
        }

        SettingRegistry settingRegistry = new SettingRegistry();
        CoreSettings.registerAll(settingRegistry);
        SettingsService settingsService = new YamlSettingsService(settingRegistry, this::getConfig, getLogger());

        scheduler = new PaperTaskScheduler(this);
        DatabaseConfig databaseConfig = CoreSettings.readDatabaseConfig(settingsService);
        storage = new StorageService(databaseConfig, getDataFolder().toPath(), getLogger());
        storage.migrations().register(new AuditSchemaMigration());
        storage.migrations().register(new AuditSchemaMigrationV2());

        ServiceRegistry services = new ServiceRegistry();
        ActionRegistry actions = new ActionRegistry();
        GuiRegistry guiPages = new GuiRegistry();
        GuiSessionManager guiSessions = new GuiSessionManager();
        GuiFramework guiFramework = new GuiFramework(guiSessions, new MaterialIconProvider(getLogger()));
        // The one GuiListener for the whole plugin - see docs/development/gui-framework.md.
        getServer().getPluginManager().registerEvents(new GuiListener(guiSessions), this);
        PermissionRegistry permissions = new PermissionRegistry();
        ModuleRegistry moduleRegistry = new ModuleRegistry();
        AuditService auditService =
                new DefaultAuditService(new JdbcAuditEventRepository(storage.dataSource(), scheduler), settingsService);
        ActionExecutor actionExecutor = new ActionExecutor(actions, auditService, getLogger());
        MessageService messages = new YamlLocaleMessageService(
                getDataFolder().toPath().resolve("lang"), settingsService, getLogger());
        NotificationService notifications = new InGameNotificationService();

        UniversalAdmin platform = new UniversalAdmin(
                this, getPluginMeta().getVersion(), startedAt, getLogger(), settingRegistry, settingsService,
                scheduler, storage, services, actions, actionExecutor, guiPages, guiFramework, permissions,
                moduleRegistry, auditService, messages, notifications);

        // First of two runPending() calls (see the second one in onEnable(),
        // after moduleManager.enableAll()): this one only covers the two
        // core audit migrations registered just above, since no module has
        // registered its own migrations yet at this point in bootstrap.
        // Migrations run synchronously here, before any module is considered
        // loaded and before players can join - one of the two places in the
        // plugin deliberately allowed to block the main thread on IO. See
        // docs/architecture/threading.md. A migration failure is critical:
        // no module can safely trust the schema, so it must abort startup
        // (this method is called from the critical bootstrap try/catch).
        storage.migrations().runPending();

        platform.permissions().register(new PermissionDefinition(
                PermissionNode.core("reload"), "Reload UniversalAdmin's configuration",
                dev.universaladmin.permission.PermissionDefault.OP));
        Supplier<FileConfiguration> configReloader = () -> {
            reloadConfig();
            return getConfig();
        };
        actions.register(ActionDefinition.builder(new ReloadConfigAction(
                        configReloader, this::saveConfig, migrationRunner, settingsService, scheduler))
                .permission(PermissionNode.core("reload"))
                .build());

        platform.permissions().register(new PermissionDefinition(
                PermissionNode.core("menu.open"), "Open the /admin main menu",
                dev.universaladmin.permission.PermissionDefault.OP));
        registerMainMenu(platform, guiFramework, messages, guiPages, moduleRegistry);

        return platform;
    }

    /**
     * Registers the main menu and one {@link PlaceholderGuiPage} per
     * built-in module that doesn't yet have a real GUI - see
     * docs/development/gui-framework.md. A module with a real GUI registers
     * its own page under the same {@link GuiPageId} in its own {@code
     * onEnable} instead (which runs after this method, during {@code
     * moduleManager.enableAll()}) - {@link GuiRegistry} allows exactly one
     * owner per id, so this method must not also register a placeholder for
     * that id. No placeholder for {@code players.home}: {@code
     * PlayersModule} registers its own {@code PlayerBrowserHomePage} there
     * (same precedent as {@code audit-log.home} below). No placeholder for
     * {@code moderation.home} either: {@code ModerationModule} registers its
     * own {@code ModerationHomePage} there. No placeholder for {@code
     * server.home} either: {@code ServerModule} registers its own {@code
     * ServerHomePage} there. No placeholder for {@code worlds.home} either:
     * {@code WorldsModule} registers its own {@code WorldsHomePage} there.
     * No placeholder for {@code whitelist.home} either: {@code
     * WhitelistModule} registers its own {@code WhitelistHomePage} there.
     * No placeholder for {@code performance.home} either: {@code
     * PerformanceModule} registers its own {@code PerformanceHomePage} there.
     */
    private void registerMainMenu(
            UniversalAdmin platform, GuiFramework guiFramework, MessageService messages, GuiRegistry guiPages,
            ModuleRegistry moduleRegistry) {
        // No placeholder for audit-log.home: AuditLogModule registers its own
        // AuditLogListPage under that id in onEnable (see docs/development/adding-module.md
        // step 10) - GuiRegistry allows exactly one owner per id.
        guiPages.register(new PlaceholderGuiPage(GuiPageId.core("settings.home"), guiFramework, messages, "Settings"));
        guiPages.register(new MainMenuPage(guiFramework, messages, moduleRegistry, guiPages));
    }

    private void registerBuiltInModules(ModuleManager moduleManager, SettingsService settings) {
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_PLAYERS_ENABLED, PlayersModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_MODERATION_ENABLED, ModerationModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_SERVER_ENABLED, ServerModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_WORLDS_ENABLED, WorldsModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_WHITELIST_ENABLED, WhitelistModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_PERFORMANCE_ENABLED, PerformanceModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_AUDIT_ENABLED, AuditLogModule::new);
        registerIfEnabled(moduleManager, settings, CoreSettings.MODULE_SETTINGS_ENABLED, SettingsModule::new);
    }

    private void registerIfEnabled(
            ModuleManager moduleManager, SettingsService settings, SettingKey<Boolean> toggle, Supplier<Module> factory) {
        if (settings.get(toggle)) {
            moduleManager.register(factory.get());
        } else {
            getLogger().info(() -> "Module disabled via config (" + toggle.configPath() + "): skipping.");
        }
    }

    private void syncPermissions(PermissionRegistry permissions) {
        for (PermissionDefinition definition : permissions.all()) {
            var bukkitPermission = new org.bukkit.permissions.Permission(
                    definition.node().value(), definition.description(), map(definition.defaultValue()));
            getServer().getPluginManager().addPermission(bukkitPermission);
        }
    }

    private void registerCommand(UniversalAdmin platform) {
        var command = getCommand("admin");
        if (command != null) {
            command.setExecutor(new UniversalAdminCommand(platform));
        }
    }

    /**
     * Starts the anonymous usage statistics after a successful enable - see
     * {@link TelemetryBootstrap} for the three possible outcomes (off, no
     * endpoint configured, actually sending) and docs/user/telemetry.md for
     * what a heartbeat contains.
     *
     * <p>Wrapped in its own try/catch on top of {@code TelemetryBootstrap}'s
     * own guarantees: usage statistics are the least important thing this
     * plugin does, and must never be able to affect a server that has
     * otherwise started fine.
     */
    private void startTelemetry(UniversalAdmin platform) {
        try {
            TelemetryEnvironment environment = new TelemetryEnvironment(
                    getPluginMeta().getVersion(),
                    getServer().getMinecraftVersion(),
                    TelemetryEnvironment.currentJavaMajorVersion());
            telemetry = TelemetryBootstrap.start(
                    platform.settings(),
                    platform.scheduler(),
                    getDataFolder().toPath(),
                    environment,
                    // Read on the main thread by TelemetryService - counts only,
                    // never player identities.
                    () -> new PlayerCounts(getServer().getOnlinePlayers().size(), getServer().getMaxPlayers()),
                    RandomGenerator.getDefault(),
                    getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING,
                    "Anonymous usage statistics could not be started. The server is unaffected; "
                            + "set telemetry.enabled: false in config.yml to stop trying.", e);
        }
    }

    private void logStartupSummary(UniversalAdmin platform) {
        var status = platform.status();
        getLogger().info(platform.messages().get(MessageKey.of("plugin.enabled")));
        getLogger().info(() -> "Active modules (" + status.activeModules().size() + "): " + status.activeModules());
        if (!status.failedModules().isEmpty()) {
            getLogger().warning(() -> "Failed modules: " + status.failedModules());
        }
    }

    private static PermissionDefault map(dev.universaladmin.permission.PermissionDefault value) {
        return switch (value) {
            case TRUE -> PermissionDefault.TRUE;
            case FALSE -> PermissionDefault.FALSE;
            case OP -> PermissionDefault.OP;
            case NOT_OP -> PermissionDefault.NOT_OP;
        };
    }
}
