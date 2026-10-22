package dev.universaladmin.modules.server.command;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.MaintenanceScope;
import dev.universaladmin.modules.server.MaintenanceService;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.ServerDashboardService;
import dev.universaladmin.modules.server.ServerDashboardSnapshot;
import dev.universaladmin.modules.server.ServerPermissions;
import dev.universaladmin.modules.server.action.BroadcastTitleInput;
import dev.universaladmin.modules.server.action.EnableMaintenanceInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import java.util.ArrayList;
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
     * {@code /admin maintenance [network] <on|off|status|allow|deny|list> ...} -
     * {@code on [kick] [reason]} also removes everyone without access when
     * {@code kick} is given. With {@code network} first, the whole network's
     * maintenance instead of this server's (network mode only).
     */
    static final class Maintenance extends BaseAdminCommand {

        private static final String NETWORK = "network";
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
            boolean network = invocation.arg(0).map(NETWORK::equalsIgnoreCase).orElse(false);
            if (network && !maintenance.networkAvailable()) {
                invocation.reply("server.action.network-required");
                return;
            }
            MaintenanceScope scope = network ? MaintenanceScope.NETWORK : MaintenanceScope.SERVER;
            int first = network ? 1 : 0;
            String sub = invocation.arg(first).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("status");
            switch (sub) {
                case "on", "enable" -> {
                    boolean kick = invocation.arg(first + 1).map(arg -> arg.equalsIgnoreCase("kick")).orElse(false);
                    String reason = invocation.joinArgs(first + (kick ? 2 : 1)).trim();
                    invocation.run(network ? ServerActionIds.NETWORK_MAINTENANCE_ENABLE : ServerActionIds.MAINTENANCE_ENABLE,
                            new EnableMaintenanceInput(reason.isEmpty() ? null : reason, null, kick));
                }
                case "off", "disable" -> invocation.run(
                        network ? ServerActionIds.NETWORK_MAINTENANCE_DISABLE : ServerActionIds.MAINTENANCE_DISABLE, null);
                case "status" -> status(invocation, network);
                case "list" -> invocation.reply(network ? "commands.maintenance.network-allowed" : "commands.maintenance.allowed",
                        maintenance.current(scope).allowedPlayers().isEmpty()
                                ? invocation.messages().get(MessageKey.of("common.none"))
                                : String.join(", ", new TreeSet<>(maintenance.current(scope).allowedPlayers())));
                case "allow", "deny" -> changeAllowed(invocation, scope, first, sub.equals("allow"));
                default -> invocation.usage(this);
            }
        }

        /** This server's state, plus the network's in network mode; {@code network status} shows only the network's. */
        private void status(CommandInvocation invocation, boolean networkOnly) {
            if (!networkOnly) {
                reportStatus(invocation, maintenance.current(MaintenanceScope.SERVER), "commands.maintenance.status");
            }
            if (maintenance.networkAvailable()) {
                reportStatus(invocation, maintenance.current(MaintenanceScope.NETWORK), "commands.maintenance.network-status");
            }
        }

        private static void reportStatus(CommandInvocation invocation, MaintenanceState state, String keyPrefix) {
            invocation.reply(keyPrefix + (state.enabled() ? "-on" : "-off"),
                    state.reason() == null ? "-" : state.reason(), state.allowedPlayers().size());
        }

        private void changeAllowed(CommandInvocation invocation, MaintenanceScope scope, int first, boolean allow) {
            if (invocation.argCount() < first + 2) {
                invocation.usage(this);
                return;
            }
            String name = invocation.args().get(first + 1);
            Set<String> allowed = new LinkedHashSet<>(maintenance.current(scope).allowedPlayers());
            boolean changed = allow ? allowed.add(name) : allowed.removeIf(existing -> existing.equalsIgnoreCase(name));
            if (!changed) {
                invocation.reply(allow ? "commands.maintenance.already-allowed" : "commands.maintenance.not-allowed", name);
                return;
            }
            ActionId action = scope == MaintenanceScope.NETWORK
                    ? ServerActionIds.NETWORK_MAINTENANCE_SET_ALLOWED_PLAYERS : ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS;
            invocation.run(action, Set.copyOf(allowed),
                    MessageKey.of(allow ? "commands.maintenance.allow-added" : "commands.maintenance.allow-removed"), name);
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            boolean network = invocation.argCount() > 1 && invocation.args().get(0).equalsIgnoreCase(NETWORK);
            int first = network ? 1 : 0;
            int position = invocation.argCount() - first;
            if (position <= 1) {
                if (network || !maintenance.networkAvailable()) {
                    return SUBCOMMANDS;
                }
                List<String> withNetwork = new ArrayList<>(SUBCOMMANDS);
                withNetwork.add(NETWORK);
                return withNetwork;
            }
            String sub = invocation.args().get(first).toLowerCase(Locale.ROOT);
            MaintenanceScope scope = network ? MaintenanceScope.NETWORK : MaintenanceScope.SERVER;
            if (position == 2 && sub.equals("allow")) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            if (position == 2 && sub.equals("deny")) {
                return List.copyOf(maintenance.current(scope).allowedPlayers());
            }
            if (position == 2 && sub.equals("on")) {
                return List.of("kick");
            }
            return List.of();
        }
    }
}
