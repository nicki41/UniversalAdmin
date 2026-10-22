package dev.universaladmin.proxy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Reads the network's maintenance from UniversalAdmin's {@code ua_maintenance}
 * table - the row {@code /admin maintenance network on} writes on the
 * servers. The answer is kept for a few seconds: every login and every
 * server-list ping asks, and the row changes rarely. Read-only.
 *
 * <p>Fails open: if the row cannot be read (database down, servers still on
 * a version without the table), the network counts as not in maintenance -
 * the servers check again on join - and the read is retried only after
 * {@link #RETRY_AFTER_FAILURE_MILLIS}, so a missing table does not fill the
 * log with driver warnings on every ping. {@link #lastError()} says why.
 */
public final class MaintenanceLookup {

    /** The network's maintenance; {@code message} is the MiniMessage override, blank = the configured default. */
    public record Maintenance(boolean enabled, String message, Set<String> allowedPlayers) {

        public static final Maintenance OFF = new Maintenance(false, null, Set.of());

        public boolean allows(String playerName) {
            return allowedPlayers.contains(playerName.toLowerCase(Locale.ROOT));
        }
    }

    public static final long RETRY_AFTER_FAILURE_MILLIS = 30_000;

    private static final String SQL = "SELECT enabled, message, allowed_players FROM ua_maintenance WHERE scope = 'network'";

    private final DataSource dataSource;
    private final long maxAgeMillis;

    private volatile Maintenance cached;
    private volatile long validUntil;
    private volatile String lastError;

    public MaintenanceLookup(DataSource dataSource, long maxAgeMillis) {
        this.dataSource = dataSource;
        this.maxAgeMillis = maxAgeMillis;
    }

    /** The network's maintenance, at most {@code maxAgeMillis} old - reads the database when the kept answer is older. */
    public Maintenance current(long nowMillis) {
        Maintenance known = cached;
        if (known != null && nowMillis < validUntil) {
            return known;
        }
        Maintenance loaded;
        try {
            loaded = load();
            lastError = null;
            validUntil = nowMillis + maxAgeMillis;
        } catch (SQLException | RuntimeException e) {
            loaded = Maintenance.OFF;
            lastError = e.getMessage();
            validUntil = nowMillis + Math.max(maxAgeMillis, RETRY_AFTER_FAILURE_MILLIS);
        }
        cached = loaded;
        return loaded;
    }

    /** Why the last read failed, {@code null} if it worked. */
    public String lastError() {
        return lastError;
    }

    private Maintenance load() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Maintenance.OFF;
            }
            String allowed = result.getString("allowed_players");
            Set<String> names = allowed == null || allowed.isBlank()
                    ? Set.of()
                    : Arrays.stream(allowed.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                            .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            return new Maintenance(result.getBoolean("enabled"), result.getString("message"), names);
        }
    }
}
