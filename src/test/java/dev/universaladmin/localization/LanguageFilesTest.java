package dev.universaladmin.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The data folder's lang files hold only the owner's own texts; old full copies are cleaned once. */
class LanguageFilesTest {

    private static final Logger LOGGER = Logger.getLogger("test");
    private static final Function<String, InputStream> JAR = LanguageFilesTest.class.getClassLoader()::getResourceAsStream;

    @Test
    void anOldFullCopyKeepsOnlyTheOwnersTexts(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("en_US.yml");
        Files.writeString(file, """
                command:
                  prefix: "<red>[MyServer]</red> "
                  done: "<green>Done."
                chat:
                  locked-broadcast: "old default text"
                custom:
                  greeting: "Hello"
                """, StandardCharsets.UTF_8);
        Map<String, String> bundled = Map.of("command.prefix", "<gray>[UA]</gray> ", "command.done", "<green>Done.",
                "chat.locked-broadcast", "new default text");
        Map<String, Set<String>> history = Map.of("chat.locked-broadcast", Set.of(LanguageFiles.digest("old default text")));

        int kept = LanguageFiles.clean(file, "en_US", bundled, history, LOGGER);

        assertEquals(2, kept);
        String cleaned = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(cleaned.startsWith(LanguageFiles.MARKER));
        YamlConfiguration own = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("<red>[MyServer]</red> ", own.getString("command.prefix"));
        assertEquals("Hello", own.getString("custom.greeting"));
        assertFalse(own.contains("command.done"), "the current default is not an own text");
        assertFalse(own.contains("chat.locked-broadcast"), "an old default must not shadow the new text");
        assertTrue(Files.readString(dir.resolve("en_US" + LanguageFiles.BACKUP_SUFFIX)).contains("old default text"));

        // Once cleaned, the file is the owner's - later starts leave it alone.
        assertEquals(-1, LanguageFiles.clean(file, "en_US", bundled, history, LOGGER));
        assertEquals(cleaned, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void aNewInstallationGetsOnlyTheExplanationAndTheReference(@TempDir Path dir) throws Exception {
        LanguageFiles.prepare(dir, List.of("en_US", "de_DE"), JAR, LOGGER);

        String own = Files.readString(dir.resolve("de_DE.yml"), StandardCharsets.UTF_8);
        assertTrue(own.startsWith(LanguageFiles.MARKER));
        assertTrue(YamlConfiguration.loadConfiguration(dir.resolve("de_DE.yml").toFile()).getKeys(true).isEmpty());
        assertTrue(Files.readString(dir.resolve("reference/en_US.yml"), StandardCharsets.UTF_8).contains("prefix:"));
    }

    @Test
    void aFullCopyOfTheCurrentVersionBecomesEmpty(@TempDir Path dir) throws Exception {
        try (InputStream bundled = JAR.apply("lang/de_DE.yml")) {
            Files.write(dir.resolve("de_DE.yml"), bundled.readAllBytes());
        }

        LanguageFiles.prepare(dir, List.of("de_DE"), JAR, LOGGER);

        assertTrue(YamlConfiguration.loadConfiguration(dir.resolve("de_DE.yml").toFile()).getKeys(true).isEmpty());
    }

    /** The history is written by a Python script - Java must read the same texts and hash them the same way. */
    @Test
    void theHistoryKnowsTheCurrentTexts() throws Exception {
        for (String locale : List.of("en_US", "de_DE")) {
            Map<String, Set<String>> history = LanguageFiles.history(JAR, locale);
            YamlConfiguration bundled = new YamlConfiguration();
            try (InputStream in = JAR.apply("lang/" + locale + ".yml")) {
                bundled.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            int known = 0;
            int total = 0;
            for (String key : bundled.getKeys(true)) {
                if (bundled.isString(key)) {
                    total++;
                    if (history.getOrDefault(key, Set.of()).contains(LanguageFiles.digest(bundled.getString(key)))) {
                        known++;
                    }
                }
            }
            // Texts changed after the history was generated are not in it - most must be.
            assertTrue(known > total * 0.9, locale + ": " + known + " of " + total + " texts found in the history");
        }
    }
}
