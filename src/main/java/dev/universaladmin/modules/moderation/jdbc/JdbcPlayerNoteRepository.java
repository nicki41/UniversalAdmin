package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.notes.PlayerNote;
import dev.universaladmin.modules.moderation.notes.PlayerNoteRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.StorageException;
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

/** JDBC {@link PlayerNoteRepository} over {@code ua_player_notes}; every call runs off the main thread. */
public final class JdbcPlayerNoteRepository implements PlayerNoteRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcPlayerNoteRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<PlayerNote>> findById(Long id) {
        return query("SELECT * FROM ua_player_notes WHERE id = ?", statement -> statement.setLong(1, id))
                .thenApply(notes -> notes.stream().findFirst());
    }

    @Override
    public CompletableFuture<List<PlayerNote>> findAll() {
        return query("SELECT * FROM ua_player_notes ORDER BY created_at DESC", statement -> { });
    }

    @Override
    public CompletableFuture<List<PlayerNote>> findByTarget(UUID targetId, int limit) {
        return query("SELECT * FROM ua_player_notes WHERE target_id = ? ORDER BY created_at DESC LIMIT ?", statement -> {
            statement.setString(1, targetId.toString());
            statement.setInt(2, limit);
        });
    }

    @Override
    public CompletableFuture<Integer> countByTarget(UUID targetId) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM ua_player_notes WHERE target_id = ?")) {
                statement.setString(1, targetId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to count notes of " + targetId, e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerNote> save(PlayerNote note) {
        if (note.id() != 0) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Notes are immutable - delete and re-add instead"));
        }
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO ua_player_notes (target_id, target_name, author_id, author_name, note, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, note.targetId().toString());
                statement.setString(2, note.targetName());
                statement.setString(3, note.authorId() == null ? null : note.authorId().toString());
                statement.setString(4, note.authorName());
                statement.setString(5, note.text());
                statement.setLong(6, note.createdAt().toEpochMilli());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? note.withId(keys.getLong(1)) : note;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to save a note about " + note.targetId(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteById(Long id) {
        return scheduler.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM ua_player_notes WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to delete note " + id, e);
            }
        });
    }

    private CompletableFuture<List<PlayerNote>> query(String sql, Binder binder) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<PlayerNote> notes = new ArrayList<>();
                    while (resultSet.next()) {
                        String authorId = resultSet.getString("author_id");
                        notes.add(new PlayerNote(
                                resultSet.getLong("id"),
                                UUID.fromString(resultSet.getString("target_id")),
                                resultSet.getString("target_name"),
                                authorId == null ? null : UUID.fromString(authorId),
                                resultSet.getString("author_name"),
                                resultSet.getString("note"),
                                Instant.ofEpochMilli(resultSet.getLong("created_at"))));
                    }
                    return notes;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to query notes", e);
            }
        });
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
