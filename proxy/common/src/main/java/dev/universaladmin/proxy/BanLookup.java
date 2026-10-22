package dev.universaladmin.proxy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Looks up an active ban in UniversalAdmin's {@code punishments} table - the
 * same rule the servers apply on join: a ban or temporary ban for the
 * player's UUID, or an IP ban for their address, that is active and not yet
 * expired. Read-only.
 */
public final class BanLookup {

    /** What the ban screen needs. {@code expiresAt == null} means permanent. */
    public record Ban(String reason, Instant expiresAt) {
    }

    private static final String SQL = """
            SELECT reason, expires_at FROM punishments
            WHERE ((target_id = ? AND type IN ('BAN', 'TEMP_BAN')) OR (target_ip = ? AND type = 'IP_BAN'))
              AND active = TRUE AND (expires_at IS NULL OR expires_at > ?)
            ORDER BY created_at DESC LIMIT 1""";

    private final DataSource dataSource;

    public BanLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @param ip the player's address, {@code null} if unknown (then only the UUID is checked) */
    public Optional<Ban> activeBan(UUID playerId, String ip, Instant now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, playerId.toString());
            // A value no row has, so a missing address matches no IP ban.
            statement.setString(2, ip == null ? "-" : ip);
            statement.setLong(3, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long expires = result.getLong("expires_at");
                // wasNull() refers to the last column read - check it before reading the reason.
                Instant expiresAt = result.wasNull() ? null : Instant.ofEpochMilli(expires);
                return Optional.of(new Ban(result.getString("reason"), expiresAt));
            }
        }
    }
}
