package dev.universaladmin.settings;

import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.DatabaseType;
import java.time.Duration;

/**
 * Every setting owned by the core platform itself (namespace {@code core}) -
 * everything under {@code general}, {@code database}, {@code gui}, {@code audit},
 * {@code modules}, {@code performance}, {@code maintenance}, {@code telemetry},
 * and {@code web} in {@code config.yml}. See docs/user/configuration.md for the full,
 * documented default file and docs/development/settings.md for how a module
 * registers its own settings under its own namespace instead.
 */
public final class CoreSettings {

    private CoreSettings() {
    }

    public static final SettingKey<String> LANGUAGE = SettingKey.of("core", "general.language");

    public static final SettingKey<DatabaseType> DATABASE_TYPE = SettingKey.of("core", "database.type");
    public static final SettingKey<String> DATABASE_FILE = SettingKey.of("core", "database.file");
    public static final SettingKey<String> DATABASE_HOST = SettingKey.of("core", "database.host");
    public static final SettingKey<Integer> DATABASE_PORT = SettingKey.of("core", "database.port");
    public static final SettingKey<String> DATABASE_NAME = SettingKey.of("core", "database.database");
    public static final SettingKey<String> DATABASE_USERNAME = SettingKey.of("core", "database.username");
    public static final SettingKey<String> DATABASE_PASSWORD = SettingKey.of("core", "database.password");
    public static final SettingKey<Boolean> DATABASE_SSL = SettingKey.of("core", "database.ssl");
    public static final SettingKey<Integer> DATABASE_POOL_SIZE = SettingKey.of("core", "database.pool-size");

    public static final SettingKey<Integer> GUI_PAGE_SIZE = SettingKey.of("core", "gui.page-size");
    public static final SettingKey<Boolean> GUI_CONFIRMATIONS = SettingKey.of("core", "gui.confirmations");

    public static final SettingKey<Boolean> AUDIT_ENABLED = SettingKey.of("core", "audit.enabled");
    public static final SettingKey<Integer> AUDIT_RETENTION_DAYS = SettingKey.of("core", "audit.retention-days");

    public static final SettingKey<Boolean> MODULE_PLAYERS_ENABLED = SettingKey.of("core", "modules.players");
    public static final SettingKey<Boolean> MODULE_MODERATION_ENABLED = SettingKey.of("core", "modules.moderation");
    public static final SettingKey<Boolean> MODULE_SERVER_ENABLED = SettingKey.of("core", "modules.server");
    public static final SettingKey<Boolean> MODULE_WORLDS_ENABLED = SettingKey.of("core", "modules.worlds");
    public static final SettingKey<Boolean> MODULE_WHITELIST_ENABLED = SettingKey.of("core", "modules.whitelist");
    public static final SettingKey<Boolean> MODULE_PERFORMANCE_ENABLED = SettingKey.of("core", "modules.performance");
    public static final SettingKey<Boolean> MODULE_AUDIT_ENABLED = SettingKey.of("core", "modules.audit");
    public static final SettingKey<Boolean> MODULE_SETTINGS_ENABLED = SettingKey.of("core", "modules.settings");

    public static final SettingKey<Duration> PERFORMANCE_REFRESH_INTERVAL =
            SettingKey.of("core", "performance.refresh-interval");

    public static final SettingKey<Boolean> MAINTENANCE_ENABLED = SettingKey.of("core", "maintenance.enabled");
    public static final SettingKey<String> MAINTENANCE_KICK_MESSAGE = SettingKey.of("core", "maintenance.kick-message");

    public static final SettingKey<Boolean> TELEMETRY_ENABLED = SettingKey.of("core", "telemetry.enabled");
    public static final SettingKey<String> TELEMETRY_ENDPOINT = SettingKey.of("core", "telemetry.endpoint");
    public static final SettingKey<Duration> TELEMETRY_INTERVAL = SettingKey.of("core", "telemetry.interval");

    /** The official nicki41-telemetry instance - a generic, multi-plugin backend, not UniversalAdmin-specific. See docs/user/telemetry.md. */
    private static final String DEFAULT_TELEMETRY_ENDPOINT = "https://telemetry.0nicki.de/v1/telemetry";

    /** The one switch {@code /admin update} and the background checker both respect - see {@code update.UpdateCheckService}. */
    public static final SettingKey<Boolean> UPDATE_CHECK_ENABLED = SettingKey.of("core", "update.check-for-updates");
    public static final SettingKey<Duration> UPDATE_CHECK_INTERVAL = SettingKey.of("core", "update.check-interval");

    public static final SettingKey<Boolean> WEB_ENABLED = SettingKey.of("core", "web.enabled");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(LANGUAGE, SettingTypes.STRING, "en_US")
                .description("Active locale; any key missing there falls back to en_US, then to a visible marker.")
                .validator(SettingValidators.matches("[a-z]{2}_[A-Z]{2}", "a locale code like en_US or de_DE"))
                .build());

        registerDatabaseSettings(registry);

        registry.register(SettingDefinition.builder(GUI_PAGE_SIZE, SettingTypes.INTEGER, 45)
                .description("Inventory GUI page size; must be a multiple of 9 between 9 and 54.")
                .validator(SettingValidators.multipleOf(9).and(SettingValidators.intRange(9, 54)))
                .build());
        registry.register(SettingDefinition.builder(GUI_CONFIRMATIONS, SettingTypes.BOOLEAN, true)
                .description("Ask for confirmation before destructive GUI actions.")
                .build());

        registry.register(SettingDefinition.builder(AUDIT_ENABLED, SettingTypes.BOOLEAN, true)
                .description("Whether AuditService actually records events.")
                .build());
        registry.register(SettingDefinition.builder(AUDIT_RETENTION_DAYS, SettingTypes.INTEGER, 0)
                .description("How many days of audit history to keep; 0 = unlimited (never deleted).")
                .validator(SettingValidators.intRange(0, 3650))
                .build());

        registerModuleToggle(registry, MODULE_PLAYERS_ENABLED, "Players");
        registerModuleToggle(registry, MODULE_MODERATION_ENABLED, "Moderation");
        registerModuleToggle(registry, MODULE_SERVER_ENABLED, "Server");
        registerModuleToggle(registry, MODULE_WORLDS_ENABLED, "Worlds");
        registerModuleToggle(registry, MODULE_WHITELIST_ENABLED, "Whitelist");
        registerModuleToggle(registry, MODULE_PERFORMANCE_ENABLED, "Performance");
        registerModuleToggle(registry, MODULE_AUDIT_ENABLED, "Audit Log");
        registerModuleToggle(registry, MODULE_SETTINGS_ENABLED, "Settings");

        registry.register(SettingDefinition.builder(PERFORMANCE_REFRESH_INTERVAL, SettingTypes.DURATION, Duration.ofSeconds(5))
                .description("How often the Performance module refreshes its TPS/MSPT snapshot.")
                .validator(SettingValidators.durationRange(Duration.ofSeconds(1), Duration.ofMinutes(10)))
                .requiresRestart(true)
                .build());

        registry.register(SettingDefinition.builder(MAINTENANCE_ENABLED, SettingTypes.BOOLEAN, false)
                .description("Reject new joins with maintenance-kick-message while true.")
                .build());
        registry.register(SettingDefinition.builder(
                        MAINTENANCE_KICK_MESSAGE, SettingTypes.STRING, "<red>Server is currently under maintenance.")
                .description("MiniMessage-formatted kick message used while maintenance.enabled is true.")
                .build());

        registerTelemetrySettings(registry);
        registerUpdateCheckSettings(registry);

        registry.register(SettingDefinition.builder(WEB_ENABLED, SettingTypes.BOOLEAN, false)
                .description("Reserved for the future web app (see docs/architecture/web-future.md); has no effect yet.")
                .build());
    }

    /**
     * Anonymous usage statistics - see docs/user/telemetry.md for exactly what
     * a heartbeat contains and what it never contains.
     *
     * <p>{@code telemetry.enabled} deliberately does <b>not</b> require a
     * restart: it is read again on every heartbeat, so turning statistics off
     * and running {@code /admin reload} stops them immediately. The endpoint
     * and interval shape long-lived objects (an HTTP client and a timer) built
     * once at startup, so those do require a restart.
     */
    private static void registerTelemetrySettings(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(TELEMETRY_ENABLED, SettingTypes.BOOLEAN, true)
                .description("Send anonymous usage statistics (see docs/user/telemetry.md); false sends nothing at all.")
                .build());
        registry.register(SettingDefinition.builder(TELEMETRY_ENDPOINT, SettingTypes.STRING, DEFAULT_TELEMETRY_ENDPOINT)
                .description("Statistics endpoint URL. Defaults to the official nicki41-telemetry instance; "
                        + "set to empty to send nothing anywhere, or point at your own instance.")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(TELEMETRY_INTERVAL, SettingTypes.DURATION, Duration.ofMinutes(30))
                .description("Time between heartbeats; a random extra of up to half this value is added to each one.")
                .validator(SettingValidators.durationRange(Duration.ofMinutes(5), Duration.ofHours(24)))
                .requiresRestart(true)
                .build());
    }

    /**
     * {@code UPDATE_CHECK_ENABLED} is live (checked fresh on every check, an
     * {@code /admin update} works regardless either way) but, like {@code
     * telemetry.enabled}, whether the background timer exists at all is
     * still decided once at startup - see {@code update.UpdateCheckBootstrap}.
     * {@code UPDATE_CHECK_INTERVAL} changes the timer itself, so that one
     * does require a restart, same reasoning as {@code TELEMETRY_INTERVAL}.
     */
    private static void registerUpdateCheckSettings(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(UPDATE_CHECK_ENABLED, SettingTypes.BOOLEAN, true)
                .description("Check GitHub for a newer UniversalAdmin release and notify staff/console; false checks nothing automatically ('/admin update' still works on demand).")
                .build());
        registry.register(SettingDefinition.builder(UPDATE_CHECK_INTERVAL, SettingTypes.DURATION, Duration.ofHours(6))
                .description("Time between automatic update checks; a random extra of up to half this value is added to each one.")
                .validator(SettingValidators.durationRange(Duration.ofHours(1), Duration.ofDays(7)))
                .requiresRestart(true)
                .build());
    }

    private static void registerDatabaseSettings(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(DATABASE_TYPE, SettingTypes.enumOf(DatabaseType.class), DatabaseType.SQLITE)
                .description("sqlite (default) or mysql (the driver also speaks MariaDB's protocol).")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_FILE, SettingTypes.STRING, "data.db")
                .description("SQLite database file name, relative to the plugin data folder.")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_HOST, SettingTypes.STRING, "localhost")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_PORT, SettingTypes.INTEGER, 3306)
                .validator(SettingValidators.intRange(1, 65535))
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_NAME, SettingTypes.STRING, "universaladmin")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_USERNAME, SettingTypes.STRING, "universaladmin")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_PASSWORD, SettingTypes.STRING, "")
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_SSL, SettingTypes.BOOLEAN, true)
                .requiresRestart(true)
                .build());
        registry.register(SettingDefinition.builder(DATABASE_POOL_SIZE, SettingTypes.INTEGER, 10)
                .validator(SettingValidators.intRange(1, 100))
                .requiresRestart(true)
                .build());
    }

    private static void registerModuleToggle(SettingRegistry registry, SettingKey<Boolean> key, String moduleDisplayName) {
        registry.register(SettingDefinition.builder(key, SettingTypes.BOOLEAN, true)
                .description("Enable/disable the " + moduleDisplayName + " module.")
                .requiresRestart(true)
                .build());
    }

    /** Assembles a {@link DatabaseConfig} from the individually-registered {@code database.*} settings. */
    public static DatabaseConfig readDatabaseConfig(SettingsService settings) {
        return new DatabaseConfig(
                settings.get(DATABASE_TYPE),
                settings.get(DATABASE_FILE),
                settings.get(DATABASE_HOST),
                settings.get(DATABASE_PORT),
                settings.get(DATABASE_NAME),
                settings.get(DATABASE_USERNAME),
                settings.get(DATABASE_PASSWORD),
                settings.get(DATABASE_SSL),
                settings.get(DATABASE_POOL_SIZE));
    }
}
