package dev.universaladmin.modules.network.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;

/** What every Network GUI page needs, in one parameter - see {@code PlayerGuiContext} for the same idea. */
public record NetworkGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        NetworkService network,
        ServiceRegistry services) {
}
