package dev.universaladmin.settings;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.config.ConfigMigrationRunner;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Backs {@code /admin reload} (see {@link dev.universaladmin.command.UniversalAdminCommand}).
 * Never touches Bukkit's global reload - this only re-reads UniversalAdmin's
 * own {@code config.yml}, migrates it if needed, re-resolves settings, and
 * re-reads the server owner's {@code lang/*.yml} overrides.
 * See docs/architecture/threading.md for why this runs off the main thread
 * even though the migration-at-startup equivalent is allowed to block it:
 * this is not a one-time startup gate anything else waits on.
 */
public final class ReloadConfigAction implements Action<Void, ConfigReloadResult> {

    public static final ActionId ID = ActionId.core("config.reload");

    private final Supplier<FileConfiguration> configReloader;
    private final Runnable configSaver;
    private final ConfigMigrationRunner migrationRunner;
    private final SettingsService settingsService;
    private final Runnable messagesReloader;
    private final TaskScheduler scheduler;

    public ReloadConfigAction(
            Supplier<FileConfiguration> configReloader,
            Runnable configSaver,
            ConfigMigrationRunner migrationRunner,
            SettingsService settingsService,
            Runnable messagesReloader,
            TaskScheduler scheduler) {
        this.configReloader = configReloader;
        this.configSaver = configSaver;
        this.migrationRunner = migrationRunner;
        this.settingsService = settingsService;
        this.messagesReloader = messagesReloader;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<ConfigReloadResult>> execute(ActionContext context, Void input) {
        return scheduler.supplyAsync(() -> {
            try {
                FileConfiguration config = configReloader.get();
                if (migrationRunner.run(config)) {
                    configSaver.run();
                }
                ConfigReloadResult result = settingsService.reload();
                messagesReloader.run();
                return ActionResult.<ConfigReloadResult>success(result);
            } catch (RuntimeException e) {
                return ActionResult.<ConfigReloadResult>failure(
                        ActionResult.FailureReason.INTERNAL_ERROR, "Failed to reload configuration: " + e.getMessage());
            }
        });
    }
}
