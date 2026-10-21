package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * LiteBans: bans (including IP bans), mutes, warnings and kicks, from the
 * database configured in {@code plugins/LiteBans/config.yml} - MySQL/MariaDB,
 * or the embedded H2 file (read with LiteBans' own H2 driver).
 *
 * <p>Names come from LiteBans' {@code history} table (the name a UUID was
 * last seen with). Wildcard IP bans and IP-only mutes have no equivalent and
 * are skipped; revoked entries keep who lifted them.
 */
public final class LiteBansImporter implements PunishmentImporter {

    private final Path pluginsFolder;
    private final Path librariesFolder;

    /**
     * @param pluginsFolder   the server's {@code plugins} folder
     * @param librariesFolder the server's {@code libraries} folder (Paper puts plugin libraries there)
     */
    public LiteBansImporter(Path pluginsFolder, Path librariesFolder) {
        this.pluginsFolder = pluginsFolder;
        this.librariesFolder = librariesFolder;
    }

    @Override
    public String id() {
        return "litebans";
    }

    @Override
    public String displayName() {
        return "LiteBans";
    }

    @Override
    public Optional<String> afterImportNoteKey() {
        return Optional.of("moderation.import.after.remove-plugin");
    }

    @Override
    public Optional<String> locate() {
        Optional<Settings> settings = settings();
        if (settings.isEmpty()) {
            return Optional.empty();
        }
        Settings found = settings.get();
        return switch (found.driver()) {
            case "mysql", "mariadb" -> Optional.of((found.driver().equals("mysql") ? "MySQL " : "MariaDB ")
                    + found.database() + "@" + found.address());
            case "h2" -> h2File().map(file -> "H2 " + pluginsFolder.getParent().relativize(file.toAbsolutePath()));
            case "sqlite" -> sqliteFile().map(file -> "SQLite " + pluginsFolder.getParent().relativize(file.toAbsolutePath()));
            default -> Optional.of(found.driver());
        };
    }

    @Override
    public void read(ImportSink sink) throws ImportException {
        Settings settings = settings().orElseThrow(() -> new ImportException("moderation.import.error.not-found", displayName()));
        try (Connection connection = open(settings)) {
            String prefix = settings.tablePrefix();
            Map<UUID, String> names = names(connection, prefix);
            readTable(connection, prefix + "bans", "ban", sink, names);
            readTable(connection, prefix + "mutes", "mute", sink, names);
            readTable(connection, prefix + "warnings", "warning", sink, names);
            readTable(connection, prefix + "kicks", "kick", sink, names);
        } catch (SQLException e) {
            throw new ImportException("moderation.import.error.read", e, displayName(), e.getMessage());
        }
    }

    private Connection open(Settings settings) throws ImportException {
        return switch (settings.driver()) {
            case "mysql", "mariadb" -> {
                String[] hostPort = settings.address().split(":", 2);
                int port = hostPort.length > 1 ? parsePort(hostPort[1]) : 3306;
                yield ForeignDatabase.mysql(displayName(), hostPort[0], port, settings.database(), settings.username(), settings.password());
            }
            case "h2" -> {
                Path file = h2File().orElseThrow(() -> new ImportException("moderation.import.error.not-found", displayName()));
                String name = file.toAbsolutePath().toString();
                name = name.substring(0, name.length() - (name.endsWith(".mv.db") ? ".mv.db".length() : ".h2.db".length()));
                Properties properties = new Properties();
                properties.setProperty("user", settings.username() == null ? "" : settings.username());
                properties.setProperty("password", settings.password() == null ? "" : settings.password());
                // Read-only and without H2's file lock: LiteBans may still have
                // the file open in this server.
                String url = "jdbc:h2:" + name + ";MODE=MySQL;IFEXISTS=TRUE;ACCESS_MODE_DATA=r;FILE_LOCK=NO";
                List<Path> driverSearch = new java.util.ArrayList<>(List.of(pluginsFolder.resolve("LiteBans")));
                pluginJar().ifPresent(driverSearch::add);
                driverSearch.add(librariesFolder.resolve("com").resolve("h2database"));
                yield ForeignDatabase.withBorrowedDriver(displayName(), "H2", driverSearch, "org/h2/Driver.class", url, properties);
            }
            case "sqlite" -> ForeignDatabase.sqlite(displayName(),
                    sqliteFile().orElseThrow(() -> new ImportException("moderation.import.error.not-found", displayName())));
            default -> throw new ImportException("moderation.import.error.unsupported-database", displayName(), settings.driver());
        };
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    /** UUID -> the name LiteBans saw it with last. */
    private Map<UUID, String> names(Connection connection, String prefix) {
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Long> seenAt = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT * FROM " + prefix + "history")) {
            SourceRow row = new SourceRow(resultSet);
            while (resultSet.next()) {
                Optional<UUID> id = ImportTargets.uuid(row.string("uuid"));
                String name = row.string("name");
                if (id.isEmpty() || name == null || name.isBlank()) {
                    continue;
                }
                Instant date = row.instant("date");
                long at = date == null ? 0 : date.toEpochMilli();
                if (at >= seenAt.getOrDefault(id.get(), Long.MIN_VALUE)) {
                    seenAt.put(id.get(), at);
                    names.put(id.get(), name);
                }
            }
        } catch (SQLException e) {
            // Older LiteBans versions or a cleared history: names then fall
            // back to UniversalAdmin's own player list.
        }
        return names;
    }

    private void readTable(Connection connection, String table, String kind, ImportSink sink, Map<UUID, String> names)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY id");
                ResultSet resultSet = statement.executeQuery()) {
            SourceRow row = new SourceRow(resultSet);
            while (resultSet.next()) {
                read(row, kind, sink, names);
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                return; // e.g. no kicks table in an old LiteBans version
            }
            throw e;
        }
    }

    private void read(SourceRow row, String kind, ImportSink sink, Map<UUID, String> names) throws SQLException {
        long id = row.number("id", -1);
        if (id < 0) {
            sink.skip(ImportSink.SkipReason.INVALID);
            return;
        }
        Optional<UUID> player = ImportTargets.uuid(row.string("uuid"));
        String ip = row.string("ip");
        boolean ipBan = row.flag("ipban", false);
        if (ipBan && row.flag("ipban_wildcard", false)) {
            sink.skip(ImportSink.SkipReason.UNSUPPORTED);
            return;
        }
        PunishmentType type;
        UUID targetId;
        String targetName;
        String targetIp = null;
        switch (kind) {
            case "ban" -> {
                if (ipBan) {
                    if (ip == null || ip.isBlank() || ip.startsWith("#")) {
                        sink.skip(ImportSink.SkipReason.NO_PLAYER);
                        return;
                    }
                    type = PunishmentType.IP_BAN;
                    targetIp = ip.trim();
                    targetId = player.orElseGet(() -> ImportTargets.forIp(ip));
                    targetName = player.map(names::get).orElseGet(() -> ImportTargets.nameForIp(ip));
                } else {
                    if (player.isEmpty()) {
                        sink.skip(ImportSink.SkipReason.NO_PLAYER);
                        return;
                    }
                    type = PunishmentType.BAN;
                    targetId = player.get();
                    targetName = names.get(targetId);
                }
            }
            case "mute", "warning", "kick" -> {
                if (player.isEmpty()) {
                    // An IP-only mute: UniversalAdmin mutes players, not addresses.
                    sink.skip(ipBan ? ImportSink.SkipReason.UNSUPPORTED : ImportSink.SkipReason.NO_PLAYER);
                    return;
                }
                type = switch (kind) {
                    case "mute" -> PunishmentType.MUTE;
                    case "warning" -> PunishmentType.WARN;
                    default -> PunishmentType.KICK;
                };
                targetId = player.get();
                targetName = names.get(targetId);
            }
            default -> throw new IllegalArgumentException(kind);
        }

        Instant until = row.instant("until");
        Instant created = row.instant("time");
        boolean active = row.flag("active", true);
        String removedBy = row.string("removed_by_name");
        Instant removedAt = row.instant("removed_by_date");
        // LiteBans marks punishments that simply ran out as removed by
        // "#expired" - those are expired, not lifted by someone.
        boolean lifted = !active && removedBy != null && !removedBy.isBlank() && !removedBy.startsWith("#");
        Optional<UUID> actor = ImportTargets.uuid(row.string("banned_by_uuid"));
        sink.punishment(new ImportedPunishment(
                kind + ":" + id, type, targetId, targetName, targetIp,
                actor.orElse(null), row.string("banned_by_name"), row.string("reason"),
                created, type == PunishmentType.KICK ? null : until, active,
                lifted ? (removedAt == null ? created : removedAt) : null,
                lifted ? removedBy : null,
                row.string("server_origin")));
    }

    private static boolean isMissingTable(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("doesn't exist") || message.contains("not found") || message.contains("no such table");
    }

    private Optional<Settings> settings() {
        Path config = pluginsFolder.resolve("LiteBans").resolve("config.yml");
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config.toFile());
        ConfigurationSection sql = yaml.getConfigurationSection("sql");
        if (sql == null) {
            return Optional.empty();
        }
        return Optional.of(new Settings(
                sql.getString("driver", "H2").trim().toLowerCase(Locale.ROOT),
                sql.getString("address", "localhost:3306"),
                sql.getString("database", "litebans"),
                sql.getString("username", ""),
                sql.getString("password", ""),
                sql.getString("table_prefix", yaml.getString("table_prefix", "litebans_"))));
    }

    private Optional<Path> h2File() {
        Path folder = pluginsFolder.resolve("LiteBans");
        for (String name : List.of("litebans.mv.db", "litebans.h2.db")) {
            if (Files.isRegularFile(folder.resolve(name))) {
                return Optional.of(folder.resolve(name));
            }
        }
        return Optional.empty();
    }

    private Optional<Path> sqliteFile() {
        Path folder = pluginsFolder.resolve("LiteBans");
        for (String name : List.of("litebans.db", "litebans.sqlite")) {
            if (Files.isRegularFile(folder.resolve(name))) {
                return Optional.of(folder.resolve(name));
            }
        }
        return Optional.empty();
    }

    private Optional<Path> pluginJar() {
        try (var files = Files.list(pluginsFolder)) {
            return files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("litebans")
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).findFirst();
        } catch (java.io.IOException e) {
            return Optional.empty();
        }
    }

    private record Settings(String driver, String address, String database, String username, String password, String tablePrefix) {
    }
}
