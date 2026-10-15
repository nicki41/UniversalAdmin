package dev.universaladmin.modules.server.command;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.ServerDashboardService;
import dev.universaladmin.modules.server.ServerDashboardSnapshot;
import dev.universaladmin.modules.server.ServerPermissions;
import dev.universaladmin.modules.server.action.BroadcastTitleInput;
import dev.universaladmin.modules.server.action.EnableMaintenanceInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Every {@code /admin} subcommand of the Server module. */
public final class ServerCommands {

    private static final long MEGABYTE = 1024L * 1024L;

    private ServerCommands() {
    }

    public static List<AdminCommand> all(ServerDashboardService dashboard, MaintenanceService maintenance) {
        return List.of(new Broadcast(), new ServerControl(dashboard), new Maintenance(maintenance));
    }

    /** {@code /admin broadcast <message>} - MiniMessage formatting allowed. */
    static final class Broadcast extends BaseAdminCommand {

        Broadcast() {
            super("broadcast", ServerPermissions.BROADCAST, MessageKey.of("commands.broadcast.usage"),
                    MessageKey.of("commands.broadcast.description"));
            aliases("bc", "announce");
            shortcuts("broadcast", "bc");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.<String, Object>run(ServerActionIds.BROADCAST_MESSAGE, invocation.joinArgs(0), ignored -> {
                if (invocation.player().isEmpty()) {
                    invocation.reply("command.done");
                }
            });
        }
    }

    /**
     * {@code /admin server <status|title|actionbar|shutdown|restart|cancel>} -
     * the dashboard as text, plus the console's path to every server action.
     */
    static final class ServerControl extends BaseAdminCommand {

        private static final List<String> SUBCOMMANDS = List.of("status", "title", "actionbar", "shutdown", "restart", "cancel", "broadcast");

        private final ServerDashboardService dashboard;

        ServerControl(ServerDashboardService dashboard) {
            super("server", ServerPermissions.VIEW, MessageKey.of("commands.server.usage"), MessageKey.of("commands.server.description"));
            this.dashboard = dashboard;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("status");
            switch (sub) {
                case "status" -> status(invocation);
                case "broadcast" -> {
                    if (invocation.argCount() < 2) {
                        invocation.usage(this);
                        return;
                    }
                    invocation.run(ServerActionIds.BROADCAST_MESSAGE, invocation.joinArgs(1));
                }
                case "title" -> title(invocation);
                case "actionbar" -> {
                    if (invocation.argCount() < 2) {
                        invocation.usage(this);
                        return;
                    }
                    invocation.run(ServerActionIds.BROADCAST_ACTIONBAR, invocation.joinArgs(1));
                }
                case "shutdown" -> invocation.run(ServerActionIds.SHUTDOWN, emptyToNull(invocation.joinArgs(1)));
                case "restart" -> invocation.run(ServerActionIds.RESTART, emptyToNull(invocation.joinArgs(1)));
                case "cancel" -> cancel(invocation);
                default -> invocation.usage(this);
            }
        }

        private void status(CommandInvocation invocation) {
            ServerDashboardSnapshot snapshot = dashboard.snapshot();
            invocation.line("commands.server.status.header");
            invocation.line("commands.server.status.version", snapshot.pluginVersion(), snapshot.paperVersion(),
                    snapshot.minecraftVersion(), snapshot.javaVersion());
            invocation.line("commands.server.status.uptime", DurationParser.format(snapshot.uptime()));
            invocation.line("commands.server.status.players", snapshot.onlinePlayers(), snapshot.maxPlayers());
            invocation.line("commands.server.status.memory", snapshot.usedMemoryBytes() / MEGABYTE, snapshot.maxMemoryBytes() / MEGABYTE);
            invocation.line("commands.server.status.cpu", snapshot.availableProcessors(),
                    snapshot.cpuLoadPercent().isPresent() ? "%.1f%%".formatted(snapshot.cpuLoadPercent().getAsDouble()) : "n/a");
            invocation.line("commands.server.status.worlds", snapshot.worldCount());
            invocation.line("commands.server.status.database", String.valueOf(snapshot.databaseStatus()));
        }

        /** {@code title <title> | <subtitle>} - a single {@code |} separates title and subtitle. */
        private void title(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String text = invocation.joinArgs(1);
            int separator = text.indexOf('|');
            String title = (separator < 0 ? text : text.substring(0, separator)).trim();
            String subtitle = separator < 0 ? "" : text.substring(separator + 1).trim();
            invocation.run(ServerActionIds.BROADCAST_TITLE, new BroadcastTitleInput(title, subtitle));
        }

        /** Cancels whichever of a pending shutdown/restart is actually active. */
        private void cancel(CommandInvocation invocation) {
            invocation.<Void, Object>execute(ServerActionIds.CANCEL_SHUTDOWN, null).thenCompose(result ->
                    result instanceof ActionResult.Success<Object>
                            ? java.util.concurrent.CompletableFuture.completedFuture(result)
                            : invocation.<Void, Object>execute(ServerActionIds.CANCEL_RESTART, null))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            invocation.reply("command.error.internal");
                        } else {
                            invocation.report(result);
                        }
                    });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? SUBCOMMANDS : List.of();
        }

        private static String emptyToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }

    /**
     * {@code /admin maintenance <on|off|status|allow|deny|list> ...} -
     * {@code on [kick] [reason]} also kicks everyone without bypass when
     * {@code kick} is given.
     */
    static final class Maintenance extends BaseAdminCommand {

        private static final List<String> SUBCOMMANDS = List.of("on", "off", "status", "allow", "deny", "list");

        private final MaintenanceService maintenance;

        Maintenance(MaintenanceService maintenance) {
            super("maintenance", ServerPermissions.MAINTENANCE, MessageKey.of("commands.maintenance.usage"),
                    MessageKey.of("commands.maintenance.description"));
            this.maintenance = maintenance;
            aliases("mm");
            shortcuts("maintenance");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("status");
            switch (sub) {
                case "on", "enable" -> {
                    boolean kick = invocation.arg(1).map(arg -> arg.equalsIgnoreCase("kick")).orElse(false);
                    String reason = invocation.joinArgs(kick ? 2 : 1).trim();
                    invocation.run(ServerActionIds.MAINTENANCE_ENABLE,
                            new EnableMaintenanceInput(reason.isEmpty() ? null : reason, null, kick));
                }
                case "off", "disable" -> invocation.run(ServerActionIds.MAINTENANCE_DISABLE, null);
                case "status" -> status(invocation);
                case "list" -> invocation.reply("commands.maintenance.allowed", maintenance.current().allowedPlayers().isEmpty()
                        ? invocation.messages().get(MessageKey.of("common.none"))
                        : String.join(", ", new TreeSet<>(maintenance.current().allowedPlayers())));
                case "allow", "deny" -> changeAllowed(invocation, sub.equals("allow"));
                default -> invocation.usage(this);
            }
        }

        private void status(CommandInvocation invocation) {
            MaintenanceState state = maintenance.current();
            invocation.reply(state.enabled() ? "commands.maintenance.status-on" : "commands.maintenance.status-off",
                    state.reason() == null ? "-" : state.reason(), state.allowedPlayers().size());
        }

        private void changeAllowed(CommandInvocation invocation, boolean allow) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String name = invocation.args().get(1);
            Set<String> allowed = new LinkedHashSet<>(maintenance.current().allowedPlayers());
            boolean changed = allow ? allowed.add(name) : allowed.removeIf(existing -> existing.equalsIgnoreCase(name));
            if (!changed) {
                invocation.reply(allow ? "commands.maintenance.already-allowed" : "commands.maintenance.not-allowed", name);
                return;
            }
            invocation.run(ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS, Set.copyOf(allowed),
                    MessageKey.of(allow ? "commands.maintenance.allow-added" : "commands.maintenance.allow-removed"), name);
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return SUBCOMMANDS;
            }
            String sub = invocation.args().get(0).toLowerCase(Locale.ROOT);
            if (invocation.argCount() == 2 && sub.equals("allow")) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            if (invocation.argCount() == 2 && sub.equals("deny")) {
                return List.copyOf(maintenance.current().allowedPlayers());
            }
            if (invocation.argCount() == 2 && sub.equals("on")) {
                return List.of("kick");
            }
            return List.of();
        }
    }
}
