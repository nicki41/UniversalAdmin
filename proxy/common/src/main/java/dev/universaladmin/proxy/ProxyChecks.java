package dev.universaladmin.proxy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import javax.sql.DataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * What UniversalAdmin checks at a proxy - the same for Velocity and
 * BungeeCord, which only adapt their events to this: bans (refused, no
 * bypass), network maintenance and the network whitelist (refused unless the
 * player holds the bypass node, which is why a refusal names it - BungeeCord
 * only knows permissions after the login). Reads the database the servers
 * share; never writes.
 */
public final class ProxyChecks {

    /** What refuses a login; {@code bypassPermission} lets a player in anyway, {@code null} for none. */
    public record Refusal(Kind kind, Component message, String bypassPermission) {

        public boolean refuses(Predicate<String> hasPermission) {
            return bypassPermission == null || !hasPermission.test(bypassPermission);
        }
    }

    /** Which check refused. */
    public enum Kind {
        BANNED,
        MAINTENANCE,
        NOT_WHITELISTED,
        CHECK_FAILED
    }

    /** What the server list shows during network maintenance; either may be {@code null} (= unchanged). */
    public record PingOverride(Component description, String versionText) {
    }

    /** The node the servers check for maintenance - granted on the proxy by its permissions plugin. */
    public static final String BYPASS_MAINTENANCE = "universaladmin.bypass.maintenance";
    /** The node the servers check for the network whitelist. */
    public static final String BYPASS_NETWORK_WHITELIST = "universaladmin.bypass.network-whitelist";
    /** Any protocol no client speaks: the client then shows the version text instead of the player count. */
    public static final int MAINTENANCE_PING_PROTOCOL = 1;

    private static final long WARNING_INTERVAL_MILLIS = 60_000;
    /** How long the maintenance row and the whitelist switch are trusted before they are read again. */
    private static final long CACHE_MILLIS = 3_000;
    /** A login or ping waits at most this long for a database connection (Hikari's default is 30 s). */
    private static final long CONNECTION_TIMEOUT_MILLIS = 5_000;

    private final ProxySettings settings;
    private final ProxyLog log;
    private final BanScreen screen;
    private final AtomicLong refusedBanned = new AtomicLong();
    private final AtomicLong refusedMaintenance = new AtomicLong();
    private final AtomicLong refusedWhitelist = new AtomicLong();
    private final AtomicLong lastBanWarning = new AtomicLong();
    private final AtomicLong lastMaintenanceWarning = new AtomicLong();
    private final AtomicLong lastWhitelistWarning = new AtomicLong();

    private volatile HikariDataSource dataSource;
    private volatile BanLookup bans;
    private volatile MaintenanceLookup maintenance;
    private volatile WhitelistLookup whitelist;
    private volatile String lastBanError;

    public ProxyChecks(ProxySettings settings, ProxyLog log) {
        this.settings = settings;
        this.log = log;
        this.screen = new BanScreen(settings.bannedPermanent(), settings.bannedTemporary(), settings.noReason(), settings.datePattern());
    }

    /** Connects to the database and switches on the configured checks - or says in the log why not. */
    public void start(String poolName) {
        if (!settings.banCheck() && !settings.maintenanceCheck() && !settings.whitelistCheck()) {
            log.info("All checks are switched off (ban-check, maintenance-check and whitelist-check in config.yml).");
            return;
        }
        if (!settings.databaseConfigured()) {
            log.warn("Fill in the database connection in the plugin's config.yml (the same MySQL/MariaDB database your "
                    + "servers use) - the checks stay off until then.");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
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
        use(dataSource);
    }

    /** Switches on the configured checks against {@code source} - {@link #start} with the pool it made; tests with their own. */
    void use(DataSource source) {
        long now = System.currentTimeMillis();
        if (settings.banCheck()) {
            bans = new BanLookup(source);
            try {
                bans.activeBan(new UUID(0, 0), null, Instant.now());
                log.info("Ban check active: players banned with UniversalAdmin are refused at the proxy.");
            } catch (SQLException e) {
                log.warn("Ban check started, but the database is not ready yet (" + e.getMessage() + ") - it is checked "
                        + "again on every login. A server with network mode creates the tables on its first start.");
            }
        } else {
            log.info("Ban check is switched off (ban-check.enabled: false).");
        }
        if (settings.maintenanceCheck()) {
            maintenance = new MaintenanceLookup(source, CACHE_MILLIS);
            boolean on = maintenance.current(now).enabled();
            if (maintenance.lastError() == null) {
                log.info("Maintenance check active: during network maintenance only staff and allowed players get in"
                        + (on ? " - the network is in maintenance right now." : "."));
            } else {
                log.warn("Maintenance check started, but the maintenance state cannot be read yet (" + maintenance.lastError()
                        + ") - it is read again on every login. UniversalAdmin 1.2.1 or newer on the servers creates it.");
            }
        } else {
            log.info("Maintenance check is switched off (maintenance-check.enabled: false).");
        }
        if (settings.whitelistCheck()) {
            whitelist = new WhitelistLookup(source, CACHE_MILLIS);
            boolean on = whitelist.enabled(now);
            if (whitelist.lastError() == null) {
                log.info("Network whitelist check active" + (on ? " - the network whitelist is on." : "."));
            } else {
                log.warn("Network whitelist check started, but its state cannot be read yet (" + whitelist.lastError()
                        + ") - it is read again on every login. UniversalAdmin 1.3.0 or newer on the servers creates it.");
            }
        } else {
            log.info("Network whitelist check is switched off (whitelist-check.enabled: false).");
        }
    }

    /** Whether any check runs. */
    public boolean active() {
        return bans != null || maintenance != null || whitelist != null;
    }

    /**
     * Every reason to refuse this login, in order: ban (or an unavailable ban
     * check with {@code allow-on-error: false}), maintenance, whitelist. Blocks
     * on the database - call it off the proxy's event thread. Empty = let in.
     */
    public List<Refusal> check(UUID playerId, String playerName, String ip) {
        List<Refusal> refusals = new ArrayList<>();
        long nowMillis = System.currentTimeMillis();
        BanLookup banLookup = bans;
        if (banLookup != null) {
            Instant now = Instant.now();
            try {
                Optional<BanLookup.Ban> ban = banLookup.activeBan(playerId, ip, now);
                lastBanError = null;
                if (ban.isPresent()) {
                    refusals.add(new Refusal(Kind.BANNED, screen.render(ban.get(), now), null));
                    return refusals;
                }
            } catch (SQLException | RuntimeException e) {
                lastBanError = e.getMessage();
                if (rateLimited(lastBanWarning)) {
                    log.warn("Ban check for " + playerName + " failed: " + e.getMessage()
                            + (settings.allowOnError() ? " - letting them in, the servers check again on join." : ""));
                }
                if (!settings.allowOnError()) {
                    refusals.add(new Refusal(Kind.CHECK_FAILED,
                            Component.text("The login check is unavailable right now - please try again in a minute."), null));
                    return refusals;
                }
            }
        }
        MaintenanceLookup maintenanceLookup = maintenance;
        if (maintenanceLookup != null) {
            MaintenanceLookup.Maintenance state = maintenanceLookup.current(nowMillis);
            if (maintenanceLookup.lastError() != null && rateLimited(lastMaintenanceWarning)) {
                log.warn("Maintenance check failed: " + maintenanceLookup.lastError()
                        + " - letting players in, the servers check again on join.");
            }
            if (state.enabled() && !state.allows(playerName)) {
                String message = state.message() == null || state.message().isBlank() ? settings.maintenanceMessage() : state.message();
                refusals.add(new Refusal(Kind.MAINTENANCE, MiniMessage.miniMessage().deserialize(message), BYPASS_MAINTENANCE));
            }
        }
        WhitelistLookup whitelistLookup = whitelist;
        if (whitelistLookup != null) {
            boolean on = whitelistLookup.enabled(nowMillis);
            if (whitelistLookup.lastError() != null && rateLimited(lastWhitelistWarning)) {
                log.warn("Network whitelist check failed: " + whitelistLookup.lastError()
                        + " - letting players in, the servers check again on join.");
            }
            if (on && !whitelistLookup.listed(playerId, nowMillis)) {
                refusals.add(new Refusal(Kind.NOT_WHITELISTED, MiniMessage.miniMessage().deserialize(settings.notWhitelisted()),
                        BYPASS_NETWORK_WHITELIST));
            }
        }
        return refusals;
    }

    /** The first refusal {@code hasPermission} does not bypass - what the login is refused with, if anything. */
    public Optional<Refusal> refusalFor(List<Refusal> refusals, Predicate<String> hasPermission) {
        return refusals.stream().filter(refusal -> refusal.refuses(hasPermission)).findFirst();
    }

    /** Counts a refused login for the status command. */
    public void refused(Refusal refusal) {
        switch (refusal.kind()) {
            case BANNED, CHECK_FAILED -> refusedBanned.incrementAndGet();
            case MAINTENANCE -> refusedMaintenance.incrementAndGet();
            case NOT_WHITELISTED -> refusedWhitelist.incrementAndGet();
        }
    }

    /** During network maintenance: what the server list shows instead. Blocks briefly on the database (cached). */
    public Optional<PingOverride> ping() {
        MaintenanceLookup maintenanceLookup = maintenance;
        if (maintenanceLookup == null || (settings.maintenanceMotd().isBlank() && settings.maintenanceVersionText().isBlank())
                || !maintenanceLookup.current(System.currentTimeMillis()).enabled()) {
            return Optional.empty();
        }
        Component description = settings.maintenanceMotd().isBlank() ? null : MiniMessage.miniMessage().deserialize(settings.maintenanceMotd());
        String versionText = settings.maintenanceVersionText().isBlank() ? null : settings.maintenanceVersionText();
        return Optional.of(new PingOverride(description, versionText));
    }

    /** The body of the status command (MiniMessage), after a {@code title} line. */
    public Component status(String title) {
        long now = System.currentTimeMillis();
        String banState = bans == null ? "<red>off <gray>(see the proxy log)"
                : lastBanError == null ? "<green>active" : "<yellow>active, last error: <white>" + lastBanError;
        String maintenanceState;
        if (maintenance == null) {
            maintenanceState = "<red>off <gray>(see the proxy log)";
        } else {
            boolean on = maintenance.current(now).enabled();
            maintenanceState = maintenance.lastError() != null
                    ? "<yellow>active, last error: <white>" + maintenance.lastError()
                    : "<green>active <gray>- network maintenance: " + (on ? "<red>on" : "<green>off");
        }
        String whitelistState;
        if (whitelist == null) {
            whitelistState = "<red>off <gray>(see the proxy log)";
        } else {
            boolean on = whitelist.enabled(now);
            whitelistState = whitelist.lastError() != null
                    ? "<yellow>active, last error: <white>" + whitelist.lastError()
                    : "<green>active <gray>- network whitelist: " + (on ? "<red>on" : "<green>off");
        }
        return MiniMessage.miniMessage().deserialize(title
                + "\n<gray>Ban check: " + banState
                + "\n<gray>Maintenance check: " + maintenanceState
                + "\n<gray>Network whitelist check: " + whitelistState
                + "\n<gray>Refused logins since start: <white>" + refusedBanned.get() + "</white> banned, <white>"
                + refusedMaintenance.get() + "</white> during maintenance, <white>" + refusedWhitelist.get()
                + "</white> not whitelisted");
    }

    /** Closes the connection pool. */
    public void close() {
        bans = null;
        maintenance = null;
        whitelist = null;
        HikariDataSource pool = dataSource;
        dataSource = null;
        if (pool != null) {
            pool.close();
        }
    }

    private static boolean rateLimited(AtomicLong last) {
        long nowMillis = System.currentTimeMillis();
        return nowMillis - last.getAndSet(nowMillis) > WARNING_INTERVAL_MILLIS;
    }
}
