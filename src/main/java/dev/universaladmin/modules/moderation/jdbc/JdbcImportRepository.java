package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.importing.ImportRepository;
import dev.universaladmin.modules.moderation.importing.ImportedNote;
import dev.universaladmin.modules.moderation.importing.ImportedPunishment;
import dev.universaladmin.modules.moderation.notes.PlayerNote;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.StorageException;
import dev.universaladmin.storage.Transactions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/** {@link ImportRepository} over {@code punishments}, {@code ua_player_notes} and {@code ua_import_records}. */
public final class JdbcImportRepository implements ImportRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcImportRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Set<String>> importedIds(String source) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT source_id FROM ua_import_records WHERE source = ?")) {
                statement.setString(1, source);
                Set<String> ids = new HashSet<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(resultSet.getString(1));
                    }
                }
                return ids;
            } catch (SQLException e) {
                throw new StorageException("Failed to read the import records of " + source, e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> write(String source, List<ImportedPunishment> punishments, List<ImportedNote> notes) {
        long now = System.currentTimeMillis();
        return Transactions.run(dataSource, scheduler, connection -> {
            if (!punishments.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(JdbcPunishmentRepository.INSERT_SQL)) {
                    for (ImportedPunishment punishment : punishments) {
                        JdbcPunishmentRepository.bind(insert, punishment.toPunishment(source));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            if (!notes.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO ua_player_notes (target_id, target_name, author_id, author_name, note, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (ImportedNote imported : notes) {
                        PlayerNote note = imported.toNote();
                        insert.setString(1, note.targetId().toString());
                        insert.setString(2, note.targetName());
                        insert.setString(3, note.authorId() == null ? null : note.authorId().toString());
                        insert.setString(4, note.authorName());
                        insert.setString(5, note.text());
                        insert.setLong(6, note.createdAt().toEpochMilli());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            try (PreparedStatement record = connection.prepareStatement(
                    "INSERT INTO ua_import_records (source, source_id, kind, imported_at) VALUES (?, ?, ?, ?)")) {
                for (ImportedPunishment punishment : punishments) {
                    addRecord(record, source, punishment.sourceId(), "punishment", now);
                }
                for (ImportedNote note : notes) {
                    addRecord(record, source, note.sourceId(), "note", now);
                }
                record.executeBatch();
            }
            return punishments.size() + notes.size();
        });
    }

    private static void addRecord(PreparedStatement statement, String source, String sourceId, String kind, long now) throws SQLException {
        statement.setString(1, source);
        statement.setString(2, sourceId);
        statement.setString(3, kind);
        statement.setLong(4, now);
        statement.addBatch();
    }
}
