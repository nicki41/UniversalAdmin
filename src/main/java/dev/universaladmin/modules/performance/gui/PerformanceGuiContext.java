package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.performance.PerformanceSamplingService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;

/** The dependencies every Performance GUI page needs, bundled into one constructor parameter - same reasoning as {@code ServerGuiContext}. */
public record PerformanceGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        SettingsService settings,
        PerformanceSamplingService samplingService) {
}
