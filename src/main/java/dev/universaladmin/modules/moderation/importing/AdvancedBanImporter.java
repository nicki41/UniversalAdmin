package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * AdvancedBan: every entry of {@code PunishmentHistory} (which holds all
 * punishments, current and past); an entry counts as in force if it is
 * still in {@code Punishments}. Reads MySQL ({@code UseMySQL: true} and
 * {@code MySQL.yml}) or the local HSQLDB files in {@code data/}, the latter
 * with the HSQLDB driver inside AdvancedBan's jar.
 *
 * <p>AdvancedBan stores UUIDs without dashes, the address in the uuid
 * column for IP bans, and - with its UUID fetcher switched off - the
 * player name. Notes become staff notes. It does not record who lifted a
 * punishment, so lifted and expired entries both import as no longer active.
 */
public final class AdvancedBanImporter implements PunishmentImporter {

    private final Path pluginsFolder;
    private final boolean offlineMode;
    private final NoteText noteText;

    /** @param offlineMode whether this server runs in offline mode - name-only entries then get the offline UUID */
    public AdvancedBanImporter(Path pluginsFolder, boolean offlineMode, NoteText noteText) {
        this.pluginsFolder = pluginsFolder;
        this.offlineMode = offlineMode;
        this.noteText = noteText;
    }

    @Override
    public String id() {
        return "advancedban";
    }

    @Override
    public String displayName() {
        return "AdvancedBan";
    }

    @Override
    public Optional<String> afterImportNoteKey() {
        return Optional.of("moderation.import.after.remove-plugin");
    }

    private Path folder() {
        return pluginsFolder.resolve("AdvancedBan");
    }

    @Override
    public Optional<String> locate() {
        if (!Files.isDirectory(folder())) {
            return Optional.empty();
        }
        if (useMySql()) {
            YamlConfiguration mysql = mysqlSettings();
            return Optional.of("MySQL " + mysql.getString("MySQL.DB-Name", "?") + "@" + mysql.getString("MySQL.IP", "?")
                    + ":" + mysql.getInt("MySQL.Port", 3306));
        }
        Path script = folder().resolve("data").resolve("storage.script");
        return Files.isRegularFile(script)
                ? Optional.of("HSQLDB " + pluginsFolder.getParent().relativize(folder().resolve("data").toAbsolutePath()))
                : Optional.empty();
    }

    @Override
    public void read(ImportSink sink) throws ImportException {
        if (locate().isEmpty()) {
            throw new ImportException("moderation.import.error.not-found", displayName());
        }
        try (Connection connection = open()) {
            Set<String> active = activeKeys(connection);
            List<Row> rows = new ArrayList<>();
            Map<String, UUID> uuidsByName = new HashMap<>();
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM PunishmentHistory ORDER BY id")) {
                SourceRow row = new SourceRow(resultSet);
                while (resultSet.next()) {
                    Row read = new Row(row.number("id", -1), row.string("name"), row.string("uuid"), row.string("reason"),
                            row.string("operator"), row.string("punishmentType"), row.number("start", 0), row.number("end", -1));
                    rows.add(read);
                    if (read.name() != null) {
                        ImportTargets.uuid(read.uuid()).ifPresent(id -> uuidsByName.put(read.name().toLowerCase(Locale.ROOT), id));
                    }
                }
            }
            // Rows are kept in memory only as far as AdvancedBan's own history
            // goes: IP bans need the name -> UUID map built from all rows first.
            for (Row row : rows) {
                emit(row, active, uuidsByName, sink);
            }
        } catch (SQLException e) {
            throw new ImportException("moderation.import.error.read", e, displayName(), e.getMessage());
        }
    }

    private void emit(Row row, Set<String> active, Map<String, UUID> uuidsByName, ImportSink sink) {
        if (row.id() < 0 || row.type() == null) {
            sink.skip(ImportSink.SkipReason.INVALID);
            return;
        }
        String type = row.type().trim().toUpperCase(Locale.ROOT);
        Instant created = row.start() > 0 ? Instant.ofEpochMilli(row.start()) : null;
        Instant expires = row.end() > 0 ? Instant.ofEpochMilli(row.end()) : null;
        boolean inForce = active.contains(key(row.uuid(), row.type(), row.start()));
        String sourceId = "history:" + row.id();

        if (type.equals("IP_BAN") || type.equals("TEMP_IP_BAN")) {
            String ip = row.uuid();
            if (!ImportTargets.looksLikeIp(ip)) {
                sink.skip(ImportSink.SkipReason.NO_PLAYER);
                return;
            }
            UUID player = row.name() == null ? null : uuidsByName.get(row.name().toLowerCase(Locale.ROOT));
            sink.punishment(new ImportedPunishment(sourceId, PunishmentType.IP_BAN,
                    player != null ? player : ImportTargets.forIp(ip),
                    player != null ? row.name() : ImportTargets.nameForIp(ip), ip.trim(),
                    null, row.operator(), row.reason(), created, expires, inForce, null, null, null));
            return;
        }

        Optional<UUID> target = target(row);
        if (target.isEmpty()) {
            sink.skip(ImportSink.SkipReason.NO_PLAYER);
            return;
        }
        if (type.equals("NOTE")) {
            sink.note(new ImportedNote(sourceId, target.get(), row.name(), row.operator(),
                    noteText.format("moderation.import.note.advancedban", row.reason() == null ? "" : row.reason()), created));
            return;
        }
        PunishmentType mapped = switch (type) {
            case "BAN", "TEMP_BAN" -> PunishmentType.BAN;
            case "MUTE", "TEMP_MUTE" -> PunishmentType.MUTE;
            case "WARNING", "TEMP_WARNING" -> PunishmentType.WARN;
            case "KICK" -> PunishmentType.KICK;
            default -> null;
        };
        if (mapped == null) {
            sink.skip(ImportSink.SkipReason.INVALID);
            return;
        }
        sink.punishment(new ImportedPunishment(sourceId, mapped, target.get(), row.name(), null, null, row.operator(),
                row.reason(), created, mapped == PunishmentType.KICK ? null : expires, inForce, null, null, null));
    }

    /** The player's UUID - from the uuid column, or (offline mode, UUID fetcher off) derived from the name stored there. */
    private Optional<UUID> target(Row row) {
        Optional<UUID> parsed = ImportTargets.uuid(row.uuid());
        if (parsed.isPresent() || !offlineMode) {
            return parsed;
        }
        String name = row.name() != null ? row.name() : row.uuid();
        if (name == null || name.isBlank() || ImportTargets.looksLikeIp(name)) {
            return Optional.empty();
        }
        return Optional.of(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name.trim()).getBytes(StandardCharsets.UTF_8)));
    }

    /** Entries of {@code Punishments} (the ones in force), keyed like AdvancedBan itself matches them: uuid, type, start. */
    private Set<String> activeKeys(Connection connection) throws SQLException {
        Set<String> keys = new HashSet<>();
        long now = System.currentTimeMillis();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT * FROM Punishments")) {
            SourceRow row = new SourceRow(resultSet);
            while (resultSet.next()) {
                long end = row.number("end", -1);
                if (end != -1 && end <= now) {
                    continue; // ran out; AdvancedBan deletes these lazily
                }
                keys.add(key(row.string("uuid"), row.string("punishmentType"), row.number("start", 0)));
            }
        }
        return keys;
    }

    private static String key(String uuid, String type, long start) {
        return (uuid == null ? "" : uuid.toLowerCase(Locale.ROOT)) + "|" + (type == null ? "" : type.toUpperCase(Locale.ROOT)) + "|" + start;
    }

    private Connection open() throws ImportException {
        if (useMySql()) {
            YamlConfiguration mysql = mysqlSettings();
            return ForeignDatabase.mysql(displayName(), mysql.getString("MySQL.IP", "localhost"), mysql.getInt("MySQL.Port", 3306),
                    mysql.getString("MySQL.DB-Name", "Reloaded"), mysql.getString("MySQL.Username", "root"),
                    mysql.getString("MySQL.Password", ""));
        }
        Properties properties = new Properties();
        properties.setProperty("user", "SA");
        properties.setProperty("password", "");
        String url = "jdbc:hsqldb:file:" + folder().resolve("data").resolve("storage").toAbsolutePath()
                + ";hsqldb.lock_file=false;readonly=true;ifexists=true;shutdown=true";
        List<Path> driverSearch = new ArrayList<>(pluginJars());
        driverSearch.add(folder());
        return ForeignDatabase.withBorrowedDriver(displayName(), "HSQLDB", driverSearch, "hsqldb/jdbc/JDBCDriver.class", url, properties);
    }

    private boolean useMySql() {
        Path config = folder().resolve("config.yml");
        return Files.isRegularFile(config) && YamlConfiguration.loadConfiguration(config.toFile()).getBoolean("UseMySQL", false);
    }

    /** AdvancedBan reads MySQL.yml, or its config.yml if there is none. */
    private YamlConfiguration mysqlSettings() {
        Path mysql = folder().resolve("MySQL.yml");
        return YamlConfiguration.loadConfiguration((Files.isRegularFile(mysql) ? mysql : folder().resolve("config.yml")).toFile());
    }

    private List<Path> pluginJars() {
        try (Stream<Path> files = Files.list(pluginsFolder)) {
            return files.filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.startsWith("advancedban") && name.endsWith(".jar");
            }).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private record Row(long id, String name, String uuid, String reason, String operator, String type, long start, long end) {
    }
}
