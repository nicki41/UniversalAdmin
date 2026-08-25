package dev.universaladmin.modules.whitelist.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.whitelist.WhitelistService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;

/** The dependencies every Whitelist GUI page needs, bundled into one constructor parameter - same reasoning as {@code ServerGuiContext}. */
public record WhitelistGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        SettingsService settings,
        WhitelistService whitelistService) {
}
