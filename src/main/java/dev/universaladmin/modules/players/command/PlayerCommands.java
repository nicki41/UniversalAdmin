package dev.universaladmin.modules.players.command;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.PlayerService;
import dev.universaladmin.modules.players.PlayerSnapshot;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.modules.players.action.SetFloatValueInput;
import dev.universaladmin.modules.players.action.SetGamemodeInput;
import dev.universaladmin.modules.players.action.TeleportInput;
import dev.universaladmin.modules.players.action.TeleportKind;
import dev.universaladmin.modules.players.gui.PlayersGui;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.network.NetworkVisibility;
import dev.universaladmin.permission.PermissionNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Every {@code /admin} subcommand of the Players module - thin frontends
 * over the module's existing actions and pages.
 */
public final class PlayerCommands {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Map<String, GameMode> GAMEMODE_ALIASES = Map.ofEntries(
            Map.entry("survival", GameMode.SURVIVAL), Map.entry("s", GameMode.SURVIVAL), Map.entry("0", GameMode.SURVIVAL),
            Map.entry("creative", GameMode.CREATIVE), Map.entry("c", GameMode.CREATIVE), Map.entry("1", GameMode.CREATIVE),
            Map.entry("adventure", GameMode.ADVENTURE), Map.entry("a", GameMode.ADVENTURE), Map.entry("2", GameMode.ADVENTURE),
            Map.entry("spectator", GameMode.SPECTATOR), Map.entry("sp", GameMode.SPECTATOR), Map.entry("3", GameMode.SPECTATOR));

    private PlayerCommands() {
    }

    /** {@code gui} is looked up lazily - the Players GUI is built after the commands in {@code PlayersModule#onEnable}. */
    public static List<AdminCommand> all(PlayerService players, Supplier<PlayersGui> gui, MessageService messages, NetworkService network) {
        return List.of(
                new Profile(gui),
                new Info(players, messages),
                new SimpleTargetAction("heal", PlayerPermissions.HEAL, PlayerActionIds.HEAL,
                        "commands.heal.usage", "commands.heal.description"),
                new SimpleTargetAction("feed", PlayerPermissions.FEED, PlayerActionIds.FEED,
                        "commands.feed.usage", "commands.feed.description"),
                new SimpleTargetAction("extinguish", PlayerPermissions.EXTINGUISH, PlayerActionIds.EXTINGUISH,
                        "commands.extinguish.usage", "commands.extinguish.description"),
                new Fly(),
                new Gamemode(),
                new Speed(),
                new Teleport(network),
                new TeleportHere(network),
                new OpenInventory("invsee", PlayerPermissions.INVENTORY_VIEW, gui, false),
                new OpenInventory("endersee", PlayerPermissions.ENDERCHEST_VIEW, gui, true),
                new ClearInventory());
    }

    /** {@code /admin player <name>} - opens a player's profile page. */
    static final class Profile extends BaseAdminCommand {

        private final Supplier<PlayersGui> gui;

        Profile(Supplier<PlayersGui> gui) {
            super("player", PlayerPermissions.VIEW, MessageKey.of("commands.player.usage"), MessageKey.of("commands.player.description"));
            this.gui = gui;
            aliases("p", "profile");
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Player viewer = invocation.player().orElseThrow();
            invocation.withPlayer(0, target -> gui.get().openProfile(viewer, target));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin info <player>} - a text summary of a player, online or offline. */
    static final class Info extends BaseAdminCommand {

        private final PlayerService players;
        private final MessageService messages;

        Info(PlayerService players, MessageService messages) {
            super("info", PlayerPermissions.VIEW, MessageKey.of("commands.info.usage"), MessageKey.of("commands.info.description"));
            this.players = players;
            this.messages = messages;
            aliases("seen", "whois");
            shortcuts("seen", "whois");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.withPlayer(0, target -> players.snapshot(target.id()).whenComplete((snapshot, error) -> {
                if (error != null) {
                    invocation.reply("command.error.internal");
                } else if (snapshot.isEmpty()) {
                    invocation.reply("players.not-found", target.name());
                } else {
                    report(invocation, snapshot.get());
                }
            }));
        }

        private void report(CommandInvocation invocation, PlayerSnapshot snapshot) {
            invocation.line("commands.info.header", snapshot.name(),
                    MessageService.markup(messages.get(MessageKey.of(snapshot.online()
                            ? "players.gui.field.status-online" : "players.gui.field.status-offline"))));
            invocation.line("commands.info.uuid", snapshot.id().toString());
            invocation.line("commands.info.first-join", format(snapshot.firstJoin()));
            invocation.line("commands.info.last-seen", format(snapshot.lastSeen()));
            invocation.line("commands.info.playtime", DurationParser.format(snapshot.totalPlaytime()));
            if (snapshot.online() && snapshot.world() != null && snapshot.x() != null) {
                invocation.line("commands.info.location", String.valueOf(snapshot.world()),
                        Math.round(snapshot.x()), Math.round(snapshot.y()), Math.round(snapshot.z()));
                invocation.line("commands.info.state", String.valueOf(snapshot.gamemode()),
                        "%.1f".formatted(snapshot.health()), String.valueOf(snapshot.food()), String.valueOf(snapshot.ping()));
            }
        }

        private static String format(Instant instant) {
            return instant == null ? "-" : DATE_TIME.format(instant);
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin <heal|feed|extinguish> [player]} - one action on one online player (default: yourself). */
    static final class SimpleTargetAction extends BaseAdminCommand {

        private final ActionId action;

        SimpleTargetAction(String name, PermissionNode permission, ActionId action, String usage, String description) {
            super(name, permission, MessageKey.of(usage), MessageKey.of(description));
            this.action = action;
            shortcuts(name);
        }

        @Override
        public void execute(CommandInvocation invocation) {
            invocation.onlinePlayerOrSelf(0, this).ifPresent(target ->
                    invocation.run(action, new PlayerTargetInput(target.getUniqueId())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin fly [player]} - toggles flight. */
    static final class Fly extends BaseAdminCommand {

        Fly() {
            super("fly", PlayerPermissions.FLY_TOGGLE, MessageKey.of("commands.fly.usage"), MessageKey.of("commands.fly.description"));
            shortcuts("fly");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            invocation.onlinePlayerOrSelf(0, this).ifPresent(target -> invocation.run(
                    PlayerActionIds.FLY_TOGGLE, new PlayerTargetInput(target.getUniqueId())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin gamemode <mode> [player]} - accepts names, initials and the numbers 0-3. */
    static final class Gamemode extends BaseAdminCommand {

        Gamemode() {
            super("gamemode", PlayerPermissions.GAMEMODE, MessageKey.of("commands.gamemode.usage"),
                    MessageKey.of("commands.gamemode.description"));
            aliases("gm");
            shortcuts("gm");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            GameMode mode = GAMEMODE_ALIASES.get(invocation.args().get(0).toLowerCase(Locale.ROOT));
            if (mode == null) {
                invocation.reply("commands.gamemode.unknown", invocation.args().get(0));
                return;
            }
            invocation.onlinePlayerOrSelf(1, this).ifPresent(target -> invocation.run(PlayerActionIds.GAMEMODE,
                    new SetGamemodeInput(target.getUniqueId(), mode)));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return List.of("survival", "creative", "adventure", "spectator");
            }
            return invocation.argCount() == 2 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /**
     * {@code /admin speed <fly|walk> <0-10> [player]} - 10 is the maximum,
     * 1 the vanilla fly speed and 2 the vanilla walk speed.
     */
    static final class Speed extends BaseAdminCommand {

        Speed() {
            // Visible to staff in general; the fly/walk speed actions check
            // their own, more specific permission when it runs.
            super("speed", PlayerPermissions.VIEW, MessageKey.of("commands.speed.usage"), MessageKey.of("commands.speed.description"));
            shortcuts("speed");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String kind = invocation.args().get(0).toLowerCase(Locale.ROOT);
            boolean fly = kind.startsWith("f");
            if (!fly && !kind.startsWith("w")) {
                invocation.usage(this);
                return;
            }
            Optional<Double> value = invocation.decimal(1);
            if (value.isEmpty()) {
                return;
            }
            if (value.get() < 0 || value.get() > 10) {
                invocation.reply("players.action.out-of-range", 0, 10);
                return;
            }
            float speed = (float) (value.get() / 10.0);
            invocation.onlinePlayerOrSelf(2, this).ifPresent(target -> invocation.run(
                    fly ? PlayerActionIds.FLY_SPEED : PlayerActionIds.WALK_SPEED,
                    new SetFloatValueInput(target.getUniqueId(), speed)));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return switch (invocation.argCount()) {
                case 0, 1 -> List.of("fly", "walk");
                case 2 -> List.of("1", "2", "3", "5", "10");
                case 3 -> Arguments.visiblePlayerNames(invocation.sender());
                default -> List.of();
            };
        }
    }

    /** {@code /admin tp <player> [destination]} - to a player, or one player to another. */
    static final class Teleport extends BaseAdminCommand {

        private final NetworkService network;

        Teleport(NetworkService network) {
            super("tp", PlayerPermissions.TELEPORT, MessageKey.of("commands.tp.usage"), MessageKey.of("commands.tp.description"));
            this.network = network;
            aliases("teleport", "goto");
            shortcuts("tpto");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            if (invocation.argCount() == 1 && onOtherServer(network, invocation.args().get(0))) {
                // Network mode: the player is on another server - go there.
                invocation.run(NETWORK_GOTO, invocation.args().get(0));
                return;
            }
            Optional<Player> target = invocation.onlinePlayer(0);
            if (target.isEmpty()) {
                return;
            }
            if (invocation.argCount() == 1) {
                if (invocation.player().isEmpty()) {
                    invocation.usage(this);
                    return;
                }
                invocation.run(PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.ADMIN_TO_PLAYER, target.get().getUniqueId()));
                return;
            }
            invocation.onlinePlayer(1).ifPresent(destination -> invocation.run(PlayerActionIds.TELEPORT,
                    TeleportInput.toPlayer(target.get().getUniqueId(), destination.getUniqueId())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 2 ? withNetworkPlayers(network, invocation) : List.of();
        }
    }

    /** {@code /admin tphere <player>} - brings a player to you. */
    static final class TeleportHere extends BaseAdminCommand {

        private final NetworkService network;

        TeleportHere(NetworkService network) {
            super("tphere", PlayerPermissions.TELEPORT, MessageKey.of("commands.tphere.usage"), MessageKey.of("commands.tphere.description"));
            this.network = network;
            aliases("bring");
            shortcuts("tphere");
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            if (onOtherServer(network, invocation.args().get(0))) {
                // Network mode: fetch them from their server.
                invocation.run(NETWORK_BRING, invocation.args().get(0));
                return;
            }
            invocation.onlinePlayer(0).ifPresent(target -> invocation.run(PlayerActionIds.TELEPORT,
                    TeleportInput.of(TeleportKind.BRING_TO_ADMIN, target.getUniqueId())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? withNetworkPlayers(network, invocation) : List.of();
        }
    }

    /** {@code /admin <invsee|endersee> <player>} - opens the live inventory/ender chest editor. */
    static final class OpenInventory extends BaseAdminCommand {

        private final Supplier<PlayersGui> gui;
        private final boolean enderChest;

        OpenInventory(String name, PermissionNode permission, Supplier<PlayersGui> gui, boolean enderChest) {
            super(name, permission,
                    MessageKey.of(enderChest ? "commands.endersee.usage" : "commands.invsee.usage"),
                    MessageKey.of(enderChest ? "commands.endersee.description" : "commands.invsee.description"));
            this.gui = gui;
            this.enderChest = enderChest;
            if (enderChest) {
                aliases("ec", "enderchest");
            }
            shortcuts(name);
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Player viewer = invocation.player().orElseThrow();
            invocation.onlinePlayer(0).ifPresent(target -> {
                PlayerRef ref = new PlayerRef(target.getUniqueId(), target.getName());
                if (enderChest) {
                    gui.get().openEnderChest(viewer, ref);
                } else {
                    gui.get().openInventory(viewer, ref);
                }
            });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin clearinv <player> confirm} - permanently empties a player's inventory. */
    static final class ClearInventory extends BaseAdminCommand {

        ClearInventory() {
            super("clearinv", PlayerPermissions.INVENTORY_EDIT, MessageKey.of("commands.clearinv.usage"),
                    MessageKey.of("commands.clearinv.description"));
            aliases("clearinventory", "ci");
            shortcuts("clearinv");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            Optional<Player> target = invocation.onlinePlayer(0);
            if (target.isEmpty() || !invocation.confirmed()) {
                return;
            }
            invocation.run(PlayerActionIds.INVENTORY_CLEAR, new PlayerTargetInput(target.get().getUniqueId()));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return switch (invocation.argCount()) {
                case 0, 1 -> Arguments.visiblePlayerNames(invocation.sender());
                case 2 -> List.of("confirm");
                default -> List.of();
            };
        }
    }

    /** The Network module's actions - referenced by id only, the module may not be loaded (network mode off). */
    private static final ActionId NETWORK_GOTO = ActionId.core("network.goto");
    private static final ActionId NETWORK_BRING = ActionId.core("network.bring");

    /** Whether {@code name} is not here but online on another server of the network. */
    private static boolean onOtherServer(NetworkService network, String name) {
        return network.enabled() && Bukkit.getPlayerExact(name) == null
                && network.player(name).filter(player -> !player.server().equalsIgnoreCase(network.serverName())).isPresent();
    }

    /** Local names plus, in network mode, everyone on the other servers the sender may see. */
    private static List<String> withNetworkPlayers(NetworkService network, CommandInvocation invocation) {
        List<String> names = new ArrayList<>(Arguments.visiblePlayerNames(invocation.sender()));
        if (network.enabled()) {
            NetworkVisibility.players(invocation.sender(), network).stream().map(NetworkPlayer::name)
                    .filter(name -> !names.contains(name)).forEach(names::add);
        }
        return names;
    }
}
