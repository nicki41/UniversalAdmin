package dev.universaladmin.modules.network.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The actions of the Network module: go to a player or server, bring a
 * player here, send a player to a server, and alert every server. All of
 * them only start the move - the proxy carries it out a moment later - and
 * all of them are audited like any other action.
 */
public final class NetworkActions {

    public static final ActionId GOTO = ActionId.core("network.goto");
    public static final ActionId BRING = ActionId.core("network.bring");
    public static final ActionId SEND = ActionId.core("network.send");
    public static final ActionId CONNECT = ActionId.core("network.connect");
    public static final ActionId ALERT = ActionId.core("network.alert");

    /** The channel alerts travel on. */
    public static final String ALERT_TOPIC = "network.alert";

    /** @param playerName who to move; {@code server} where to */
    public record SendInput(String playerName, String server) {
    }

    private NetworkActions() {
    }

    /** The acting staff member, online on this server - every move except {@link Send} needs one. */
    private static Optional<Player> actor(ActionContext context) {
        return context.actor().playerId() == null ? Optional.empty() : Optional.ofNullable(Bukkit.getPlayer(context.actor().playerId()));
    }

    private static <R> ActionResult<R> playerOnly() {
        return ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-only"));
    }

    private static <R> ActionResult<R> notInNetwork(String name) {
        return ActionResult.failure(FailureReason.NOT_FOUND, MessageKey.of("network.action.player-not-found"), name);
    }

    private static <R> ActionResult<R> noProxy() {
        return ActionResult.failure(FailureReason.CONFLICT, MessageKey.of("network.action.no-proxy"));
    }

    /** Takes the staff member to a player, wherever in the network they are. Input: the player's name. */
    public static final class Goto implements Action<String, NetworkPlayer> {

        private final NetworkService network;
        private final TaskScheduler scheduler;

        public Goto(NetworkService network, TaskScheduler scheduler) {
            this.network = network;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return GOTO;
        }

        @Override
        public CompletableFuture<ActionResult<NetworkPlayer>> execute(ActionContext context, String name) {
            return scheduler.supplyOnMainThread(() -> actor(context)).thenCompose(staff -> {
                if (staff.isEmpty()) {
                    return CompletableFuture.completedFuture(playerOnly());
                }
                Optional<NetworkPlayer> target = network.player(name);
                if (target.isEmpty()) {
                    return CompletableFuture.completedFuture(notInNetwork(name));
                }
                NetworkPlayer player = target.get();
                if (player.id() == null) {
                    // A server without UniversalAdmin: the proxy can take us
                    // there, but nobody there can place us next to them.
                    return network.connect(staff.get(), player.server()).thenApply(moved -> moved
                            ? ActionResult.success(player, MessageKey.of("network.result.goto-server-only"), player.name(), player.server())
                            : noProxy());
                }
                boolean here = player.server().equalsIgnoreCase(network.serverName());
                return network.sendTo(staff.get(), player.server(), new NetworkService.ArrivalTarget.ToPlayer(player.id()))
                        .thenApply(moved -> moved
                                ? ActionResult.success(player, MessageKey.of(here ? "network.result.goto-here" : "network.result.goto"),
                                        player.name(), player.server())
                                : noProxy());
            });
        }
    }

    /** Brings a player from anywhere in the network to the staff member. Input: the player's name. */
    public static final class Bring implements Action<String, NetworkPlayer> {

        private final NetworkService network;
        private final TaskScheduler scheduler;

        public Bring(NetworkService network, TaskScheduler scheduler) {
            this.network = network;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return BRING;
        }

        @Override
        public CompletableFuture<ActionResult<NetworkPlayer>> execute(ActionContext context, String name) {
            return scheduler.supplyOnMainThread(() -> actor(context)).thenCompose(staff -> {
                if (staff.isEmpty()) {
                    return CompletableFuture.completedFuture(playerOnly());
                }
                Optional<NetworkPlayer> target = network.player(name);
                if (target.isEmpty()) {
                    return CompletableFuture.completedFuture(notInNetwork(name));
                }
                NetworkPlayer player = target.get();
                return network.bring(staff.get(), player).thenApply(moved -> moved
                        ? ActionResult.success(player, MessageKey.of("network.result.bring"), player.name(), player.server())
                        : noProxy());
            });
        }
    }

    /** Sends a player to a server. Works from the console too, as long as someone is online here to carry the request. */
    public static final class Send implements Action<SendInput, NetworkPlayer> {

        private final NetworkService network;
        private final TaskScheduler scheduler;

        public Send(NetworkService network, TaskScheduler scheduler) {
            this.network = network;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return SEND;
        }

        @Override
        public CompletableFuture<ActionResult<NetworkPlayer>> execute(ActionContext context, SendInput input) {
            Optional<NetworkPlayer> target = network.player(input.playerName());
            if (target.isEmpty()) {
                return CompletableFuture.completedFuture(notInNetwork(input.playerName()));
            }
            Optional<NetworkServer> server = network.servers().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(input.server()))
                    .findFirst();
            if (server.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ActionResult.failure(FailureReason.NOT_FOUND, MessageKey.of("network.action.unknown-server"), input.server()));
            }
            NetworkPlayer player = target.get();
            String destination = server.get().name();
            if (player.server().equalsIgnoreCase(destination)) {
                return CompletableFuture.completedFuture(ActionResult.failure(FailureReason.CONFLICT,
                        MessageKey.of("network.action.already-there"), player.name(), destination));
            }
            return scheduler.supplyOnMainThread(() -> actor(context).or(() -> Bukkit.getOnlinePlayers().stream()
                            .<Player>map(online -> online).findFirst()))
                    .thenCompose(carrier -> carrier.isEmpty()
                            ? CompletableFuture.completedFuture(ActionResult.<NetworkPlayer>failure(
                                    FailureReason.CONFLICT, MessageKey.of("network.action.no-carrier")))
                            : network.connectOther(carrier.get(), player.name(), destination).thenApply(moved -> moved
                                    ? ActionResult.success(player, MessageKey.of("network.result.send"), player.name(), destination)
                                    : noProxy()));
        }
    }

    /** Takes the staff member to a server. Input: the server's name. */
    public static final class Connect implements Action<String, NetworkServer> {

        private final NetworkService network;
        private final TaskScheduler scheduler;

        public Connect(NetworkService network, TaskScheduler scheduler) {
            this.network = network;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return CONNECT;
        }

        @Override
        public CompletableFuture<ActionResult<NetworkServer>> execute(ActionContext context, String serverName) {
            Optional<NetworkServer> server = network.servers().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(serverName))
                    .findFirst();
            if (server.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ActionResult.failure(FailureReason.NOT_FOUND, MessageKey.of("network.action.unknown-server"), serverName));
            }
            if (server.get().name().equalsIgnoreCase(network.serverName())) {
                return CompletableFuture.completedFuture(ActionResult.failure(FailureReason.CONFLICT,
                        MessageKey.of("network.action.you-are-there"), server.get().name()));
            }
            return scheduler.supplyOnMainThread(() -> actor(context)).thenCompose(staff -> staff.isEmpty()
                    ? CompletableFuture.completedFuture(playerOnly())
                    : network.connect(staff.get(), server.get().name()).thenApply(moved -> moved
                            ? ActionResult.success(server.get(), MessageKey.of("network.result.connect"), server.get().name())
                            : noProxy()));
        }
    }

    /** Broadcasts a message on every server of the network. Input: the message (MiniMessage, staff-written). */
    public static final class Alert implements Action<String, Integer> {

        private final NetworkService network;
        private final MessageService messages;
        private final TaskScheduler scheduler;

        public Alert(NetworkService network, MessageService messages, TaskScheduler scheduler) {
            this.network = network;
            this.messages = messages;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return ALERT;
        }

        @Override
        public CompletableFuture<ActionResult<Integer>> execute(ActionContext context, String message) {
            deliver(message.trim());
            network.publish(ALERT_TOPIC, Map.of("message", message.trim()));
            int servers = (int) network.servers().stream().filter(server -> server.online() && server.universalAdmin()).count();
            return CompletableFuture.completedFuture(ActionResult.success(servers, MessageKey.of("network.result.alert"),
                    Integer.toString(Math.max(1, servers))));
        }

        /** Shows an alert to everyone on this server. */
        public void deliver(String message) {
            // The text is written by staff with the alert permission, like a
            // broadcast - its MiniMessage formatting is wanted.
            String text = messages.get(MessageKey.of("network.alert.format"), MessageService.markup(message));
            scheduler.runOnMainThread(() -> Bukkit.broadcast(ComponentMessages.render(text)));
        }
    }
}
