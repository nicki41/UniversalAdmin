package dev.universaladmin.modules.players.jdbc;

import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerProfileRepository;
import dev.universaladmin.modules.players.PlayerSearchQuery;
import dev.universaladmin.modules.players.PlayerSort;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

public final class JdbcPlayerProfileRepository implements PlayerProfileRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcPlayerProfileRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM player_profiles WHERE id = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new PlayerStorageException("Failed to load player profile " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> findAll() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_profiles");
                    ResultSet resultSet = statement.executeQuery()) {
                List<PlayerProfile> profiles = new ArrayList<>();
                while (resultSet.next()) {
                    profiles.add(map(resultSet));
                }
                return profiles;
            } catch (SQLException e) {
                throw new PlayerStorageException("Failed to load player profiles", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerProfile> save(PlayerProfile entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, entity.id().toString());
                statement.setString(2, entity.lastKnownName());
                statement.setLong(3, entity.firstJoin().toEpochMilli());
                statement.setLong(4, entity.lastSeen().toEpochMilli());
                statement.executeUpdate();
                return entity;
            } catch (SQLException e) {
                throw new PlayerStorageException("Failed to save player profile " + entity.id(), e);
            }
        });
    }

    // SQLite's upsert syntax (ON CONFLICT ... DO UPDATE) and MySQL/MariaDB's
    // (ON DUPLICATE KEY UPDATE) are not interchangeable; every repository
    // that upserts branches on the driver like this. See docs/architecture/storage.md.
    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO player_profiles (id, last_known_name, first_join, last_seen)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        last_known_name = excluded.last_known_name,
                        last_seen = excluded.last_seen
                    """;
        }
        return """
                INSERT INTO player_profiles (id, last_known_name, first_join, last_seen)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    last_known_name = VALUES(last_known_name),
                    last_seen = VALUES(last_seen)
                """;
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> search(PlayerSearchQuery query) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT * FROM player_profiles" + (hasNameFilter(query) ? " WHERE LOWER(last_known_name) LIKE ?" : "")
                    + " ORDER BY " + orderBy(query.sort()) + " LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (hasNameFilter(query)) {
                    statement.setString(index++, "%" + query.nameContains().toLowerCase(Locale.ROOT) + "%");
                }
                statement.setInt(index, query.limit());
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<PlayerProfile> profiles = new ArrayList<>();
                    while (resultSet.next()) {
                        profiles.add(map(resultSet));
                    }
                    return profiles;
                }
            } catch (SQLException e) {
                throw new PlayerStorageException("Failed to search player profiles", e);
            }
        });
    }

    private static boolean hasNameFilter(PlayerSearchQuery query) {
        return query.nameContains() != null && !query.nameContains().isBlank();
    }

    // No LIKE '%term%' can use the last_known_name index (see
    // PlayerProfileIndexMigration) - that index exists for ORDER BY, not for
    // this substring filter. Acceptable at the expected scale of a
    // player_profiles table (unique players ever joined, not events), see
    // docs/user/modules/players.md.
    private static String orderBy(PlayerSort sort) {
        return switch (sort) {
            case NAME_ASC -> "last_known_name ASC";
            case NAME_DESC -> "last_known_name DESC";
            case LAST_SEEN_DESC -> "last_seen DESC";
            case LAST_SEEN_ASC -> "last_seen ASC";
            case FIRST_JOIN_DESC -> "first_join DESC";
        };
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM player_profiles WHERE id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new PlayerStorageException("Failed to delete player profile " + id, e);
            }
        });
    }

    private PlayerProfile map(ResultSet resultSet) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("last_known_name"),
                Instant.ofEpochMilli(resultSet.getLong("first_join")),
                Instant.ofEpochMilli(resultSet.getLong("last_seen")));
    }
}
