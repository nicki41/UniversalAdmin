package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.PlayerMenuRegistry;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.PlayerService;
import dev.universaladmin.modules.players.inventory.LiveInventoryService;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;

/**
 * The dependencies every Players GUI page needs, bundled into one
 * constructor parameter instead of six repeated across ~10 page classes -
 * the same reasoning {@link GuiFramework} itself already applies to
 * sessions+icons. Not a substitute for the {@code UniversalAdmin platform}
 * anti-pattern ADR-0004 rejects: this is scoped to exactly what the Players
 * GUI needs, not the whole platform. {@link #playerMenu} is how other modules
 * and extensions add buttons to {@link PlayerProfilePage} without the Players
 * module importing them.
 */
record PlayerGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        PlayerService playerService,
        ActionExecutor actionExecutor,
        SettingsService settings,
        PlayerMenuRegistry playerMenu,
        LiveInventoryService liveInventory,
        NetworkService network) {
}
