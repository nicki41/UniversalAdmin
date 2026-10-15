package dev.universaladmin.modules.reports.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.modules.reports.ReportService.UpdateResult;
import dev.universaladmin.modules.reports.ReportStatus;
import dev.universaladmin.modules.reports.ReportsSettings;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.concurrent.CompletableFuture;

/** Claims, resolves or rejects a report - one action class, three registered ids. */
public final class UpdateReportAction implements Action<UpdateReportAction.Input, Report> {

    /** What the action does to the report. */
    public enum Kind {
        CLAIM(ReportActionIds.CLAIM),
        RESOLVE(ReportActionIds.RESOLVE),
        REJECT(ReportActionIds.REJECT);

        final ActionId id;

        Kind(ActionId id) {
            this.id = id;
        }
    }

    /** @param note optional staff note stored with a resolve/reject (ignored for a claim) */
    public record Input(long reportId, String note) {
    }

    private final Kind kind;
    private final ReportService reports;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final MessageService messages;
    private final TaskScheduler scheduler;

    public UpdateReportAction(
            Kind kind, ReportService reports, SettingsService settings, NotificationService notifications,
            MessageService messages, TaskScheduler scheduler) {
        this.kind = kind;
        this.reports = reports;
        this.settings = settings;
        this.notifications = notifications;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return kind.id;
    }

    @Override
    public CompletableFuture<ActionResult<Report>> execute(ActionContext context, Input input) {
        String staffName = context.actor().displayName();
        String note = input.note() == null || input.note().isBlank() ? null : input.note().trim();
        CompletableFuture<UpdateResult> update = switch (kind) {
            case CLAIM -> reports.claim(input.reportId(), context.actor().playerId(), staffName);
            case RESOLVE -> reports.close(input.reportId(), ReportStatus.RESOLVED, context.actor().playerId(), staffName, note);
            case REJECT -> reports.close(input.reportId(), ReportStatus.REJECTED, context.actor().playerId(), staffName, note);
        };
        return update.thenApply(result -> switch (result) {
            case UpdateResult.Updated updated -> {
                if (kind != Kind.CLAIM) {
                    notifyReporter(updated.report());
                }
                yield ActionResult.success(updated.report());
            }
            case UpdateResult.NotFound notFound -> ActionResult.failure(
                    FailureReason.NOT_FOUND, MessageKey.of("reports.action.not-found"), String.valueOf(input.reportId()));
            case UpdateResult.AlreadyClosed closed -> ActionResult.failure(
                    FailureReason.CONFLICT, MessageKey.of("reports.action.already-closed"), String.valueOf(input.reportId()));
        });
    }

    private void notifyReporter(Report report) {
        if (!settings.get(ReportsSettings.NOTIFY_REPORTER)) {
            return;
        }
        String key = report.status() == ReportStatus.RESOLVED ? "reports.notify.reporter-resolved" : "reports.notify.reporter-rejected";
        String text = messages.get(MessageKey.of(key), report.targetName());
        scheduler.runOnMainThread(() -> notifications.notifyPlayer(report.reporterId(), Notification.info(text)));
    }
}
