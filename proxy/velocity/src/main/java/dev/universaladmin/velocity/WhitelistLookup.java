package dev.universaladmin.velocity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Reads UniversalAdmin's network whitelist - the switch in
 * {@code ua_whitelist_state} (kept for a few seconds, like
 * {@link MaintenanceLookup}) and the player's row in {@code ua_whitelist},
 * scope {@code network}. Read-only, and fails open: if the database cannot
 * be read, the servers still check on join.
 */
final class WhitelistLookup {

    private static final String STATE_SQL = "SELECT enabled FROM ua_whitelist_state WHERE scope = 'network'";
    private static final String ENTRY_SQL = "SELECT expires_at FROM ua_whitelist WHERE scope = 'network' AND player_id = ?";

    private final DataSource dataSource;
    private final long maxAgeMillis;

    private volatile Boolean enabled;
    private volatile long validUntil;
    private volatile String lastError;

    WhitelistLookup(DataSource dataSource, long maxAgeMillis) {
        this.dataSource = dataSource;
        this.maxAgeMillis = maxAgeMillis;
    }

    /** Whether the network whitelist is on, at most {@code maxAgeMillis} old. */
    boolean enabled(long nowMillis) {
        Boolean known = enabled;
        if (known != null && nowMillis < validUntil) {
            return known;
        }
        boolean loaded;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(STATE_SQL);
                ResultSet result = statement.executeQuery()) {
            loaded = result.next() && result.getBoolean("enabled");
            lastError = null;
            validUntil = nowMillis + maxAgeMillis;
        } catch (SQLException | RuntimeException e) {
            loaded = false;
            lastError = e.getMessage();
            validUntil = nowMillis + Math.max(maxAgeMillis, MaintenanceLookup.RETRY_AFTER_FAILURE_MILLIS);
        }
        enabled = loaded;
        return loaded;
    }

    /** Whether {@code playerId} has a network whitelist entry that has not expired; {@code true} if that cannot be read. */
    boolean listed(UUID playerId, long nowMillis) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ENTRY_SQL)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                long expiresAt = result.getLong("expires_at");
                return result.wasNull() || expiresAt > nowMillis;
            }
        } catch (SQLException | RuntimeException e) {
            lastError = e.getMessage();
            return true;
        }
    }

    /** Why the last read failed, {@code null} if it worked. */
    String lastError() {
        return lastError;
    }
}
