package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.worlds.WorldInfoService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;

/** The dependencies every Worlds GUI page needs, bundled into one constructor parameter - same reasoning as {@code ServerGuiContext}. */
public record WorldsGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        SettingsService settings,
        WorldInfoService infoService) {
}
