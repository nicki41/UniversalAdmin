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
import dev.universaladmin.modules.whitelist.WhitelistMemberView;
import dev.universaladmin.modules.whitelist.WhitelistPermissions;
import dev.universaladmin.modules.whitelist.WhitelistService;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryInput;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;

/**
 * {@code /admin whitelist <on|off|add|remove|list>} (alias {@code wl}).
 * {@code add} also works for players who have never joined: the name is
 * resolved to a UUID through Paper's profile lookup, off the main thread.
 */
public final class WhitelistCommands {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "add", "remove", "list");

    private WhitelistCommands() {
    }

    public static List<AdminCommand> all(WhitelistService whitelist) {
        return List.of(new Whitelist(whitelist));
    }

    static final class Whitelist extends BaseAdminCommand {

        private final WhitelistService whitelist;

        Whitelist(WhitelistService whitelist) {
            super("whitelist", WhitelistPermissions.VIEW, MessageKey.of("commands.whitelist.usage"),
                    MessageKey.of("commands.whitelist.description"));
            this.whitelist = whitelist;
            aliases("wl");
            shortcuts("wl");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("list");
            switch (sub) {
                case "on", "enable" -> invocation.run(WhitelistActionIds.ENABLE, null);
                case "off", "disable" -> invocation.run(WhitelistActionIds.DISABLE, null);
                case "add" -> add(invocation);
                case "remove" -> remove(invocation);
                case "list" -> list(invocation);
                default -> invocation.usage(this);
            }
        }

        /** {@code add <player> [duration] [reason]}. */
        private void add(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            Instant expiresAt = null;
            int reasonIndex = 2;
            if (invocation.argCount() > 2) {
                try {
                    Optional<Duration> duration = DurationParser.parse(invocation.args().get(2));
                    expiresAt = duration.map(Instant.now()::plus).orElse(null);
                    reasonIndex = 3;
                } catch (DurationParseException notADuration) {
                    // no duration given - the third argument starts the reason
                }
            }
            String reason = invocation.joinArgs(reasonIndex).trim();
            Instant expiry = expiresAt;
            resolve(invocation.args().get(1)).whenComplete((player, error) -> {
                if (error != null || player.isEmpty()) {
                    invocation.reply("command.error.player-not-found", invocation.args().get(1));
                    return;
                }
                PlayerRef ref = player.get();
                invocation.run(WhitelistActionIds.ADD,
                        new AddWhitelistEntryInput(ref.id(), ref.name(), reason.isEmpty() ? null : reason, null, expiry));
            });
        }

        private void remove(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String name = invocation.args().get(1);
            whitelist.listMembers().whenComplete((members, error) -> {
                Optional<WhitelistMemberView> member = error != null ? Optional.empty() : members.stream()
                        .filter(candidate -> name.equalsIgnoreCase(candidate.playerName()))
                        .findFirst();
                if (member.isEmpty()) {
                    invocation.reply("commands.whitelist.not-listed", name);
                    return;
                }
                invocation.run(WhitelistActionIds.REMOVE, member.get().playerId());
            });
        }

        private void list(CommandInvocation invocation) {
            boolean enabled = Bukkit.hasWhitelist();
            whitelist.listMembers().whenComplete((members, error) -> {
                if (error != null) {
                    invocation.reply("command.error.internal");
                    return;
                }
                invocation.line(enabled ? "commands.whitelist.list-header-on" : "commands.whitelist.list-header-off", members.size());
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
            if (invocation.argCount() <= 1) {
                return SUBCOMMANDS;
            }
            String sub = invocation.args().get(0).toLowerCase(Locale.ROOT);
            if (invocation.argCount() == 2 && sub.equals("add")) {
                return Arguments.visiblePlayerNames(invocation.sender());
            }
            if (invocation.argCount() == 2 && sub.equals("remove")) {
                return Bukkit.getWhitelistedPlayers().stream()
                        .map(org.bukkit.OfflinePlayer::getName)
                        .filter(java.util.Objects::nonNull)
                        .toList();
            }
            if (invocation.argCount() == 3 && sub.equals("add")) {
                return List.of("1d", "7d", "30d", "permanent");
            }
            return List.of();
        }
    }
}
