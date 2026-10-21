package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * EssentialsX: mutes from {@code plugins/Essentials/userdata/<uuid>.yml}.
 * EssentialsX keeps its bans in the vanilla ban lists - those come from
 * {@link VanillaImporter}.
 *
 * <p>UniversalAdmin has no jails; a player who is jailed gets a staff note
 * saying so, so the information is not lost ({@code moderation.import.note.essentials-jail}).
 * EssentialsX does not record who muted someone or
 * when; the userdata file's date stands in for the time.
 */
public final class EssentialsImporter implements PunishmentImporter {

    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final Path pluginsFolder;
    private final NoteText noteText;

    public EssentialsImporter(Path pluginsFolder, NoteText noteText) {
        this.pluginsFolder = pluginsFolder;
        this.noteText = noteText;
    }

    @Override
    public String id() {
        return "essentials";
    }

    @Override
    public String displayName() {
        return "EssentialsX";
    }

    @Override
    public Optional<String> afterImportNoteKey() {
        return Optional.of("moderation.import.after.essentials");
    }

    private Path userdata() {
        return pluginsFolder.resolve("Essentials").resolve("userdata");
    }

    @Override
    public Optional<String> locate() {
        return Files.isDirectory(userdata())
                ? Optional.of(pluginsFolder.getParent().relativize(userdata().toAbsolutePath()).toString())
                : Optional.empty();
    }

    @Override
    public void read(ImportSink sink) throws ImportException {
        if (!Files.isDirectory(userdata())) {
            throw new ImportException("moderation.import.error.not-found", displayName());
        }
        try (Stream<Path> files = Files.list(userdata())) {
            Iterator<Path> iterator = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted()
                    .iterator();
            while (iterator.hasNext()) {
                read(iterator.next(), sink);
            }
        } catch (IOException e) {
            throw new ImportException("moderation.import.error.read", e, displayName(), e.getMessage());
        }
    }

    private void read(Path file, ImportSink sink) {
        String fileName = file.getFileName().toString();
        Optional<UUID> player = ImportTargets.uuid(fileName.substring(0, fileName.length() - ".yml".length()));
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file.toFile());
        boolean muted = data.getBoolean("muted", false);
        boolean jailed = data.getBoolean("jailed", false);
        if (!muted && !jailed) {
            return;
        }
        if (player.isEmpty()) {
            sink.skip(ImportSink.SkipReason.NO_PLAYER);
            return;
        }
        String name = firstString(data, "last-account-name", "lastAccountName");
        Instant fileTime = modified(file);
        if (muted) {
            long until = data.getLong("timestamps.mute", 0);
            sink.punishment(new ImportedPunishment("mute:" + player.get() + ":" + until, PunishmentType.MUTE, player.get(), name,
                    null, null, "EssentialsX", firstString(data, "mute-reason", "muteReason"), fileTime,
                    until > 0 ? Instant.ofEpochMilli(until) : null, true, null, null, null));
        }
        if (jailed) {
            String jail = firstString(data, "jail");
            long until = data.getLong("timestamps.jail", 0);
            String text = until > 0
                    ? noteText.format("moderation.import.note.essentials-jail-until", jail == null ? "?" : jail,
                            NOTE_DATE.format(Instant.ofEpochMilli(until)))
                    : noteText.format("moderation.import.note.essentials-jail", jail == null ? "?" : jail);
            sink.note(new ImportedNote("jail:" + player.get() + ":" + until, player.get(), name, "EssentialsX", text, fileTime));
        }
    }

    private static String firstString(YamlConfiguration data, String... keys) {
        for (String key : keys) {
            String value = data.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
