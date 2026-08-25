package dev.universaladmin.telemetry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads (or, on first start, creates) the {@link InstallationIdentity} in
 * {@code <plugin data folder>/installation-id.yml}.
 *
 * <p>Its own file rather than a key in {@code config.yml} for two reasons: it
 * is generated state, not a setting a server owner is meant to edit (so it
 * has no {@code SettingDefinition} - see
 * docs/development/architecture-rules.md's Configuration section), and
 * keeping it out of {@code config.yml} means copying a config between servers
 * doesn't silently clone one installation's identity onto another.
 *
 * <p>Uses {@link YamlConfiguration} (a plain data-structure class in
 * paper-api, usable without a running server) so the file looks like every
 * other file UniversalAdmin writes.
 */
public final class InstallationIdentityStore {

    static final String FILE_NAME = "installation-id.yml";
    static final String KEY = "installation-id";

    private static final List<String> HEADER = List.of(
            "UniversalAdmin installation id.",
            "",
            "A random 128-bit value generated once, on first start. It identifies this",
            "installation to the anonymous usage statistics described in",
            "docs/user/telemetry.md - nothing else. It is not derived from your hardware,",
            "MAC address, IP, hostname, server address, or any player data.",
            "",
            "Telemetry can be switched off completely with telemetry.enabled: false in",
            "config.yml; nothing is sent then, and this file is simply unused.",
            "",
            "Deleting this file makes the next start generate a new id, which the backend",
            "would count as a different installation.");

    private final Path dataFolder;
    private final Logger logger;

    public InstallationIdentityStore(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * The stored identity, generating and persisting one if the file is
     * missing, empty, or holds a value that isn't a valid identity.
     *
     * <p>Never throws: an unreadable or unwritable data folder degrades to an
     * in-memory identity for this run (telemetry stays anonymous either way,
     * the backend just sees a new installation next restart) rather than
     * taking the plugin down over a statistics file.
     */
    public InstallationIdentity loadOrCreate() {
        Path file = dataFolder.resolve(FILE_NAME);
        InstallationIdentity existing = read(file);
        if (existing != null) {
            return existing;
        }
        InstallationIdentity created = InstallationIdentity.generate();
        write(file, created);
        return created;
    }

    private InstallationIdentity read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            String raw = yaml.getString(KEY);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return new InstallationIdentity(raw.trim());
        } catch (IllegalArgumentException e) {
            logger.warning(() -> FILE_NAME + " contains an invalid installation id; generating a new one.");
            return null;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not read " + FILE_NAME + "; generating a new installation id.", e);
            return null;
        }
    }

    private void write(Path file, InstallationIdentity identity) {
        try {
            Files.createDirectories(dataFolder);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().setHeader(HEADER);
            yaml.set(KEY, identity.value());
            File target = file.toFile();
            yaml.save(target);
        } catch (IOException | RuntimeException e) {
            // Not fatal: this run just uses an in-memory identity. Logged once,
            // at start, so it can't turn into a per-heartbeat log loop.
            logger.log(Level.WARNING,
                    "Could not persist " + FILE_NAME + "; this start uses a temporary installation id.", e);
        }
    }
}
