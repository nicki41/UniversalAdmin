package dev.universaladmin.placeholders;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.MaintenanceStatus;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.status.ReportOverview;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Resolves {@code %universaladmin_<params>%} - without PlaceholderAPI or a
 * server, so it is testable on its own. Every value comes from UniversalAdmin's
 * in-memory status services; a placeholder whose module is switched off
 * resolves to an empty text, an unknown one to {@code null} (PlaceholderAPI
 * then leaves it as it is).
 */
final class Placeholders {

    /** What the placeholders read - the live implementation is {@link UniversalAdminExpansion}. */
    interface Environment {

        Optional<PlayerStatus> playerStatus();

        Optional<ReportOverview> reports();

        Optional<MaintenanceStatus> maintenance();

        NetworkService network();

        /** The players online on this server. */
        List<OnlinePlayer> online();

        String yes();

        String no();

        /** "permanent" in the server's language. */
        String permanent();
    }

    /** @param staff whether the player may open the admin menu ({@code universaladmin.menu.open}) */
    record OnlinePlayer(UUID id, boolean staff) {
    }

    private static final String SERVER_ONLINE_PREFIX = "network_online_";

    private final Environment environment;

    Placeholders(Environment environment) {
        this.environment = environment;
    }

    /** @param player the player the placeholder is shown for; {@code null} where there is none (console, holograms) */
    String resolve(UUID player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "vanished" -> flag(player, PlayerStatus::vanished);
            case "staffmode" -> flag(player, PlayerStatus::staffMode);
            case "frozen" -> flag(player, PlayerStatus::frozen);
            case "godmode" -> flag(player, PlayerStatus::godmode);
            case "muted" -> player(player, status -> bool(status.mute(player).isPresent()));
            case "mute_reason" -> player(player, status -> status.mute(player)
                    .map(mute -> mute.reason() == null ? "" : mute.reason())
                    .orElse(""));
            case "mute_remaining" -> player(player, status -> status.mute(player)
                    .map(mute -> mute.permanent() ? environment.permanent() : DurationParser.formatRemaining(mute.expiresAt()))
                    .orElse(""));
            case "warnings" -> player(player, status -> String.valueOf(status.activeWarnings(player)));
            case "punishments" -> player(player, status -> String.valueOf(status.punishments(player)));
            case "online" -> String.valueOf(visibleOnline(false));
            case "staff_online" -> String.valueOf(visibleOnline(true));
            case "vanished_count" -> environment.playerStatus().map(status -> String.valueOf(status.vanishedOnline())).orElse("");
            case "open_reports" -> environment.reports().map(reports -> String.valueOf(reports.openReports())).orElse("");
            case "maintenance" -> environment.maintenance().map(maintenance -> bool(maintenance.active())).orElse("");
            case "server" -> environment.network().enabled() ? environment.network().serverName() : "";
            case "network_online" -> String.valueOf(environment.network().enabled()
                    ? environment.network().players().size()
                    : environment.online().size());
            case "network_servers" -> String.valueOf(environment.network().enabled()
                    ? environment.network().servers().stream().filter(NetworkServer::online).count()
                    : 1);
            default -> key.startsWith(SERVER_ONLINE_PREFIX) ? serverOnline(key.substring(SERVER_ONLINE_PREFIX.length())) : null;
        };
    }

    private String flag(UUID player, BiPredicate<PlayerStatus, UUID> flag) {
        return player(player, status -> bool(flag.test(status, player)));
    }

    private String player(UUID player, Function<PlayerStatus, String> value) {
        if (player == null) {
            return "";
        }
        return environment.playerStatus().map(value).orElse("");
    }

    /** Online players others can see (vanished ones left out); only staff if {@code staffOnly}. */
    private long visibleOnline(boolean staffOnly) {
        Optional<PlayerStatus> status = environment.playerStatus();
        return environment.online().stream()
                .filter(online -> !staffOnly || online.staff())
                .filter(online -> status.isEmpty() || !status.get().vanished(online.id()))
                .count();
    }

    private String serverOnline(String server) {
        NetworkService network = environment.network();
        if (!network.enabled()) {
            return server.equalsIgnoreCase(network.serverName()) ? String.valueOf(environment.online().size()) : "";
        }
        return network.servers().stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(server))
                .findFirst()
                .map(found -> String.valueOf(found.online() ? found.playerCount() : 0))
                .orElse("");
    }

    private String bool(boolean value) {
        return value ? environment.yes() : environment.no();
    }
}
