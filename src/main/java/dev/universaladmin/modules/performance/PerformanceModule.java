package dev.universaladmin.modules.performance;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.AuditDetails;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.gui.PerformanceEntityByTypePage;
import dev.universaladmin.modules.performance.gui.PerformanceEntityOverviewPage;
import dev.universaladmin.modules.performance.gui.PerformanceGuiContext;
import dev.universaladmin.modules.performance.gui.PerformanceHomePage;
import dev.universaladmin.modules.performance.gui.PerformanceWorldsPage;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Monitoring and diagnostics: a live TPS/MSPT/memory/world/entity dashboard,
 * a short in-memory history, staff alerts on threshold breaches, and a
 * deliberately narrow, confirmed, audited Entity Clear - never a "lag
 * cleaner" that acts on its own. Everything here reads Paper/JVM APIs
 * already in use elsewhere in the codebase ({@code Server#getTPS()}/{@code
 * getAverageTickTime()}, {@code World#getChunkCount()}/{@code getEntities()},
 * {@code Runtime}), sampled on a timer rather than recomputed per GUI
 * render - see {@link PerformanceSamplingService}. See
 * docs/user/modules/performance.md.
 */
public final class PerformanceModule implements Module {

    public static final ModuleId ID = ModuleId.core("performance");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Performance")
            .description("Performance monitoring: TPS/MSPT/memory dashboard, per-world and per-entity-type breakdown, staff alerts, confirmed Entity Clear.")
            .icon(new GuiIcon("comparator", "Performance"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        PerformanceSettings.registerAll(context.platform().settingRegistry());
        PerformancePermissions.registerAll(context.platform().permissions());

        SettingsService settings = context.platform().settings();
        TaskScheduler scheduler = context.platform().scheduler();
        Plugin plugin = context.platform().plugin();

        PerformanceSamplingService samplingService = new PerformanceSamplingService(
                context.platform()::status, context.platform().notifications(), context.platform().messages(),
                settings, context.logger());
        // Sampled once synchronously here (onEnable already runs on the main
        // thread during plugin startup, see Module's javadoc) so the
        // dashboard has real data on the very first open, instead of an
        // empty/zeroed snapshot until the first scheduled tick below fires.
        samplingService.sample();
        context.platform().services().register(PerformanceSamplingService.class, samplingService);

        scheduleSampling(context, plugin, settings, samplingService);
        registerActions(context, scheduler, settings);
        registerGui(context, samplingService);
    }

    private void scheduleSampling(
            ModuleContext context, Plugin plugin, SettingsService settings, PerformanceSamplingService samplingService) {
        long intervalTicks = Math.max(1, settings.get(CoreSettings.PERFORMANCE_REFRESH_INTERVAL).toMillis() / 50L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, samplingService::sample, intervalTicks, intervalTicks);
        context.resources().task(task);
    }

    private void registerActions(ModuleContext context, TaskScheduler scheduler, SettingsService settings) {
        context.platform().actions().register(ActionDefinition.builder(
                        new ClearEntitiesAction(scheduler, settings, context.logger()))
                .permission(PerformancePermissions.ENTITY_CLEAR)
                .module(ID.key().name())
                .validator((actionContext, input) -> input.entityTypes().isEmpty()
                        ? Optional.of(ValidationError.of(
                                ActionResult.FailureReason.VALIDATION, MessageKey.of("performance.action.no-types-selected")))
                        : Optional.empty())
                .auditSummary(input -> "Cleared entities ("
                        + input.entityTypes().stream().map(Enum::name).sorted().collect(Collectors.joining(", "))
                        + ") in " + (input.worldName() == null ? "all worlds" : input.worldName()))
                .auditDetails((input, result) -> AuditDetails.builder()
                        .newValue(result instanceof ActionResult.Success<Integer> success ? "removed=" + success.value() : null)
                        .world(input.worldName())
                        .metadata(Map.of("types", input.entityTypes().stream().map(Enum::name).sorted().toList()))
                        .build())
                .build());
    }

    private void registerGui(ModuleContext context, PerformanceSamplingService samplingService) {
        PerformanceGuiContext guiContext = new PerformanceGuiContext(
                context.platform().guiFramework(), context.platform().messages(), context.platform().scheduler(),
                context.platform().actionExecutor(), context.platform().settings(), samplingService);

        context.platform().guiPages().register(new PerformanceHomePage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceHomePage.ID));
        context.platform().guiPages().register(new PerformanceWorldsPage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceWorldsPage.ID));
        context.platform().guiPages().register(new PerformanceEntityOverviewPage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceEntityOverviewPage.ID));
        context.platform().guiPages().register(new PerformanceEntityByTypePage(guiContext));
        context.resources().closeable(() -> context.platform().guiPages().unregister(PerformanceEntityByTypePage.ID));
    }
}
