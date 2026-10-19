package dev.universaladmin.modules.network.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.network.NetworkService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /admin network [server]}, {@code find}, {@code send}, {@code connect}
 * and {@code alert} - plus, in the Players module, {@code tp}/{@code tphere}
 * for players on other servers. Frontends only: every move is a
 * {@link NetworkActions} action.
 */
public final class NetworkCommands {

    private NetworkCommands() {
    }

    public static List<AdminCommand> all(NetworkService network) {
        // No goto/bring here: /admin tp and /admin tphere fall back to the
        // network actions when the player is on another server.
        return List.of(new Network(network), new Find(network), new Send(network), new Connect(network), new Alert());
    }

    private static List<String> playerNames(NetworkService network) {
        return network.players().stream().map(NetworkPlayer::name).toList();
    }

    private static List<String> serverNames(NetworkService network) {
        return network.servers().stream().map(NetworkServer::name).toList();
    }

    private static String since(Instant since) {
        return since == null ? "" : DurationParser.format(Duration.between(since, Instant.now()));
    }

    /** {@code /admin network} lists every server; {@code /admin network <server>} its players. */
    static final class Network extends BaseAdminCommand {

        private final NetworkService network;

        Network(NetworkService network) {
            super("network", NetworkPermissions.VIEW, MessageKey.of("commands.network.usage"), MessageKey.of("commands.network.description"));
            this.network = network;
            aliases("net");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() > 0) {
                players(invocation, invocation.args().get(0));
                return;
            }
            List<NetworkServer> servers = network.servers();
            int players = servers.stream().mapToInt(NetworkServer::playerCount).sum();
            invocation.line("network.list.header", Integer.toString(servers.size()), Integer.toString(players));
            for (NetworkServer server : servers) {
                Object here = MessageService.markup(server.name().equalsIgnoreCase(network.serverName())
                        ? invocation.messages().get(MessageKey.of("network.list.you-are-here")) : "");
                if (!server.universalAdmin()) {
                    invocation.line("network.list.server-other", server.name(), Integer.toString(server.playerCount()), here);
                } else if (!server.online()) {
                    invocation.line("network.list.server-offline", server.name(), since(server.lastSeen()), here);
                } else {
                    invocation.line("network.list.server", server.name(), Integer.toString(server.playerCount()),
                            Integer.toString(server.maxPlayers()), String.format(Locale.ROOT, "%.1f", server.tps()), here);
                }
            }
        }

        private void players(CommandInvocation invocation, String serverName) {
            Optional<NetworkServer> server = network.servers().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(serverName)).findFirst();
            if (server.isEmpty()) {
                invocation.reply("network.action.unknown-server", serverName);
                return;
            }
            List<String> names = network.players().stream()
                    .filter(player -> player.server().equalsIgnoreCase(server.get().name()))
                    .map(NetworkPlayer::name)
                    .toList();
            invocation.line("network.list.players-header", server.get().name(), Integer.toString(names.size()));
            if (!names.isEmpty()) {
                invocation.line("network.list.players", String.join(", ", names));
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? serverNames(network) : List.of();
        }
    }

    /** {@code /admin find <player>} - which server someone is on. */
    static final class Find extends BaseAdminCommand {

        private final NetworkService network;

        Find(NetworkService network) {
            super("find", NetworkPermissions.VIEW, MessageKey.of("commands.find.usage"), MessageKey.of("commands.find.description"));
            this.network = network;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            String name = invocation.args().get(0);
            network.player(name).ifPresentOrElse(
                    player -> invocation.reply(player.since() == null ? "network.find.found" : "network.find.found-since",
                            player.name(), player.server(), since(player.since())),
                    () -> invocation.reply("network.action.player-not-found", name));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? playerNames(network) : List.of();
        }
    }

    /** {@code /admin send <player> <server>}. */
    static final class Send extends BaseAdminCommand {

        private final NetworkService network;

        Send(NetworkService network) {
            super("send", NetworkPermissions.SEND, MessageKey.of("commands.send.usage"), MessageKey.of("commands.send.description"));
            this.network = network;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            invocation.run(NetworkActions.SEND, new NetworkActions.SendInput(invocation.args().get(0), invocation.args().get(1)));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return switch (invocation.argCount()) {
                case 0, 1 -> playerNames(network);
                case 2 -> serverNames(network);
                default -> List.of();
            };
        }
    }

    /** {@code /admin connect <server>} - go to another server. */
    static final class Connect extends BaseAdminCommand {

        private final NetworkService network;

        Connect(NetworkService network) {
            super("connect", NetworkPermissions.GOTO, MessageKey.of("commands.connect.usage"), MessageKey.of("commands.connect.description"));
            this.network = network;
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.run(NetworkActions.CONNECT, invocation.args().get(0));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1
                    ? serverNames(network).stream().filter(name -> !name.equalsIgnoreCase(network.serverName())).collect(Collectors.toList())
                    : List.of();
        }
    }

    /** {@code /admin alert <message>} - a broadcast on every server. */
    static final class Alert extends BaseAdminCommand {

        Alert() {
            super("alert", NetworkPermissions.ALERT, MessageKey.of("commands.alert.usage"), MessageKey.of("commands.alert.description"));
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.run(NetworkActions.ALERT, invocation.joinArgs(0));
        }
    }
}
