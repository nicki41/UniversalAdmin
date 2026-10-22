package dev.universaladmin.bungee;

import dev.universaladmin.proxy.ProxyChecks;
import dev.universaladmin.proxy.ProxyLog;
import dev.universaladmin.proxy.ProxySettings;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * UniversalAdmin for BungeeCord - the same checks as UniversalAdmin Velocity
 * ({@link ProxyChecks}). BungeeCord knows a player's permissions only after
 * the login, so the checks run in {@link LoginEvent} (off the event thread):
 * a ban refuses right there, maintenance and the network whitelist - which
 * a bypass node can override - are decided in {@link PostLoginEvent}, once
 * the permissions plugin has loaded the player and before they reach a
 * server. It only reads.
 */
public final class UniversalAdminBungee extends Plugin implements Listener {

    /** BungeeCord's own components understand the legacy format with hex colours. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    /** Refusals a bypass node may lift, from the login until the player is loaded. */
    private final Map<UUID, List<ProxyChecks.Refusal>> pending = new ConcurrentHashMap<>();

    private ProxyChecks checks;

    @Override
    public void onEnable() {
        ProxySettings settings;
        try {
            settings = ProxySettings.load(getDataFolder().toPath());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not read config.yml - the ban, maintenance and whitelist checks stay off.", e);
            return;
        }
        checks = new ProxyChecks(settings, new ProxyLog() {
            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warning(message);
            }
        });
        checks.start("universaladmin-bungee");
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new StatusCommand());
    }

    @Override
    public void onDisable() {
        if (checks != null) {
            checks.close();
        }
    }

    /** Early, so other plugins see the refusal. */
    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        ProxyChecks current = checks;
        if (current == null || !current.active() || event.isCancelled()) {
            return;
        }
        PendingConnection connection = event.getConnection();
        event.registerIntent(this);
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                String ip = connection.getSocketAddress() instanceof InetSocketAddress inet && inet.getAddress() != null
                        ? inet.getAddress().getHostAddress() : null;
                UUID playerId = connection.getUniqueId();
                List<ProxyChecks.Refusal> refusals = current.check(playerId, connection.getName(), ip);
                // Without permissions yet: only what no node can lift refuses here.
                current.refusalFor(refusals, permission -> true).ifPresentOrElse(refusal -> {
                    current.refused(refusal);
                    event.setReason(bungee(refusal.message()));
                    event.setCancelled(true);
                }, () -> {
                    if (!refusals.isEmpty()) {
                        pending.put(playerId, refusals);
                    }
                });
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "Login check for " + connection.getName() + " failed - letting them in", e);
            } finally {
                event.completeIntent(this);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        List<ProxyChecks.Refusal> refusals = pending.remove(player.getUniqueId());
        if (refusals == null || checks == null) {
            return;
        }
        checks.refusalFor(refusals, player::hasPermission).ifPresent(refusal -> {
            checks.refused(refusal);
            player.disconnect(bungee(refusal.message()));
        });
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    /** During network maintenance: the configured server-list text instead of the usual one. */
    @EventHandler
    public void onPing(ProxyPingEvent event) {
        ProxyChecks current = checks;
        if (current == null || !current.active()) {
            return;
        }
        event.registerIntent(this);
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                current.ping().ifPresent(override -> {
                    ServerPing ping = event.getResponse();
                    if (override.description() != null) {
                        ping.setDescriptionComponent(bungee(override.description()));
                    }
                    if (override.versionText() != null) {
                        ping.setVersion(new ServerPing.Protocol(override.versionText(), ProxyChecks.MAINTENANCE_PING_PROTOCOL));
                    }
                    event.setResponse(ping);
                });
            } finally {
                event.completeIntent(this);
            }
        });
    }

    static BaseComponent bungee(Component component) {
        return TextComponent.fromLegacy(LEGACY.serialize(component));
    }

    /** {@code /uabungee} - whether the checks run, and how many logins they refused. */
    private final class StatusCommand extends Command {

        StatusCommand() {
            super("uabungee", "universaladmin.bungee.status");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (checks != null) {
                sender.sendMessage(bungee(checks.status("<gradient:#55ddff:#3399ff><bold>UniversalAdmin BungeeCord</bold></gradient> <gray>"
                        + getDescription().getVersion())));
            }
        }
    }
}
