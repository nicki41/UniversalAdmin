package dev.universaladmin.modules.reports.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
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
    private final NetworkService network;

    public TeleportToReportAction(ReportService reports, TaskScheduler scheduler, NetworkService network) {
        this.reports = reports;
        this.scheduler = scheduler;
        this.network = network;
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
        String id = Long.toString(report.id());
        if (toTarget) {
            Player target = Bukkit.getPlayer(report.targetId());
            if (target != null) {
                staff.teleportAsync(target.getLocation());
                return ActionResult.success(null, MessageKey.of("reports.teleported-target"), id, report.targetName(), where(target.getLocation()));
            }
            // Network mode: the reported player may be on another server.
            Optional<NetworkPlayer> elsewhere = network.player(report.targetId());
            if (elsewhere.isEmpty()) {
                return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-not-online"), report.targetName());
            }
            network.sendTo(staff, elsewhere.get().server(), new NetworkService.ArrivalTarget.ToPlayer(report.targetId()));
            return ActionResult.success(null, MessageKey.of("reports.teleported-remote"), id, report.targetName(), elsewhere.get().server());
        }
        if (!report.hasLocation()) {
            return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("reports.action.no-location"));
        }
        if (report.server() != null && network.enabled() && !report.server().equalsIgnoreCase(network.serverName())) {
            network.sendTo(staff, report.server(), new NetworkService.ArrivalTarget.ToLocation(report.world(), report.x(), report.y(), report.z()));
            return ActionResult.success(null, MessageKey.of("reports.teleported-remote"), id, report.targetName(), report.server());
        }
        World world = Bukkit.getWorld(report.world());
        if (world == null) {
            return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("reports.action.no-location"));
        }
        Location destination = new Location(world, report.x(), report.y(), report.z());
        staff.teleportAsync(destination);
        return ActionResult.success(null, MessageKey.of("reports.teleported-location"), id, report.targetName(), where(destination));
    }

    private static String where(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }
}
