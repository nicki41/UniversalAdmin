package dev.universaladmin.modules.server.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.ServerDashboardService;
import dev.universaladmin.modules.server.ServerLifecycleService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;

/** The dependencies every Server GUI page needs, bundled into one constructor parameter - same reasoning as {@code ModerationGuiContext}. */
public record ServerGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        SettingsService settings,
        ServerDashboardService dashboardService,
        MaintenanceService maintenanceService,
        ServerLifecycleService lifecycleService) {
}
