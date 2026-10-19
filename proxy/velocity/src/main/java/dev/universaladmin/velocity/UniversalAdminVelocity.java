package dev.universaladmin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

/**
 * UniversalAdmin for Velocity. Optional in network mode: the servers already
 * refuse banned players on join, but a server without UniversalAdmin (a
 * minigame, a fallback) would let them in - this checks every login at the
 * proxy, against the database the servers share. It only reads.
 */
@Plugin(id = "universaladmin-velocity", name = "UniversalAdmin Velocity", version = BuildConstants.VERSION,
        description = "Turns players banned with UniversalAdmin away at the proxy.",
        url = "https://github.com/nicki41/UniversalAdmin")
public final class UniversalAdminVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private static final long WARNING_INTERVAL_MILLIS = 60_000;

    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong lastWarning = new AtomicLong();
    private volatile String lastError;

    private VelocitySettings settings;
    private HikariDataSource dataSource;
    private BanLookup bans;
    private BanScreen screen;

    @Inject
    public UniversalAdminVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            settings = VelocitySettings.load(dataDirectory);
        } catch (Exception e) {
            logger.error("Could not read config.yml - the ban check stays off.", e);
            return;
        }
        screen = new BanScreen(settings.bannedPermanent(), settings.bannedTemporary(), settings.noReason(), settings.datePattern());
        if (!settings.banCheck()) {
            logger.info("Ban check is switched off (ban-check.enabled: false).");
        } else if (settings.password().isEmpty() && "universaladmin".equals(settings.username())
                && "localhost".equals(settings.host())) {
            logger.warn("Fill in the database connection in plugins/universaladmin-velocity/config.yml "
                    + "(the same MySQL/MariaDB database your servers use) - the ban check stays off until then.");
        } else {
            connect();
        }
        CommandMeta meta = proxy.getCommandManager().metaBuilder("uavelocity").plugin(this).build();
        proxy.getCommandManager().register(meta, new StatusCommand());
    }

    private void connect() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("universaladmin-velocity");
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?sslMode=" + (settings.ssl() ? "trust" : "disable"));
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        config.setReadOnly(true);
        // The proxy often starts before the database or the first server:
        // keep the pool and keep trying instead of giving up at startup.
        config.setInitializationFailTimeout(-1);
        dataSource = new HikariDataSource(config);
        bans = new BanLookup(dataSource);
        try {
            bans.activeBan(new UUID(0, 0), null, Instant.now());
            logger.info("Ban check active: players banned with UniversalAdmin are refused at the proxy.");
        } catch (SQLException e) {
            logger.warn("Ban check started, but the database is not ready yet (" + e.getMessage() + ") - "
                    + "it is checked again on every login. A server with network mode creates the tables on its first start.");
        }
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onLogin(LoginEvent event) {
        if (bans == null || !event.getResult().isAllowed()) {
            return null;
        }
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            InetSocketAddress address = player.getRemoteAddress();
            String ip = address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
            Instant now = Instant.now();
            try {
                Optional<BanLookup.Ban> ban = bans.activeBan(player.getUniqueId(), ip, now);
                lastError = null;
                if (ban.isPresent()) {
                    refused.incrementAndGet();
                    event.setResult(ResultedEvent.ComponentResult.denied(screen.render(ban.get(), now)));
                }
            } catch (SQLException | RuntimeException e) {
                lastError = e.getMessage();
                long nowMillis = System.currentTimeMillis();
                if (nowMillis - lastWarning.getAndSet(nowMillis) > WARNING_INTERVAL_MILLIS) {
                    logger.warn("Ban check for " + player.getUsername() + " failed: " + e.getMessage()
                            + (settings.allowOnError() ? " - letting them in, the servers check again on join." : ""));
                }
                if (!settings.allowOnError()) {
                    event.setResult(ResultedEvent.ComponentResult.denied(
                            Component.text("The login check is unavailable right now - please try again in a minute.")));
                }
            }
        });
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        close();
    }

    private void close() {
        bans = null;
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** {@code /uavelocity} - whether the ban check runs, and how many logins it refused. */
    private final class StatusCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            String state = bans == null ? "<red>off <gray>(see the proxy log)"
                    : lastError == null ? "<green>active" : "<yellow>active, last error: <white>" + lastError;
            invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gradient:#55ddff:#3399ff><bold>UniversalAdmin Velocity</bold></gradient> <gray>" + BuildConstants.VERSION
                            + "\n<gray>Ban check: " + state + "\n<gray>Refused logins since start: <white>" + refused.get()));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("universaladmin.velocity.status");
        }
    }
}
