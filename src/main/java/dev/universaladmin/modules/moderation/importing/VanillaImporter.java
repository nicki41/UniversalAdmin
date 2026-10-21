package dev.universaladmin.modules.moderation.importing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The server's own ban lists, {@code banned-players.json} and
 * {@code banned-ips.json} - also where EssentialsX keeps its bans.
 *
 * <p>The entries are <em>moved</em>: after a successful import they are
 * taken off the vanilla lists (through {@link BanListRemover}), because the
 * server would otherwise keep enforcing its own copy and {@code /unban} in
 * UniversalAdmin could not lift them.
 */
public final class VanillaImporter implements PunishmentImporter {

    /** Takes moved entries off the vanilla ban lists - on the main thread, through the Bukkit API. */
    @FunctionalInterface
    public interface BanListRemover {
        void remove(List<UUID> players, List<String> addresses) throws ImportException;
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    private final List<Path> folders;
    private final BanListRemover remover;

    /** @param folders where to look for the two files, in order (the server's working folder first) */
    public VanillaImporter(List<Path> folders, BanListRemover remover) {
        this.folders = List.copyOf(folders);
        this.remover = remover;
    }

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public String displayName() {
        return "Vanilla";
    }

    @Override
    public Optional<String> afterImportNoteKey() {
        return Optional.of("moderation.import.after.vanilla");
    }

    @Override
    public Optional<String> locate() {
        Optional<Path> players = file("banned-players.json");
        Optional<Path> ips = file("banned-ips.json");
        if (players.isEmpty() && ips.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = new ArrayList<>();
        players.ifPresent(path -> names.add(path.getFileName().toString()));
        ips.ifPresent(path -> names.add(path.getFileName().toString()));
        return Optional.of(String.join(", ", names));
    }

    @Override
    public void read(ImportSink sink) throws ImportException {
        Optional<Path> players = file("banned-players.json");
        Optional<Path> ips = file("banned-ips.json");
        if (players.isEmpty() && ips.isEmpty()) {
            throw new ImportException("moderation.import.error.not-found", displayName());
        }
        if (players.isPresent()) {
            for (JsonElement element : entries(players.get())) {
                if (!element.isJsonObject()) {
                    sink.skip(ImportSink.SkipReason.INVALID);
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                Optional<UUID> id = ImportTargets.uuid(text(entry, "uuid"));
                if (id.isEmpty()) {
                    sink.skip(ImportSink.SkipReason.NO_PLAYER);
                    continue;
                }
                sink.punishment(new ImportedPunishment("player:" + id.get(), PunishmentType.BAN, id.get(), text(entry, "name"),
                        null, null, source(entry), text(entry, "reason"), date(entry, "created"), date(entry, "expires"),
                        true, null, null, null));
            }
        }
        if (ips.isPresent()) {
            for (JsonElement element : entries(ips.get())) {
                if (!element.isJsonObject()) {
                    sink.skip(ImportSink.SkipReason.INVALID);
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String ip = text(entry, "ip");
                if (!ImportTargets.looksLikeIp(ip)) {
                    sink.skip(ImportSink.SkipReason.NO_PLAYER);
                    continue;
                }
                sink.punishment(new ImportedPunishment("ip:" + ip.trim(), PunishmentType.IP_BAN, ImportTargets.forIp(ip),
                        ImportTargets.nameForIp(ip), ip.trim(), null, source(entry), text(entry, "reason"),
                        date(entry, "created"), date(entry, "expires"), true, null, null, null));
            }
        }
    }

    @Override
    public void afterImport(Set<String> importedSourceIds) throws ImportException {
        List<UUID> players = new ArrayList<>();
        List<String> addresses = new ArrayList<>();
        for (String sourceId : importedSourceIds) {
            if (sourceId.startsWith("player:")) {
                ImportTargets.uuid(sourceId.substring("player:".length())).ifPresent(players::add);
            } else if (sourceId.startsWith("ip:")) {
                addresses.add(sourceId.substring("ip:".length()));
            }
        }
        if (!players.isEmpty() || !addresses.isEmpty()) {
            remover.remove(players, addresses);
        }
    }

    private Optional<Path> file(String name) {
        return folders.stream().map(folder -> folder.resolve(name)).filter(Files::isRegularFile).findFirst();
    }

    private JsonArray entries(Path file) throws ImportException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        } catch (IOException | JsonParseException e) {
            throw new ImportException("moderation.import.error.read", e, file.getFileName().toString(), e.getMessage());
        }
    }

    private static String text(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** "Server" and "Console" are the vanilla console; anything else is the operator's name. */
    private static String source(JsonObject entry) {
        String source = text(entry, "source");
        return source == null || source.equalsIgnoreCase("Server") || source.equalsIgnoreCase("Console") ? "CONSOLE" : source;
    }

    /** Vanilla's "2024-05-01 18:30:00 +0200"; "forever" and anything unreadable read as none. */
    private static Instant date(JsonObject entry, String key) {
        String raw = text(entry, key);
        if (raw == null || raw.equalsIgnoreCase("forever")) {
            return null;
        }
        try {
            return ZonedDateTime.parse(raw.trim(), DATE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
