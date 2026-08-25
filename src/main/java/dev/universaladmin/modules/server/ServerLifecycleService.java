package dev.universaladmin.modules.server;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.ModuleResources;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.settings.SettingsService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schedules and runs the shutdown/restart confirmation countdown - see
 * {@code ServerSettings.COUNTDOWN_*}. {@link dev.universaladmin.scheduler.TaskScheduler}
 * has no delayed/periodic scheduling (only next-tick/async-and-back), so
 * actual ticking goes through Bukkit's own scheduler directly, the same way
 * {@code ModerationModule.scheduleExpirySweep} runs its periodic cleanup -
 * see docs/architecture/threading.md. The resulting {@link BukkitTask} is
 * registered with {@link ModuleResources} so it's cancelled if the module is
 * ever disabled mid-countdown.
 *
 * <p>Only one shutdown/restart countdown may be active at a time; {@link
 * #cancel()} aborts it. "Restart" calls {@link org.bukkit.Server#restart()} -
 * Paper/Spigot's own built-in restart mechanism, not a shell command - which
 * performs a clean shutdown; whether the OS process actually relaunches
 * depends entirely on how the server was started (a looping start script or
 * a process manager that restarts on exit). See docs/user/modules/server.md.
 */
public final class ServerLifecycleService {

    public enum PendingAction {
        NONE,
        SHUTDOWN,
        RESTART
    }

    public record PendingSnapshot(PendingAction action, int remainingSeconds, String reason) {
    }

    private final Plugin plugin;
    private final ModuleResources resources;
    private final NotificationService notifications;
    private final MessageService messages;
    private final SettingsService settings;

    private volatile PendingAction pending = PendingAction.NONE;
    private volatile int remainingSeconds;
    private volatile String pendingReason;
    private volatile BukkitTask activeTask;

    public ServerLifecycleService(
            Plugin plugin, ModuleResources resources, NotificationService notifications, MessageService messages, SettingsService settings) {
        this.plugin = plugin;
        this.resources = resources;
        this.notifications = notifications;
        this.messages = messages;
        this.settings = settings;
    }

    public synchronized boolean scheduleShutdown(String reason) {
        return schedule(PendingAction.SHUTDOWN, reason);
    }

    public synchronized boolean scheduleRestart(String reason) {
        return schedule(PendingAction.RESTART, reason);
    }

    /** Cancels whichever countdown is active. Returns {@code false} if none was pending. */
    public synchronized boolean cancel() {
        if (pending == PendingAction.NONE) {
            return false;
        }
        BukkitTask task = activeTask;
        if (task != null) {
            task.cancel();
        }
        notifications.broadcast(text("server.action.countdown-cancelled", label(pending)));
        reset();
        return true;
    }

    public PendingSnapshot pending() {
        return new PendingSnapshot(pending, remainingSeconds, pendingReason);
    }

    private boolean schedule(PendingAction action, String reason) {
        if (pending != PendingAction.NONE) {
            return false;
        }
        List<Integer> steps = settings.get(ServerSettings.COUNTDOWN_BROADCAST_STEPS).stream()
                .map(String::trim).map(Integer::parseInt).sorted(Comparator.reverseOrder()).toList();
        boolean countdownEnabled = settings.get(ServerSettings.COUNTDOWN_ENABLED);
        int start = countdownEnabled && !steps.isEmpty() ? steps.get(0) : 0;
        Set<Integer> stepSet = Set.copyOf(steps);

        pending = action;
        pendingReason = reason;
        remainingSeconds = start;
        activeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(action, stepSet), 0L, 20L);
        resources.task(activeTask);
        return true;
    }

    private void tick(PendingAction action, Set<Integer> broadcastSteps) {
        if (remainingSeconds <= 0) {
            BukkitTask task = activeTask;
            if (task != null) {
                task.cancel();
            }
            execute(action);
            return;
        }
        if (broadcastSteps.contains(remainingSeconds)) {
            notifications.broadcast(text("server.action.countdown-warning", label(action), remainingSeconds));
        }
        remainingSeconds--;
    }

    private void execute(PendingAction action) {
        notifications.broadcast(text("server.action.executing-now", label(action)));
        reset();
        switch (action) {
            case SHUTDOWN -> Bukkit.shutdown();
            case RESTART -> Bukkit.getServer().restart();
            case NONE -> {
            }
        }
    }

    private void reset() {
        pending = PendingAction.NONE;
        pendingReason = null;
        remainingSeconds = 0;
        activeTask = null;
    }

    private String label(PendingAction action) {
        return messages.get(MessageKey.of(action == PendingAction.RESTART ? "server.action.label-restart" : "server.action.label-shutdown"));
    }

    private Component text(String key, Object... args) {
        return ComponentMessages.render(messages.get(MessageKey.of(key), args));
    }
}
