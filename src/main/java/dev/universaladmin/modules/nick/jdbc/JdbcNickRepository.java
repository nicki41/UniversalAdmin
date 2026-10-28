package dev.universaladmin.modules.nick.jdbc;

import dev.universaladmin.modules.nick.NickNameTakenException;
import dev.universaladmin.modules.nick.NickRecord;
import dev.universaladmin.modules.nick.NickRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

public final class JdbcNickRepository implements NickRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcNickRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<NickRecord>> findById(UUID id) {
        return scheduler.supplyAsync(() -> queryOne("SELECT * FROM ua_nicks WHERE player_id = ?", id.toString()));
    }

    @Override
    public CompletableFuture<Optional<NickRecord>> findByName(String name) {
        return scheduler.supplyAsync(() -> queryOne("SELECT * FROM ua_nicks WHERE name_key = ?", key(name)));
    }

    @Override
    public CompletableFuture<List<NickRecord>> findAll() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM ua_nicks");
                    ResultSet resultSet = statement.executeQuery()) {
                List<NickRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
                return records;
            } catch (SQLException e) {
                throw new NickStorageException("Failed to load the nicks", e);
            }
        });
    }

    /** Replaces the player's nick in one transaction: delete, then insert - the insert hits the unique name index. */
    @Override
    public CompletableFuture<NickRecord> save(NickRecord entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ua_nicks WHERE player_id = ?");
                        PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO ua_nicks (player_id, real_name, name, name_key, skin_owner, skin_value, skin_signature, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """)) {
                    delete.setString(1, entity.playerId().toString());
                    delete.executeUpdate();
                    NickRecord.Skin skin = entity.skin();
                    insert.setString(1, entity.playerId().toString());
                    insert.setString(2, entity.realName());
                    insert.setString(3, entity.name());
                    insert.setString(4, key(entity.name()));
                    insert.setString(5, skin == null ? null : skin.owner());
                    insert.setString(6, skin == null ? null : skin.value());
                    insert.setString(7, skin == null ? null : skin.signature());
                    insert.setLong(8, entity.createdAt().toEpochMilli());
                    insert.executeUpdate();
                    connection.commit();
                    return entity;
                } catch (SQLException e) {
                    connection.rollback();
                    if (uniqueViolation(e)) {
                        throw new NickNameTakenException(entity.name(), e);
                    }
                    throw e;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                throw new NickStorageException("Failed to save the nick of " + entity.playerId(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM ua_nicks WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new NickStorageException("Failed to delete the nick of " + id, e);
            }
        });
    }

    private Optional<NickRecord> queryOne(String sql, String parameter) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new NickStorageException("Failed to load a nick", e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** SQLite reports a constraint without an SQL state; MySQL/MariaDB with class 23. */
    private static boolean uniqueViolation(SQLException e) {
        String state = e.getSQLState();
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return e instanceof SQLIntegrityConstraintViolationException
                || state != null && state.startsWith("23")
                || message.contains("unique") || message.contains("duplicate");
    }

    private NickRecord map(ResultSet resultSet) throws SQLException {
        String value = resultSet.getString("skin_value");
        NickRecord.Skin skin = value == null ? null
                : new NickRecord.Skin(resultSet.getString("skin_owner"), value, resultSet.getString("skin_signature"));
        return new NickRecord(UUID.fromString(resultSet.getString("player_id")), resultSet.getString("real_name"),
                resultSet.getString("name"), skin, Instant.ofEpochMilli(resultSet.getLong("created_at")));
    }
}
