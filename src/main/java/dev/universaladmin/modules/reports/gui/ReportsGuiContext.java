package dev.universaladmin.modules.reports.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.PlayerMenuRegistry;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** What every Reports page needs. */
public record ReportsGuiContext(
        GuiFramework framework,
        MessageService messages,
        TaskScheduler scheduler,
        ActionExecutor actionExecutor,
        ReportService reports,
        PlayerMenuRegistry playerMenu) {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    static String formatTime(Instant instant) {
        return DATE_TIME.format(instant);
    }

    /** "5m", "3h 2m", ... - how long ago {@code report} was filed. */
    static String age(Report report) {
        Duration age = Duration.between(report.createdAt(), Instant.now());
        return dev.universaladmin.core.time.DurationParser.format(age.toSeconds() >= 3600
                ? age.minusSeconds(age.toSeconds() % 60)
                : age);
    }
}
