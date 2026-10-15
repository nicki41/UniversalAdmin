package dev.universaladmin.modules.reports.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.modules.reports.ReportService.CreateResult;
import dev.universaladmin.modules.reports.ReportsPermissions;
import dev.universaladmin.modules.reports.ReportsSettings;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Files a report ({@code /report}): records where the reporter stood, applies
 * the cooldown/open-report limits ({@link ReportService}), then notifies
 * every online staff member with {@link ReportsPermissions#VIEW} - with a
 * clickable "view" link and, if enabled, a sound.
 */
public final class CreateReportAction implements Action<CreateReportAction.Input, Report> {

    /**
     * @param targetId   the reported player
     * @param targetName their name, for display
     * @param reason     what the reporter wrote
     */
    public record Input(UUID targetId, String targetName, String reason) {
    }

    private final ReportService reports;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final MessageService messages;
    private final TaskScheduler scheduler;

    public CreateReportAction(
            ReportService reports, SettingsService settings, NotificationService notifications,
            MessageService messages, TaskScheduler scheduler) {
        this.reports = reports;
        this.settings = settings;
        this.notifications = notifications;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ReportActionIds.CREATE;
    }

    @Override
    public CompletableFuture<ActionResult<Report>> execute(ActionContext context, Input input) {
        UUID reporterId = context.actor().playerId();
        if (reporterId == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    FailureReason.VALIDATION, MessageKey.of("reports.action.players-only")));
        }
        boolean bypass = context.actor().hasPermission(ReportsPermissions.BYPASS_LIMITS);
        Duration cooldown = bypass ? Duration.ZERO : settings.get(ReportsSettings.COOLDOWN);
        int maxOpen = bypass ? 0 : settings.get(ReportsSettings.MAX_OPEN_PER_PLAYER);
        String reporterName = context.actor().displayName();

        return scheduler.supplyOnMainThread(() -> {
                    Player reporter = Bukkit.getPlayer(reporterId);
                    return reporter == null ? null : reporter.getLocation();
                })
                .thenCompose(location -> reports.create(draft(reporterId, reporterName, input, location), cooldown, maxOpen))
                .thenApply(result -> switch (result) {
                    case CreateResult.Created created -> {
                        notifyStaff(created.report());
                        yield ActionResult.success(created.report(), MessageKey.of("reports.filed"), created.report().targetName());
                    }
                    case CreateResult.Rejected rejected -> rejection(rejected);
                });
    }

    private static Report draft(UUID reporterId, String reporterName, Input input, Location location) {
        return Report.open(reporterId, reporterName, input.targetId(), input.targetName(), input.reason().trim(),
                location == null || location.getWorld() == null ? null : location.getWorld().getName(),
                location == null ? null : location.getX(),
                location == null ? null : location.getY(),
                location == null ? null : location.getZ(),
                Instant.now());
    }

    private ActionResult<Report> rejection(CreateResult.Rejected rejected) {
        return switch (rejected.reason()) {
            case SELF -> ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("reports.action.self"));
            case COOLDOWN -> ActionResult.failure(FailureReason.CONFLICT, MessageKey.of("reports.action.cooldown"),
                    DurationParser.format(rejected.retryAfter().plusSeconds(1)));
            case TOO_MANY_OPEN -> ActionResult.failure(FailureReason.CONFLICT, MessageKey.of("reports.action.too-many"),
                    settings.get(ReportsSettings.MAX_OPEN_PER_PLAYER));
        };
    }

    private void notifyStaff(Report report) {
        String text = messages.get(MessageKey.of("reports.notify.new"),
                report.reporterName(), report.targetName(), report.reason(), String.valueOf(report.id()));
        boolean sound = settings.get(ReportsSettings.NOTIFY_SOUND);
        scheduler.runOnMainThread(() -> {
            notifications.notifyStaff(ReportsPermissions.VIEW, Notification.info(text));
            if (sound) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission(ReportsPermissions.VIEW.value()))
                        .forEach(player -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f));
            }
        });
    }
}
