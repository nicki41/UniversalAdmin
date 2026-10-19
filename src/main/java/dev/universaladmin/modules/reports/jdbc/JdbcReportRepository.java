package dev.universaladmin.modules.reports.jdbc;

import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportRepository;
import dev.universaladmin.modules.reports.ReportStatus;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/** JDBC {@link ReportRepository} over {@code ua_reports}; every call runs off the main thread. */
public final class JdbcReportRepository implements ReportRepository {

    private static final String COLUMNS = "reporter_id, reporter_name, target_id, target_name, reason, status, "
            + "handler_id, handler_name, resolution, world, x, y, z, created_at, updated_at, server";

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcReportRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<Report>> findById(Long id) {
        return query("SELECT * FROM ua_reports WHERE id = ?", statement -> statement.setLong(1, id))
                .thenApply(reports -> reports.stream().findFirst());
    }

    @Override
    public CompletableFuture<List<Report>> findAll() {
        return query("SELECT * FROM ua_reports ORDER BY created_at DESC", statement -> { });
    }

    @Override
    public CompletableFuture<List<Report>> findByStatus(Set<ReportStatus> statuses, int limit) {
        String placeholders = statuses.stream().map(status -> "?").collect(Collectors.joining(", "));
        return query("SELECT * FROM ua_reports WHERE status IN (" + placeholders + ") ORDER BY created_at DESC LIMIT ?", statement -> {
            int index = 1;
            for (ReportStatus status : statuses) {
                statement.setString(index++, status.name());
            }
            statement.setInt(index, limit);
        });
    }

    @Override
    public CompletableFuture<List<Report>> findByTarget(UUID targetId, int limit) {
        return query("SELECT * FROM ua_reports WHERE target_id = ? ORDER BY created_at DESC LIMIT ?", statement -> {
            statement.setString(1, targetId.toString());
            statement.setInt(2, limit);
        });
    }

    @Override
    public CompletableFuture<Integer> countOpenByReporter(UUID reporterId) {
        return count("SELECT COUNT(*) FROM ua_reports WHERE reporter_id = ? AND status IN (?, ?)", statement -> {
            statement.setString(1, reporterId.toString());
            statement.setString(2, ReportStatus.OPEN.name());
            statement.setString(3, ReportStatus.CLAIMED.name());
        });
    }

    @Override
    public CompletableFuture<Integer> countByStatus(Set<ReportStatus> statuses) {
        String placeholders = statuses.stream().map(status -> "?").collect(Collectors.joining(", "));
        return count("SELECT COUNT(*) FROM ua_reports WHERE status IN (" + placeholders + ")", statement -> {
            int index = 1;
            for (ReportStatus status : statuses) {
                statement.setString(index++, status.name());
            }
        });
    }

    @Override
    public CompletableFuture<Report> save(Report report) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return report.id() == 0 ? insert(connection, report) : update(connection, report);
            } catch (SQLException e) {
                throw new StorageException("Failed to save report " + report.id(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteById(Long id) {
        return scheduler.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM ua_reports WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to delete report " + id, e);
            }
        });
    }

    private Report insert(Connection connection, Report report) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ua_reports (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, report);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? report.withId(keys.getLong(1)) : report;
            }
        }
    }

    private Report update(Connection connection, Report report) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE ua_reports SET reporter_id = ?, reporter_name = ?, target_id = ?, target_name = ?, reason = ?,
                    status = ?, handler_id = ?, handler_name = ?, resolution = ?, world = ?, x = ?, y = ?, z = ?,
                    created_at = ?, updated_at = ?, server = ?
                WHERE id = ?
                """)) {
            bind(statement, report);
            statement.setLong(17, report.id());
            statement.executeUpdate();
            return report;
        }
    }

    private static void bind(PreparedStatement statement, Report report) throws SQLException {
        statement.setString(1, report.reporterId().toString());
        statement.setString(2, report.reporterName());
        statement.setString(3, report.targetId().toString());
        statement.setString(4, report.targetName());
        statement.setString(5, report.reason());
        statement.setString(6, report.status().name());
        statement.setString(7, report.handlerId() == null ? null : report.handlerId().toString());
        statement.setString(8, report.handlerName());
        statement.setString(9, report.resolution());
        statement.setString(10, report.world());
        setNullableDouble(statement, 11, report.x());
        setNullableDouble(statement, 12, report.y());
        setNullableDouble(statement, 13, report.z());
        statement.setLong(14, report.createdAt().toEpochMilli());
        statement.setLong(15, report.updatedAt().toEpochMilli());
        statement.setString(16, report.server());
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    private CompletableFuture<List<Report>> query(String sql, Binder binder) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Report> reports = new ArrayList<>();
                    while (resultSet.next()) {
                        reports.add(map(resultSet));
                    }
                    return reports;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to query reports", e);
            }
        });
    }

    private CompletableFuture<Integer> count(String sql, Binder binder) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to count reports", e);
            }
        });
    }

    private static Report map(ResultSet resultSet) throws SQLException {
        String handlerId = resultSet.getString("handler_id");
        return new Report(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("reporter_id")),
                resultSet.getString("reporter_name"),
                UUID.fromString(resultSet.getString("target_id")),
                resultSet.getString("target_name"),
                resultSet.getString("reason"),
                ReportStatus.valueOf(resultSet.getString("status")),
                handlerId == null ? null : UUID.fromString(handlerId),
                resultSet.getString("handler_name"),
                resultSet.getString("resolution"),
                resultSet.getString("world"),
                nullableDouble(resultSet, "x"),
                nullableDouble(resultSet, "y"),
                nullableDouble(resultSet, "z"),
                resultSet.getString("server"),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                Instant.ofEpochMilli(resultSet.getLong("updated_at")));
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
