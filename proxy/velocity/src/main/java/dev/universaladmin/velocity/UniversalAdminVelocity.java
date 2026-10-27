package dev.universaladmin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import dev.universaladmin.proxy.ProxyChecks;
import dev.universaladmin.proxy.ProxyLog;
import dev.universaladmin.proxy.ProxySettings;
import dev.universaladmin.proxy.ProxyUpdateCheck;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * UniversalAdmin for Velocity. Optional in network mode: the servers already
 * refuse banned players, players during maintenance and players not on the
 * network whitelist on join, but a server without UniversalAdmin (a
 * minigame, a fallback) would let them in - this checks every login at the
 * proxy ({@link ProxyChecks}, shared with the BungeeCord plugin) and shows
 * the network's maintenance in the server list. It only reads.
 */
@Plugin(id = "universaladmin-velocity", name = "UniversalAdmin Velocity", version = BuildConstants.VERSION,
        description = "Checks UniversalAdmin bans, network maintenance and the network whitelist at the proxy.",
        url = "https://github.com/universaladmin-mc/UniversalAdmin", authors = {"UniversalAdmin"})
public final class UniversalAdminVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ProxyChecks checks;

    @Inject
    public UniversalAdminVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        ProxySettings settings;
        try {
            settings = ProxySettings.load(dataDirectory);
        } catch (Exception e) {
            logger.error("Could not read config.yml - the ban, maintenance and whitelist checks stay off.", e);
            return;
        }
        ProxyLog log = new ProxyLog() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }
        };
        checks = new ProxyChecks(settings, log);
        checks.start("universaladmin-velocity");
        if (settings.updateCheck()) {
            ProxyUpdateCheck updates = new ProxyUpdateCheck(BuildConstants.VERSION, "universaladmin-velocity-", log);
            proxy.getScheduler().buildTask(this, updates::check)
                    .delay(ProxyUpdateCheck.INITIAL_DELAY).repeat(ProxyUpdateCheck.INTERVAL).schedule();
        }
        CommandMeta meta = proxy.getCommandManager().metaBuilder("uavelocity").plugin(this).build();
        proxy.getCommandManager().register(meta, new StatusCommand());
    }

    // Early (the value PostOrder.EARLY stood for), so other plugins see the refusal.
    @Subscribe(priority = Short.MAX_VALUE / 2)
    public EventTask onLogin(LoginEvent event) {
        ProxyChecks current = checks;
        if (current == null || !current.active() || !event.getResult().isAllowed()) {
            return null;
        }
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            // Velocity knows the player's permissions at login already.
            InetSocketAddress address = player.getRemoteAddress();
            String ip = address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
            current.refusalFor(current.check(player.getUniqueId(), player.getUsername(), ip), player::hasPermission)
                    .ifPresent(refusal -> {
                        current.refused(refusal);
                        event.setResult(ResultedEvent.ComponentResult.denied(refusal.message()));
                    });
        });
    }

    /** During network maintenance: the configured server-list text instead of the usual one. */
    @Subscribe
    public EventTask onPing(ProxyPingEvent event) {
        ProxyChecks current = checks;
        if (current == null || !current.active()) {
            return null;
        }
        return EventTask.async(() -> current.ping().ifPresent(override -> {
            ServerPing.Builder ping = event.getPing().asBuilder();
            if (override.description() != null) {
                ping.description(override.description());
            }
            if (override.versionText() != null) {
                ping.version(new ServerPing.Version(ProxyChecks.MAINTENANCE_PING_PROTOCOL, override.versionText()));
            }
            event.setPing(ping.build());
        }));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (checks != null) {
            checks.close();
        }
    }

    /** {@code /uavelocity} - whether the checks run, and how many logins they refused. */
    private final class StatusCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            if (checks == null) {
                return;
            }
            invocation.source().sendMessage(checks.status(
                    "<gradient:#55ddff:#3399ff><bold>UniversalAdmin Velocity</bold></gradient> <gray>" + BuildConstants.VERSION));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("universaladmin.velocity.status");
        }
    }
}
