package dev.universaladmin.modules.reports;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tells a joining staff member how many reports are waiting - only the
 * translation of the join event into a {@link ReportService} lookup, no
 * logic of its own.
 */
public final class ReportReminderListener implements Listener {

    private final ReportService reports;
    private final SettingsService settings;
    private final MessageService messages;
    private final TaskScheduler scheduler;

    public ReportReminderListener(ReportService reports, SettingsService settings, MessageService messages, TaskScheduler scheduler) {
        this.reports = reports;
        this.settings = settings;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!settings.get(ReportsSettings.REMIND_STAFF_ON_JOIN) || !player.hasPermission(ReportsPermissions.VIEW.value())) {
            return;
        }
        reports.openCount().thenAccept(open -> {
            if (open > 0) {
                scheduler.runOnMainThread(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("reports.notify.reminder"), open)));
                    }
                });
            }
        });
    }
}
