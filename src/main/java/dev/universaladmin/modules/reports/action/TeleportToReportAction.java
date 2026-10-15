package dev.universaladmin.modules.reports.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Teleports the staff member handling a report to the reported player (if
 * online) or to where the reporter stood when filing it - the two places a
 * staff member actually wants to look.
 */
public final class TeleportToReportAction implements Action<TeleportToReportAction.Input, Void> {

    /** @param toTarget {@code true} for the reported player, {@code false} for the report's location */
    public record Input(long reportId, boolean toTarget) {
    }

    private final ReportService reports;
    private final TaskScheduler scheduler;

    public TeleportToReportAction(ReportService reports, TaskScheduler scheduler) {
        this.reports = reports;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ReportActionIds.TELEPORT;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, Input input) {
        UUID staffId = context.actor().playerId();
        if (staffId == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-only")));
        }
        return reports.find(input.reportId()).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(ActionResult.<Void>failure(
                        FailureReason.NOT_FOUND, MessageKey.of("reports.action.not-found"), String.valueOf(input.reportId())));
            }
            Report report = found.get();
            return scheduler.supplyOnMainThread(() -> teleport(staffId, report, input.toTarget()));
        });
    }

    private ActionResult<Void> teleport(UUID staffId, Report report, boolean toTarget) {
        Player staff = Bukkit.getPlayer(staffId);
        if (staff == null) {
            return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-only"));
        }
        Location destination;
        if (toTarget) {
            Player target = Bukkit.getPlayer(report.targetId());
            if (target == null) {
                return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-not-online"), report.targetName());
            }
            destination = target.getLocation();
        } else {
            World world = report.hasLocation() ? Bukkit.getWorld(report.world()) : null;
            if (world == null) {
                return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("reports.action.no-location"));
            }
            destination = new Location(world, report.x(), report.y(), report.z());
        }
        staff.teleportAsync(destination);
        String where = destination.getWorld().getName() + " " + destination.getBlockX() + " " + destination.getBlockY() + " " + destination.getBlockZ();
        return ActionResult.success(null, MessageKey.of(toTarget ? "reports.teleported-target" : "reports.teleported-location"),
                Long.toString(report.id()), report.targetName(), where);
    }
}
