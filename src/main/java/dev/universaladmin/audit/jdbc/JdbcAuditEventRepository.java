package dev.universaladmin.audit.jdbc;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.ActorType;
import dev.universaladmin.action.Source;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventRepository;
import dev.universaladmin.audit.AuditEventType;
import dev.universaladmin.audit.AuditPage;
import dev.universaladmin.audit.AuditPosition;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.core.id.Key;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/**
 * JDBC-backed {@link AuditEventRepository}. This is the only class allowed
 * to write SQL for audit events - {@link dev.universaladmin.audit.DefaultAuditService}
 * and everything above it depends only on the {@link AuditEventRepository}
 * interface.
 */
public final class JdbcAuditEventRepository implements AuditEventRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcAuditEventRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<AuditEvent>> findById(Long id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM audit_log WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to load audit event " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<AuditEvent>> findAll() {
        return recent(Integer.MAX_VALUE);
    }

    @Override
    public CompletableFuture<List<AuditEvent>> recent(int limit) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM audit_log ORDER BY occurred_at DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<AuditEvent> events = new ArrayList<>();
                    while (resultSet.next()) {
                        events.add(map(resultSet));
                    }
                    return events;
                }
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to load recent audit events", e);
            }
        });
    }

    @Override
    public CompletableFuture<AuditPage> query(AuditQuery query) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                long total = count(connection, query);
                List<AuditEvent> items = select(connection, query);
                return new AuditPage(items, query.page(), query.pageSize(), total);
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to query audit events", e);
            }
        });
    }

    @Override
    public CompletableFuture<AuditEvent> save(AuditEvent entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO audit_log
                                (event_type, actor_type, actor_id, actor_name, summary, target_id,
                                 occurred_at, action_id, module, target_type, target_display_name, source,
                                 success, reason, old_value, new_value, world, pos_x, pos_y, pos_z,
                                 metadata, correlation_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            Statement.RETURN_GENERATED_KEYS)) {
                bindInsert(statement, entity);
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    Long id = keys.next() ? keys.getLong(1) : null;
                    return new AuditEvent(
                            id, entity.timestamp(), entity.actor(), entity.type(), entity.module(), entity.target(),
                            entity.source(), entity.success(), entity.reason(), entity.oldValue(), entity.newValue(),
                            entity.world(), entity.position(), entity.summary(), entity.metadata(), entity.correlationId());
                }
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to save audit event", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteById(Long id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM audit_log WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to delete audit event " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> deleteOlderThan(Instant cutoff) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM audit_log WHERE occurred_at < ?")) {
                statement.setLong(1, cutoff.toEpochMilli());
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new AuditStorageException("Failed to delete audit events older than " + cutoff, e);
            }
        });
    }

    private void bindInsert(PreparedStatement statement, AuditEvent entity) throws SQLException {
        statement.setString(1, entity.type().toString());
        statement.setString(2, entity.actor().type().name());
        statement.setString(3, entity.actor().playerId() == null ? null : entity.actor().playerId().toString());
        statement.setString(4, entity.actor().displayName());
        statement.setString(5, entity.summary());
        statement.setString(6, entity.target() == null ? null : entity.target().id());
        statement.setLong(7, entity.timestamp().toEpochMilli());
        statement.setString(8, entity.type().toString());
        statement.setString(9, entity.module());
        statement.setString(10, entity.target() == null ? null : entity.target().type());
        statement.setString(11, entity.target() == null ? null : entity.target().displayName());
        statement.setString(12, entity.source().name());
        statement.setBoolean(13, entity.success());
        statement.setString(14, entity.reason());
        statement.setString(15, entity.oldValue());
        statement.setString(16, entity.newValue());
        statement.setString(17, entity.world());
        if (entity.position() == null) {
            statement.setNull(18, java.sql.Types.DOUBLE);
            statement.setNull(19, java.sql.Types.DOUBLE);
            statement.setNull(20, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(18, entity.position().x());
            statement.setDouble(19, entity.position().y());
            statement.setDouble(20, entity.position().z());
        }
        statement.setString(21, MetadataJson.encode(entity.metadata()));
        statement.setString(22, entity.correlationId());
    }

    private long count(Connection connection, AuditQuery query) throws SQLException {
        WhereClause where = WhereClause.from(query);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM audit_log" + where.sql())) {
            where.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private List<AuditEvent> select(Connection connection, AuditQuery query) throws SQLException {
        WhereClause where = WhereClause.from(query);
        String sql = "SELECT * FROM audit_log" + where.sql() + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int nextParam = where.bind(statement);
            statement.setInt(nextParam++, query.pageSize());
            statement.setInt(nextParam, query.page() * query.pageSize());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(map(resultSet));
                }
                return events;
            }
        }
    }

    private AuditEvent map(ResultSet resultSet) throws SQLException {
        String actorIdRaw = resultSet.getString("actor_id");
        // A rehydrated actor from history, never used to authorize anything
        // again - denyAll() is the safe default rather than reconstructing a
        // live PermissionEvaluator for a player who may be long offline.
        Actor actor = new Actor(
                ActorType.valueOf(resultSet.getString("actor_type")),
                actorIdRaw == null ? null : UUID.fromString(actorIdRaw),
                resultSet.getString("actor_name"),
                PermissionEvaluator.denyAll());

        String[] typeParts = resultSet.getString("event_type").split(":", 2);
        AuditEventType type = new AuditEventType(Key.of(typeParts[0], typeParts[1]));

        String targetId = resultSet.getString("target_id");
        String targetType = resultSet.getString("target_type");
        ActionTarget target = targetId == null
                ? null
                : ActionTarget.of(targetType == null ? "unknown" : targetType, targetId, resultSet.getString("target_display_name"));

        double posX = resultSet.getDouble("pos_x");
        boolean hasPosition = !resultSet.wasNull();
        AuditPosition position = null;
        if (hasPosition) {
            position = new AuditPosition(posX, resultSet.getDouble("pos_y"), resultSet.getDouble("pos_z"));
        }

        return new AuditEvent(
                resultSet.getLong("id"),
                Instant.ofEpochMilli(resultSet.getLong("occurred_at")),
                actor,
                type,
                resultSet.getString("module"),
                target,
                Source.valueOf(resultSet.getString("source")),
                resultSet.getBoolean("success"),
                resultSet.getString("reason"),
                resultSet.getString("old_value"),
                resultSet.getString("new_value"),
                resultSet.getString("world"),
                position,
                resultSet.getString("summary"),
                MetadataJson.decode(resultSet.getString("metadata")),
                resultSet.getString("correlation_id"));
    }

    /** Builds the shared {@code WHERE} fragment (and bound values) for {@link #count} and {@link #select}. */
    private record WhereClause(List<String> conditions, List<Object> values) {

        static WhereClause from(AuditQuery query) {
            List<String> conditions = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (query.actorId() != null) {
                conditions.add("actor_id = ?");
                values.add(query.actorId().toString());
            }
            if (query.targetId() != null) {
                conditions.add("target_id = ?");
                values.add(query.targetId());
            }
            if (query.type() != null) {
                conditions.add("event_type = ?");
                values.add(query.type().toString());
            }
            if (query.module() != null) {
                conditions.add("module = ?");
                values.add(query.module());
            }
            if (query.source() != null) {
                conditions.add("source = ?");
                values.add(query.source().name());
            }
            if (query.success() != null) {
                conditions.add("success = ?");
                values.add(query.success());
            }
            if (query.from() != null) {
                conditions.add("occurred_at >= ?");
                values.add(query.from().toEpochMilli());
            }
            if (query.to() != null) {
                conditions.add("occurred_at <= ?");
                values.add(query.to().toEpochMilli());
            }
            return new WhereClause(conditions, values);
        }

        String sql() {
            return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }

        /** Binds every value starting at parameter 1; returns the next free parameter index. */
        int bind(PreparedStatement statement) throws SQLException {
            int index = 1;
            for (Object value : values) {
                switch (value) {
                    case String s -> statement.setString(index, s);
                    case Boolean b -> statement.setBoolean(index, b);
                    case Long l -> statement.setLong(index, l);
                    default -> throw new IllegalStateException("Unexpected filter value type: " + value.getClass());
                }
                index++;
            }
            return index;
        }
    }
}
