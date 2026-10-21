package dev.universaladmin.placeholders;

import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.MaintenanceStatus;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.status.ReportOverview;
import java.util.List;
import java.util.Optional;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** {@code %universaladmin_...%} - the list is in docs/extensions/placeholderapi.md. */
final class UniversalAdminExpansion extends PlaceholderExpansion implements Placeholders.Environment {

    private static final String STAFF_PERMISSION = "universaladmin.menu.open";

    private final Plugin plugin;
    private final UniversalAdmin universalAdmin;
    private final Placeholders placeholders;

    UniversalAdminExpansion(Plugin plugin, UniversalAdmin universalAdmin) {
        this.plugin = plugin;
        this.universalAdmin = universalAdmin;
        this.placeholders = new Placeholders(this);
    }

    @Override
    public String getIdentifier() {
        return "universaladmin";
    }

    @Override
    public String getAuthor() {
        return "UniversalAdmin";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Registered by our own plugin, so it must survive {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return placeholders.resolve(player == null ? null : player.getUniqueId(), params);
    }

    @Override
    public Optional<PlayerStatus> playerStatus() {
        return universalAdmin.services().get(PlayerStatus.class);
    }

    @Override
    public Optional<ReportOverview> reports() {
        return universalAdmin.services().get(ReportOverview.class);
    }

    @Override
    public Optional<MaintenanceStatus> maintenance() {
        return universalAdmin.services().get(MaintenanceStatus.class);
    }

    @Override
    public NetworkService network() {
        return universalAdmin.network();
    }

    @Override
    public List<Placeholders.OnlinePlayer> online() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(online -> new Placeholders.OnlinePlayer(online.getUniqueId(), online.hasPermission(STAFF_PERMISSION)))
                .toList();
    }

    @Override
    public String yes() {
        return PlaceholderAPIPlugin.booleanTrue();
    }

    @Override
    public String no() {
        return PlaceholderAPIPlugin.booleanFalse();
    }

    @Override
    public String permanent() {
        return universalAdmin.messages().get(MessageKey.of("placeholders.permanent"));
    }
}
