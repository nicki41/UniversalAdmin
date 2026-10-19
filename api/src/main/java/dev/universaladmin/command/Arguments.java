package dev.universaladmin.command;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parsing and completion helpers for command arguments - the shared
 * vocabulary every {@link AdminCommand} uses so "a player name", "a
 * duration" or "a world" behave identically everywhere. Pure Bukkit lookups,
 * no business logic. Methods that touch the Bukkit API must be called on the
 * main thread, like {@link AdminCommand#execute} itself.
 */
public final class Arguments {

    private Arguments() {
    }

    /** An online player by exact name (case-insensitive). */
    public static Optional<Player> onlinePlayer(String name) {
        return Optional.ofNullable(Bukkit.getPlayerExact(name));
    }

    /**
     * Any player the server has seen, by name: an online player first, then
     * the server's cached profile of an offline one. Never contacts Mojang -
     * a name the server has never seen resolves to empty.
     */
    public static Optional<PlayerRef> knownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new PlayerRef(online.getUniqueId(), online.getName()));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return Optional.of(new PlayerRef(cached.getUniqueId(), cached.getName() != null ? cached.getName() : name));
        }
        // Network mode: online on another server.
        return UniversalAdminProvider.find()
                .flatMap(platform -> platform.network().player(name))
                .filter(player -> player.id() != null)
                .map(player -> new PlayerRef(player.id(), player.name()));
    }

    /** Names of online players {@code viewer} can see - vanished staff never leak through tab completion. */
    public static List<String> visiblePlayerNames(CommandSender viewer) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(viewer instanceof Player self) || self.canSee(player))
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> worldNames() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    public static Optional<World> world(String name) {
        return Optional.ofNullable(Bukkit.getWorld(name));
    }

    /** A positive duration such as {@code 30m} or {@code 1d12h}; empty for invalid input or "permanent". */
    public static Optional<Duration> duration(String raw) {
        try {
            return DurationParser.parse(raw);
        } catch (DurationParseException e) {
            return Optional.empty();
        }
    }

    /** Whether {@code raw} is a valid duration (including {@code permanent}). */
    public static boolean isDuration(String raw) {
        try {
            DurationParser.parse(raw);
            return true;
        } catch (DurationParseException e) {
            return false;
        }
    }

    public static OptionalInt integer(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static OptionalDouble decimal(String raw) {
        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** An enum constant by (case-insensitive) name, e.g. {@code creative} for {@code GameMode.CREATIVE}. */
    public static <E extends Enum<E>> Optional<E> enumValue(Class<E> type, String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(normalized)).findFirst();
    }

    /** Lower-case constant names of {@code type}, for completion. */
    public static <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(constant -> constant.name().toLowerCase(Locale.ROOT)).toList();
    }

    /** {@code candidates} that start with {@code prefix} (case-insensitive), in their original order. */
    public static List<String> matching(Collection<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
