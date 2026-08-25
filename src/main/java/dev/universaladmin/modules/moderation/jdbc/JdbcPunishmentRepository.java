package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentQuery;
import dev.universaladmin.modules.moderation.PunishmentRepository;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.Transactions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

public final class JdbcPunishmentRepository implements PunishmentRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcPunishmentRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findById(Long id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to load punishment " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> findAll() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments ORDER BY created_at DESC");
                    ResultSet resultSet = statement.executeQuery()) {
                return mapAll(resultSet);
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to load punishments", e);
            }
        });
    }

    @Override
    public CompletableFuture<Punishment> save(Punishment entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return entity.id() == 0 ? insert(connection, entity) : update(connection, entity);
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to save punishment " + entity.id(), e);
            }
        });
    }

    private Punishment insert(Connection connection, Punishment entity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO punishments
                    (type, target_id, target_last_known_name, target_ip, actor_id, actor_name, reason,
                     created_at, expires_at, active, revoked_at, revoked_by, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, entity);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? entity.withId(keys.getLong(1)) : entity;
            }
        }
    }

    private Punishment update(Connection connection, Punishment entity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE punishments SET
                    type = ?, target_id = ?, target_last_known_name = ?, target_ip = ?, actor_id = ?,
                    actor_name = ?, reason = ?, created_at = ?, expires_at = ?, active = ?, revoked_at = ?,
                    revoked_by = ?, metadata = ?
                WHERE id = ?
                """)) {
            bind(statement, entity);
            statement.setLong(14, entity.id());
            statement.executeUpdate();
            return entity;
        }
    }

    private void bind(PreparedStatement statement, Punishment entity) throws SQLException {
        statement.setString(1, entity.type().name());
        statement.setString(2, entity.targetId().toString());
        statement.setString(3, entity.targetLastKnownName());
        statement.setString(4, entity.targetIp());
        statement.setString(5, entity.actorId() == null ? null : entity.actorId().toString());
        statement.setString(6, entity.actorName());
        statement.setString(7, entity.reason());
        statement.setLong(8, entity.createdAt().toEpochMilli());
        if (entity.expiresAt() == null) {
            statement.setNull(9, java.sql.Types.BIGINT);
        } else {
            statement.setLong(9, entity.expiresAt().toEpochMilli());
        }
        statement.setBoolean(10, entity.active());
        if (entity.revokedAt() == null) {
            statement.setNull(11, java.sql.Types.BIGINT);
        } else {
            statement.setLong(11, entity.revokedAt().toEpochMilli());
        }
        statement.setString(12, entity.revokedBy());
        statement.setString(13, MetadataJson.encode(entity.metadata()));
    }

    @Override
    public CompletableFuture<Void> deleteById(Long id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM punishments WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to delete punishment " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveBan(UUID targetId, Instant now) {
        return findActiveOne(Set.of(PunishmentType.BAN, PunishmentType.TEMP_BAN), "target_id", targetId.toString(), now);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip, Instant now) {
        return findActiveOne(Set.of(PunishmentType.IP_BAN), "target_ip", ip, now);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveMute(UUID targetId, Instant now) {
        return findActiveOne(Set.of(PunishmentType.MUTE, PunishmentType.TEMP_MUTE), "target_id", targetId.toString(), now);
    }

    private CompletableFuture<Optional<Punishment>> findActiveOne(
            Set<PunishmentType> types, String matchColumn, String matchValue, Instant now) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT * FROM punishments WHERE " + matchColumn + " = ? AND type IN (" + placeholders(types.size())
                    + ") AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) ORDER BY created_at DESC LIMIT 1";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, matchValue);
                for (PunishmentType type : types) {
                    statement.setString(index++, type.name());
                }
                statement.setLong(index, now.toEpochMilli());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to look up active punishment", e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveFreeze(UUID targetId, Instant now) {
        return findActiveOne(Set.of(PunishmentType.FREEZE), "target_id", targetId.toString(), now);
    }

    @Override
    public CompletableFuture<List<Punishment>> findByQuery(PunishmentQuery query) {
        return scheduler.supplyAsync(() -> {
            List<String> conditions = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (query.targetId() != null) {
                conditions.add("target_id = ?");
                values.add(query.targetId().toString());
            }
            if (query.types() != null && !query.types().isEmpty()) {
                conditions.add("type IN (" + placeholders(query.types().size()) + ")");
                query.types().forEach(type -> values.add(type.name()));
            }
            Instant now = Instant.now();
            if (Boolean.TRUE.equals(query.active())) {
                conditions.add("active = TRUE AND (expires_at IS NULL OR expires_at > ?)");
                values.add(now.toEpochMilli());
            } else if (Boolean.FALSE.equals(query.active())) {
                conditions.add("(active = FALSE OR (expires_at IS NOT NULL AND expires_at <= ?))");
                values.add(now.toEpochMilli());
            }
            String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
            String sql = "SELECT * FROM punishments" + where + " ORDER BY created_at DESC LIMIT ?";

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (Object value : values) {
                    if (value instanceof Long l) {
                        statement.setLong(index++, l);
                    } else {
                        statement.setString(index++, (String) value);
                    }
                }
                statement.setInt(index, query.limit());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return mapAll(resultSet);
                }
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to query punishments", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> revokeActiveByTarget(
            UUID targetId, Set<PunishmentType> types, Instant revokedAt, String revokedBy) {
        return Transactions.run(dataSource, scheduler, connection -> {
            List<Punishment> candidates = new ArrayList<>();
            String selectSql = "SELECT * FROM punishments WHERE target_id = ? AND type IN (" + placeholders(types.size())
                    + ") AND active = TRUE";
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                int index = 1;
                select.setString(index++, targetId.toString());
                for (PunishmentType type : types) {
                    select.setString(index++, type.name());
                }
                try (ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        candidates.add(map(resultSet));
                    }
                }
            }
            List<Punishment> revoked = new ArrayList<>();
            for (Punishment candidate : candidates) {
                if (revokeOne(connection, candidate.id(), revokedAt, revokedBy)) {
                    revoked.add(candidate.revoke(revokedAt, revokedBy));
                }
            }
            return revoked;
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revokeById(long id, Instant revokedAt, String revokedBy) {
        return Transactions.run(dataSource, scheduler, connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
                select.setLong(1, id);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.<Punishment>empty();
                    }
                    Punishment candidate = map(resultSet);
                    return revokeOne(connection, id, revokedAt, revokedBy)
                            ? Optional.of(candidate.revoke(revokedAt, revokedBy))
                            : Optional.<Punishment>empty();
                }
            }
        });
    }

    /** {@code WHERE id = ? AND active = TRUE} - only ever flips a row that was still active, see {@link PunishmentRepository}. */
    private boolean revokeOne(Connection connection, long id, Instant revokedAt, String revokedBy) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE punishments SET active = FALSE, revoked_at = ?, revoked_by = ? WHERE id = ? AND active = TRUE")) {
            update.setLong(1, revokedAt.toEpochMilli());
            update.setString(2, revokedBy);
            update.setLong(3, id);
            return update.executeUpdate() == 1;
        }
    }

    @Override
    public CompletableFuture<Integer> expireOverdue(Instant now) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE punishments SET active = FALSE WHERE active = TRUE AND expires_at IS NOT NULL AND expires_at <= ?")) {
                statement.setLong(1, now.toEpochMilli());
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new PunishmentStorageException("Failed to expire overdue punishments", e);
            }
        });
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private List<Punishment> mapAll(ResultSet resultSet) throws SQLException {
        List<Punishment> punishments = new ArrayList<>();
        while (resultSet.next()) {
            punishments.add(map(resultSet));
        }
        return punishments;
    }

    private Punishment map(ResultSet resultSet) throws SQLException {
        long expiresAt = resultSet.getLong("expires_at");
        boolean hasExpiry = !resultSet.wasNull();
        long revokedAt = resultSet.getLong("revoked_at");
        boolean hasRevokedAt = !resultSet.wasNull();
        return new Punishment(
                resultSet.getLong("id"),
                PunishmentType.valueOf(resultSet.getString("type")),
                UUID.fromString(resultSet.getString("target_id")),
                resultSet.getString("target_last_known_name"),
                resultSet.getString("target_ip"),
                resultSet.getString("actor_id") == null ? null : UUID.fromString(resultSet.getString("actor_id")),
                resultSet.getString("actor_name"),
                resultSet.getString("reason"),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                hasExpiry ? Instant.ofEpochMilli(expiresAt) : null,
                resultSet.getBoolean("active"),
                hasRevokedAt ? Instant.ofEpochMilli(revokedAt) : null,
                resultSet.getString("revoked_by"),
                MetadataJson.decode(resultSet.getString("metadata")));
    }
}
