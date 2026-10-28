package dev.universaladmin.localization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The data folder's {@code lang/} directory: the server owner's own texts,
 * nothing else (ADR 0026).
 *
 * <ul>
 *   <li>{@code lang/<locale>.yml} holds only the texts the owner changed -
 *       everything else comes from the plugin, so a text fixed or reworded in
 *       an update reaches the server. A new installation gets a file with
 *       only an explanation in it.</li>
 *   <li>{@code lang/reference/<locale>.yml} is every built-in text, rewritten
 *       on every start - to look up keys, not to edit.</li>
 *   <li>Up to 1.7.0 the whole bundled file was copied once and then
 *       shadowed every later change. Such a copy is cleaned once: a text
 *       equal to any default it ever had in a release is dropped, the rest
 *       are the owner's changes and stay. The original is kept next to it as
 *       {@code <locale>.yml.before-1.7.1}.</li>
 * </ul>
 */
public final class LanguageFiles {

    /** First line of an overrides-only file - a file starting with it is never cleaned again. */
    static final String MARKER = "# UniversalAdmin: your own texts";
    static final String BACKUP_SUFFIX = ".yml.before-1.7.1";

    private LanguageFiles() {
    }

    /**
     * Prepares {@code langDirectory} for every locale in {@code locales}.
     *
     * @param resources opens a resource of the plugin jar, {@code null} if it is missing
     */
    public static void prepare(Path langDirectory, List<String> locales, Function<String, InputStream> resources, Logger logger) {
        for (String locale : locales) {
            try {
                Map<String, String> bundled = read(resources, "lang/" + locale + ".yml");
                writeReference(langDirectory, locale, resources);
                clean(langDirectory.resolve(locale + ".yml"), locale, bundled, history(resources, locale), logger);
            } catch (IOException | InvalidConfigurationException e) {
                logger.log(Level.WARNING, "Could not prepare lang/" + locale + ".yml - its texts stay as they are", e);
            }
        }
    }

    /**
     * Turns {@code file} into an overrides-only file: writes the explanation
     * if it does not exist, and drops every text that is a default - the
     * current one or one from an earlier release ({@code history}: key to
     * hashes, see {@link #digest}). Returns how many of the owner's texts
     * the file keeps, or -1 if it was left alone.
     */
    static int clean(Path file, String locale, Map<String, String> bundled, Map<String, Set<String>> history, Logger logger)
            throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, header(locale), StandardCharsets.UTF_8);
            return 0;
        }
        String original = Files.readString(file, StandardCharsets.UTF_8);
        if (original.startsWith(MARKER)) {
            return -1;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(original);
        YamlConfiguration own = new YamlConfiguration();
        int kept = 0;
        for (String key : config.getKeys(true)) {
            if (!config.isString(key)) {
                continue;
            }
            String value = config.getString(key);
            boolean isDefault = value.equals(bundled.get(key)) || history.getOrDefault(key, Set.of()).contains(digest(value));
            if (!isDefault) {
                own.set(key, value);
                kept++;
            }
        }
        Path backup = file.resolveSibling(locale + BACKUP_SUFFIX);
        if (!Files.exists(backup)) {
            Files.writeString(backup, original, StandardCharsets.UTF_8);
        }
        String body = kept == 0 ? "" : own.saveToString();
        Files.writeString(file, header(locale) + body, StandardCharsets.UTF_8);
        int keptTexts = kept;
        logger.info(() -> "lang/" + locale + ".yml now holds only your own " + keptTexts + " text(s) - every other text "
                + "follows the plugin again. The old file is kept as lang/" + locale + BACKUP_SUFFIX + ".");
        return kept;
    }

    private static void writeReference(Path langDirectory, String locale, Function<String, InputStream> resources)
            throws IOException {
        try (InputStream in = resources.apply("lang/" + locale + ".yml")) {
            if (in == null) {
                return;
            }
            Path reference = langDirectory.resolve("reference").resolve(locale + ".yml");
            Files.createDirectories(reference.getParent());
            String text = "# Every built-in " + locale + " text of this UniversalAdmin version - rewritten on every start.\n"
                    + "# Do not edit this file: copy the keys you want to change into lang/" + locale + ".yml.\n\n"
                    + new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(reference, text, StandardCharsets.UTF_8);
        }
    }

    static String header(String locale) {
        return MARKER + " for " + locale + ".\n"
                + "#\n"
                + "# Only what you put here replaces a built-in text - everything else comes from\n"
                + "# the plugin and is updated with it. To change a text, copy its key from\n"
                + "# lang/reference/" + locale + ".yml (every text, rewritten on every start) into this\n"
                + "# file, keep the nesting, and run /admin reload. Example:\n"
                + "#\n"
                + "#   command:\n"
                + "#     prefix: \"<gray>[Admin]</gray> \"\n"
                + "\n";
    }

    private static Map<String, String> read(Function<String, InputStream> resources, String resource)
            throws IOException, InvalidConfigurationException {
        try (InputStream in = resources.apply(resource)) {
            if (in == null) {
                return Map.of();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> flat = new LinkedHashMap<>();
            for (String key : config.getKeys(true)) {
                if (config.isString(key)) {
                    flat.put(key, config.getString(key));
                }
            }
            return flat;
        }
    }

    /** {@code lang-history/<locale>.txt}: one line per key, a tab, then comma-separated hashes; {@code #} comments. */
    static Map<String, Set<String>> history(Function<String, InputStream> resources, String locale) throws IOException {
        try (InputStream in = resources.apply("lang-history/" + locale + ".txt")) {
            if (in == null) {
                return Map.of();
            }
            Map<String, Set<String>> history = new HashMap<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                int tab = line.indexOf('\t');
                if (line.startsWith("#") || tab < 0) {
                    continue;
                }
                history.put(line.substring(0, tab), Set.of(line.substring(tab + 1).trim().split(",")));
            }
            return history;
        }
    }

    /** The first 16 hex digits of the text's SHA-256 - what {@code lang-history} stores. */
    static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
