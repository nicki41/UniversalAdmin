package dev.universaladmin.modules.whitelist.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.NetworkWhitelistService;
import dev.universaladmin.modules.whitelist.WhitelistMemberView;
import dev.universaladmin.modules.whitelist.WhitelistPermissions;
import dev.universaladmin.modules.whitelist.WhitelistService;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryInput;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/**
 * {@code /admin whitelist [network] <on|off|add|remove|list>} (alias {@code wl}).
 * {@code add} also works for players who have never joined: the name is
 * resolved to a UUID through Paper's profile lookup, off the main thread.
 * With {@code network} first, the network whitelist instead of this server's.
 */
public final class WhitelistCommands {

    private static final String NETWORK = "network";
    private static final List<String> SUBCOMMANDS = List.of("on", "off", "add", "remove", "list");
    private static final List<String> NETWORK_SUBCOMMANDS = List.of("on", "off", "status", "add", "remove", "list");

    private WhitelistCommands() {
    }

    public static List<AdminCommand> all(WhitelistService whitelist, NetworkWhitelistService networkWhitelist) {
        return List.of(new Whitelist(whitelist, networkWhitelist));
    }

    static final class Whitelist extends BaseAdminCommand {

        private final WhitelistService whitelist;
        private final NetworkWhitelistService networkWhitelist;

        Whitelist(WhitelistService whitelist, NetworkWhitelistService networkWhitelist) {
            super("whitelist", WhitelistPermissions.VIEW, MessageKey.of("commands.whitelist.usage"),
                    MessageKey.of("commands.whitelist.description"));
            this.whitelist = whitelist;
            this.networkWhitelist = networkWhitelist;
            aliases("wl");
            shortcuts("wl");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            boolean network = invocation.arg(0).map(NETWORK::equalsIgnoreCase).orElse(false);
            if (network && !networkWhitelist.available()) {
                invocation.reply("whitelist.action.network-required");
                return;
            }
            int first = network ? 1 : 0;
            String sub = invocation.arg(first).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse(network ? "status" : "list");
            switch (sub) {
                case "on", "enable" -> invocation.run(network ? WhitelistActionIds.NETWORK_ENABLE : WhitelistActionIds.ENABLE, null);
                case "off", "disable" -> invocation.run(network ? WhitelistActionIds.NETWORK_DISABLE : WhitelistActionIds.DISABLE, null);
                case "add" -> add(invocation, first, network);
                case "remove" -> remove(invocation, first, network);
                case "list" -> list(invocation, network);
                case "status" -> {
                    if (network) {
                        invocation.reply(networkWhitelist.enabled() ? "commands.whitelist.network-status-on"
                                : "commands.whitelist.network-status-off", networkWhitelist.size());
                    } else {
                        list(invocation, false);
                    }
                }
                default -> invocation.usage(this);
            }
        }

        /** {@code [network] add <player> [duration] [reason]}. */
        private void add(CommandInvocation invocation, int first, boolean network) {
            if (invocation.argCount() < first + 2) {
                invocation.usage(this);
                return;
            }
            Instant expiresAt = null;
            int reasonIndex = first + 2;
            if (invocation.argCount() > first + 2) {
                try {
                    Optional<Duration> duration = DurationParser.parse(invocation.args().get(first + 2));
                    expiresAt = duration.map(Instant.now()::plus).orElse(null);
                    reasonIndex = first + 3;
                } catch (DurationParseException notADuration) {
                    // no duration given - this argument starts the reason
                }
            }
            String reason = invocation.joinArgs(reasonIndex).trim();
            Instant expiry = expiresAt;
            String name = invocation.args().get(first + 1);
            resolve(name).whenComplete((player, error) -> {
                if (error != null || player.isEmpty()) {
                    invocation.reply("command.error.player-not-found", name);
                    return;
                }
                PlayerRef ref = player.get();
                invocation.run(network ? WhitelistActionIds.NETWORK_ADD : WhitelistActionIds.ADD,
                        new AddWhitelistEntryInput(ref.id(), ref.name(), reason.isEmpty() ? null : reason, null, expiry));
            });
        }

        private void remove(CommandInvocation invocation, int first, boolean network) {
            if (invocation.argCount() < first + 2) {
                invocation.usage(this);
                return;
            }
            String name = invocation.args().get(first + 1);
            (network ? networkWhitelist : whitelist).listMembers().whenComplete((members, error) -> {
                Optional<WhitelistMemberView> member = error != null ? Optional.empty() : members.stream()
                        .filter(candidate -> name.equalsIgnoreCase(candidate.playerName()))
                        .findFirst();
                if (member.isEmpty()) {
                    invocation.reply("commands.whitelist.not-listed", name);
                    return;
                }
                invocation.run(network ? WhitelistActionIds.NETWORK_REMOVE : WhitelistActionIds.REMOVE, member.get().playerId());
            });
        }

        private void list(CommandInvocation invocation, boolean network) {
            boolean enabled = network ? networkWhitelist.enabled() : Bukkit.hasWhitelist();
            String header = (network ? "commands.whitelist.network-list-header-" : "commands.whitelist.list-header-") + (enabled ? "on" : "off");
            (network ? networkWhitelist : whitelist).listMembers().whenComplete((members, error) -> {
                if (error != null) {
                    invocation.reply("command.error.internal");
                    return;
                }
                invocation.line(header, members.size());
                if (!members.isEmpty()) {
                    invocation.line("commands.whitelist.list-entries", members.stream()
                            .sorted(Comparator.comparing(WhitelistMemberView::playerName, String.CASE_INSENSITIVE_ORDER))
                            .map(WhitelistMemberView::playerName)
                            .collect(Collectors.joining(", ")));
                }
            });
        }

        /** A known player first; otherwise a Mojang profile lookup, completed asynchronously by Paper. */
        private static CompletableFuture<Optional<PlayerRef>> resolve(String name) {
            Optional<PlayerRef> known = Arguments.knownPlayer(name);
            if (known.isPresent()) {
                return CompletableFuture.completedFuture(known);
            }
            PlayerProfile profile = Bukkit.createProfile(name);
            return profile.update().thenApply(completed -> completed.getId() == null || completed.getName() == null
                    ? Optional.<PlayerRef>empty()
                    : Optional.of(new PlayerRef(completed.getId(), completed.getName())));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            boolean network = invocation.argCount() > 1 && invocation.args().get(0).equalsIgnoreCase(NETWORK);
            int position = invocation.argCount() - (network ? 1 : 0);
            if (position <= 1) {
                if (network) {
                    return NETWORK_SUBCOMMANDS;
                }
                List<String> subcommands = new ArrayList<>(SUBCOMMANDS);
                if (networkWhitelist.available()) {
                    subcommands.add(NETWORK);
                }
                return subcommands;
            }
            String sub = invocation.args().get(network ? 1 : 0).toLowerCase(Locale.ROOT);
            if (position == 2 && sub.equals("add")) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            if (position == 2 && sub.equals("remove")) {
                return network
                        ? networkWhitelist.names()
                        : Bukkit.getWhitelistedPlayers().stream()
                                .map(org.bukkit.OfflinePlayer::getName)
                                .filter(java.util.Objects::nonNull)
                                .toList();
            }
            if (position == 3 && sub.equals("add")) {
                return List.of("1d", "7d", "30d", "permanent");
            }
            return List.of();
        }
    }
}
