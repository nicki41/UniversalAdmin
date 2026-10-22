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
 * refuse banned players, players during maintenance and players not on the
 * network whitelist on join, but a server without UniversalAdmin (a
 * minigame, a fallback) would let them in - this checks every login at the
 * proxy, against the database the servers share, and shows the network's
 * maintenance in the server list. It only reads.
 */
@Plugin(id = "universaladmin-velocity", name = "UniversalAdmin Velocity", version = BuildConstants.VERSION,
        description = "Checks UniversalAdmin bans, network maintenance and the network whitelist at the proxy.",
        url = "https://github.com/nicki41/UniversalAdmin", authors = {"UniversalAdmin"})
public final class UniversalAdminVelocity {

    /** The same node the servers check - granted on the proxy by its permissions plugin (e.g. LuckPerms). */
    private static final String BYPASS_MAINTENANCE = "universaladmin.bypass.maintenance";
    private static final String BYPASS_NETWORK_WHITELIST = "universaladmin.bypass.network-whitelist";
    private static final long WARNING_INTERVAL_MILLIS = 60_000;
    /** How long the network's maintenance row is trusted before it is read again. */
    private static final long MAINTENANCE_MAX_AGE_MILLIS = 3_000;
    /** A login or ping waits at most this long for a database connection (Hikari's default is 30 s). */
    private static final long CONNECTION_TIMEOUT_MILLIS = 5_000;
    /** Any protocol no client speaks: the client then shows the version text instead of the player count. */
    private static final int MAINTENANCE_PING_PROTOCOL = 1;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong refusedMaintenance = new AtomicLong();
    private final AtomicLong refusedWhitelist = new AtomicLong();
    private final AtomicLong lastWarning = new AtomicLong();
    private final AtomicLong lastMaintenanceWarning = new AtomicLong();
    private final AtomicLong lastWhitelistWarning = new AtomicLong();
    private volatile String lastError;

    private VelocitySettings settings;
    private HikariDataSource dataSource;
    private BanLookup bans;
    private MaintenanceLookup maintenance;
    private WhitelistLookup whitelist;
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
            logger.error("Could not read config.yml - the ban and maintenance checks stay off.", e);
            return;
        }
        screen = new BanScreen(settings.bannedPermanent(), settings.bannedTemporary(), settings.noReason(), settings.datePattern());
        if (!settings.banCheck() && !settings.maintenanceCheck() && !settings.whitelistCheck()) {
            logger.info("All checks are switched off (ban-check, maintenance-check and whitelist-check in config.yml).");
        } else if (settings.password().isEmpty() && "universaladmin".equals(settings.username())
                && "localhost".equals(settings.host())) {
            logger.warn("Fill in the database connection in plugins/universaladmin-velocity/config.yml "
                    + "(the same MySQL/MariaDB database your servers use) - the checks stay off until then.");
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
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        // The proxy often starts before the database or the first server:
        // keep the pool and keep trying instead of giving up at startup.
        config.setInitializationFailTimeout(-1);
        dataSource = new HikariDataSource(config);
        if (settings.banCheck()) {
            bans = new BanLookup(dataSource);
            try {
                bans.activeBan(new UUID(0, 0), null, Instant.now());
                logger.info("Ban check active: players banned with UniversalAdmin are refused at the proxy.");
            } catch (SQLException e) {
                logger.warn("Ban check started, but the database is not ready yet (" + e.getMessage() + ") - "
                        + "it is checked again on every login. A server with network mode creates the tables on its first start.");
            }
        } else {
            logger.info("Ban check is switched off (ban-check.enabled: false).");
        }
        if (settings.maintenanceCheck()) {
            maintenance = new MaintenanceLookup(dataSource, MAINTENANCE_MAX_AGE_MILLIS);
            boolean on = maintenance.current(System.currentTimeMillis()).enabled();
            if (maintenance.lastError() == null) {
                logger.info("Maintenance check active: during network maintenance only staff and allowed players get in"
                        + (on ? " - the network is in maintenance right now." : "."));
            } else {
                logger.warn("Maintenance check started, but the maintenance state cannot be read yet (" + maintenance.lastError()
                        + ") - it is read again on every login. UniversalAdmin 1.2.1 or newer on the servers creates it.");
            }
        } else {
            logger.info("Maintenance check is switched off (maintenance-check.enabled: false).");
        }
        if (settings.whitelistCheck()) {
            whitelist = new WhitelistLookup(dataSource, MAINTENANCE_MAX_AGE_MILLIS);
            boolean on = whitelist.enabled(System.currentTimeMillis());
            if (whitelist.lastError() == null) {
                logger.info("Network whitelist check active" + (on ? " - the network whitelist is on." : "."));
            } else {
                logger.warn("Network whitelist check started, but its state cannot be read yet (" + whitelist.lastError()
                        + ") - it is read again on every login. UniversalAdmin 1.3.0 or newer on the servers creates it.");
            }
        } else {
            logger.info("Network whitelist check is switched off (whitelist-check.enabled: false).");
        }
    }

    // Early (the value PostOrder.EARLY stood for), so other plugins see the refusal.
    @Subscribe(priority = Short.MAX_VALUE / 2)
    public EventTask onLogin(LoginEvent event) {
        if ((bans == null && maintenance == null && whitelist == null) || !event.getResult().isAllowed()) {
            return null;
        }
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            if (bans != null && refuseBanned(event, player)) {
                return;
            }
            if (maintenance != null && refuseDuringMaintenance(event, player)) {
                return;
            }
            if (whitelist != null) {
                refuseUnlisted(event, player);
            }
        });
    }

    /** @return whether the login was refused */
    private boolean refuseBanned(LoginEvent event, Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        String ip = address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
        Instant now = Instant.now();
        try {
            Optional<BanLookup.Ban> ban = bans.activeBan(player.getUniqueId(), ip, now);
            lastError = null;
            if (ban.isPresent()) {
                refused.incrementAndGet();
                event.setResult(ResultedEvent.ComponentResult.denied(screen.render(ban.get(), now)));
                return true;
            }
            return false;
        } catch (SQLException | RuntimeException e) {
            lastError = e.getMessage();
            if (rateLimited(lastWarning)) {
                logger.warn("Ban check for " + player.getUsername() + " failed: " + e.getMessage()
                        + (settings.allowOnError() ? " - letting them in, the servers check again on join." : ""));
            }
            if (!settings.allowOnError()) {
                event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text("The login check is unavailable right now - please try again in a minute.")));
                return true;
            }
            return false;
        }
    }

    /** @return whether the login was refused */
    private boolean refuseDuringMaintenance(LoginEvent event, Player player) {
        MaintenanceLookup.Maintenance state = maintenance.current(System.currentTimeMillis());
        if (maintenance.lastError() != null && rateLimited(lastMaintenanceWarning)) {
            logger.warn("Maintenance check failed: " + maintenance.lastError() + " - letting players in, the servers check again on join.");
        }
        if (!state.enabled() || state.allows(player.getUsername()) || player.hasPermission(BYPASS_MAINTENANCE)) {
            return false;
        }
        refusedMaintenance.incrementAndGet();
        String message = state.message() == null || state.message().isBlank() ? settings.maintenanceMessage() : state.message();
        event.setResult(ResultedEvent.ComponentResult.denied(MiniMessage.miniMessage().deserialize(message)));
        return true;
    }

    private void refuseUnlisted(LoginEvent event, Player player) {
        long now = System.currentTimeMillis();
        boolean on = whitelist.enabled(now);
        if (whitelist.lastError() != null && rateLimited(lastWhitelistWarning)) {
            logger.warn("Network whitelist check failed: " + whitelist.lastError() + " - letting players in, the servers check again on join.");
        }
        if (!on || player.hasPermission(BYPASS_NETWORK_WHITELIST) || whitelist.listed(player.getUniqueId(), now)) {
            return;
        }
        refusedWhitelist.incrementAndGet();
        event.setResult(ResultedEvent.ComponentResult.denied(MiniMessage.miniMessage().deserialize(settings.notWhitelisted())));
    }

    /** During network maintenance: the configured server-list text instead of the usual one. */
    @Subscribe
    public EventTask onPing(ProxyPingEvent event) {
        if (maintenance == null || (settings.maintenanceMotd().isBlank() && settings.maintenanceVersionText().isBlank())) {
            return null;
        }
        return EventTask.async(() -> {
            if (!maintenance.current(System.currentTimeMillis()).enabled()) {
                return;
            }
            ServerPing.Builder ping = event.getPing().asBuilder();
            if (!settings.maintenanceMotd().isBlank()) {
                ping.description(MiniMessage.miniMessage().deserialize(settings.maintenanceMotd()));
            }
            if (!settings.maintenanceVersionText().isBlank()) {
                ping.version(new ServerPing.Version(MAINTENANCE_PING_PROTOCOL, settings.maintenanceVersionText()));
            }
            event.setPing(ping.build());
        });
    }

    private static boolean rateLimited(AtomicLong last) {
        long nowMillis = System.currentTimeMillis();
        return nowMillis - last.getAndSet(nowMillis) > WARNING_INTERVAL_MILLIS;
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        close();
    }

    private void close() {
        bans = null;
        maintenance = null;
        whitelist = null;
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** {@code /uavelocity} - whether the checks run, and how many logins they refused. */
    private final class StatusCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            String banState = bans == null ? "<red>off <gray>(see the proxy log)"
                    : lastError == null ? "<green>active" : "<yellow>active, last error: <white>" + lastError;
            String maintenanceState;
            if (maintenance == null) {
                maintenanceState = "<red>off <gray>(see the proxy log)";
            } else {
                boolean on = maintenance.current(System.currentTimeMillis()).enabled();
                maintenanceState = maintenance.lastError() != null
                        ? "<yellow>active, last error: <white>" + maintenance.lastError()
                        : "<green>active <gray>- network maintenance: " + (on ? "<red>on" : "<green>off");
            }
            String whitelistState;
            if (whitelist == null) {
                whitelistState = "<red>off <gray>(see the proxy log)";
            } else {
                boolean on = whitelist.enabled(System.currentTimeMillis());
                whitelistState = whitelist.lastError() != null
                        ? "<yellow>active, last error: <white>" + whitelist.lastError()
                        : "<green>active <gray>- network whitelist: " + (on ? "<red>on" : "<green>off");
            }
            invocation.source().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gradient:#55ddff:#3399ff><bold>UniversalAdmin Velocity</bold></gradient> <gray>" + BuildConstants.VERSION
                            + "\n<gray>Ban check: " + banState
                            + "\n<gray>Maintenance check: " + maintenanceState
                            + "\n<gray>Network whitelist check: " + whitelistState
                            + "\n<gray>Refused logins since start: <white>" + refused.get() + "</white> banned, <white>"
                            + refusedMaintenance.get() + "</white> during maintenance, <white>" + refusedWhitelist.get()
                            + "</white> not whitelisted"));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("universaladmin.velocity.status");
        }
    }
}
