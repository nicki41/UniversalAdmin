package dev.universaladmin.localization;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Loads the real, shipped {@code src/main/resources/lang/*.yml} files
 * through the exact same {@link org.bukkit.configuration.file.YamlConfiguration}
 * path production code uses (not a synthetic in-test string, unlike {@link
 * YamlLocaleMessageServiceTest}), and cross-checks every {@code
 * MessageKey.of("...")} string literal actually found in {@code src/main/java}
 * against {@code en_US.yml} (the mandatory fallback locale - see {@link
 * YamlLocaleMessageService}'s javadoc, every other locale falls back to it).
 *
 * <p>Exists because two real, live-server-only bugs slipped past every other
 * check this project has: (1) a duplicate sibling YAML key silently
 * discarding the first block's translations ("worlds.gui.action" defined
 * twice), and (2) YAML 1.1 treating unquoted {@code on:}/{@code off:} keys
 * as booleans, not the strings the code expected ({@code
 * moderation.gui.status.on/off}). Both would have failed this test
 * immediately.
 */
class RealLangFilesTest {

    // Two call shapes reference a lang key in this codebase: MessageKey.of(...)
    // directly, and the AbstractGuiPage/AbstractListGuiPage "text(...)"
    // helper (never "Component.text(...)" or "foo.text(...)" - the negative
    // lookbehind excludes those, since that's a literal display string, not
    // a lang key). For both, this finds the call's argument list and then
    // pulls every dotted, key-shaped quoted literal out of it - not just one
    // immediately after the opening paren - so a ternary like
    // "text(condition ? \"a.b\" : \"a.c\")" yields both "a.b" and "a.c"
    // instead of being missed entirely (this exact shape is what
    // ModerationHomePage's toggle-status lookup uses, and it was the actual
    // page whose bug prompted writing this test). A key literal is assumed
    // to contain at least one dot and use only lowercase/digits/./-/_ -
    // display strings passed to Component.text(...) essentially never look
    // like that.
    private static final Pattern KEY_LOOKUP_CALL = Pattern.compile("(?:MessageKey\\.of|(?<![.\\w])text|\\.reply|\\.line)\\(([^)]*)\\)");
    private static final Pattern KEY_SHAPED_LITERAL = Pattern.compile("\"([a-z][a-z0-9_-]*(?:\\.[a-z0-9_-]+)+)\"");

    @Test
    void everyMessageKeyLiteralInSourceResolvesInTheFallbackLocale() throws IOException {
        YamlConfiguration enUs = loadRealLangFile("en_US.yml");
        Set<String> missing = new LinkedHashSet<>();
        for (String key : messageKeyLiteralsInSource()) {
            if (!enUs.isString(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), () -> "MessageKey.of(...) literals with no matching string in lang/en_US.yml: " + missing);
    }

    @Test
    void enUsAndDeDeHaveNoAmbiguousYamlKeys() throws IOException {
        // A YAML boolean-like scalar (on/off/yes/no/true/false, case-insensitive)
        // used as an unquoted mapping key resolves to a Boolean key under YAML
        // 1.1, silently breaking any code that looks it up by its intended
        // string name - see this class's javadoc. Loading through
        // YamlConfiguration (not raw SnakeYAML) already produces the same
        // failure mode Bukkit's real config loading does; if a key is
        // missing from getKeys(true), it was swallowed exactly this way.
        checkForRegressionRisk("en_US.yml");
        checkForRegressionRisk("de_DE.yml");
    }

    @Test
    void deDeTranslatesEveryEnglishKey() throws IOException {
        YamlConfiguration enUs = loadRealLangFile("en_US.yml");
        YamlConfiguration deDe = loadRealLangFile("de_DE.yml");
        Set<String> missing = new LinkedHashSet<>();
        for (String key : enUs.getKeys(true)) {
            if (enUs.isString(key) && !deDe.isString(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), () -> "Keys in en_US.yml without a de_DE.yml translation: " + missing);
    }

    @Test
    void templatesWithParametersDoubleTheirApostrophes() throws IOException {
        // MessageFormat treats a single ' as the start of a quoted section,
        // so "don't {0}" silently swallows the parameter. Templates without
        // parameters are returned verbatim and may use plain apostrophes.
        Pattern parameter = Pattern.compile("\\{\\d+}");
        Pattern singleApostrophe = Pattern.compile("(?<!')'(?!')");
        Set<String> offenders = new LinkedHashSet<>();
        for (String fileName : List.of("en_US.yml", "de_DE.yml")) {
            YamlConfiguration config = loadRealLangFile(fileName);
            for (String key : config.getKeys(true)) {
                String value = config.getString(key);
                if (config.isString(key) && parameter.matcher(value).find() && singleApostrophe.matcher(value).find()) {
                    offenders.add(fileName + ": " + key);
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Templates with {n} parameters must write apostrophes as '': " + offenders);
    }

    private void checkForRegressionRisk(String fileName) throws IOException {
        Path source = Path.of("src/main/resources/lang", fileName);
        assertTrue(Files.exists(source), () -> source + " not found - run from the project root");
        Pattern ambiguousKey = Pattern.compile(
                "(?i)^\\s*(on|off|yes|no|true|false)\\s*:", Pattern.MULTILINE);
        String content = Files.readString(source, StandardCharsets.UTF_8);
        Matcher matcher = ambiguousKey.matcher(content);
        Set<String> offenders = new LinkedHashSet<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            // Quoting ("on": ...) is the fix - only flag genuinely bare keys.
            String line = matcher.group();
            if (!line.contains("\"") && !line.contains("'")) {
                offenders.add(key + " (in " + fileName + ")");
            }
        }
        if (!offenders.isEmpty()) {
            fail("Unquoted YAML-boolean-ambiguous key(s) found - quote them (e.g. \"on\": ...) so they parse as strings: " + offenders);
        }
    }

    private YamlConfiguration loadRealLangFile(String fileName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + fileName)) {
            assertTrue(in != null, () -> "lang/" + fileName + " not found on the test classpath");
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (InvalidConfigurationException e) {
                throw new IOException("Failed to parse lang/" + fileName, e);
            }
            return config;
        }
    }

    private Set<String> messageKeyLiteralsInSource() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        // The core and the public API (api/) both resolve lang keys.
        for (Path root : List.of(Path.of("src/main/java"), Path.of("api/src/main/java"))) {
            assertTrue(Files.exists(root), () -> root + " not found - run from the project root");
            try (var paths = Files.walk(root)) {
                List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
                for (Path file : javaFiles) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher calls = KEY_LOOKUP_CALL.matcher(content);
                    while (calls.find()) {
                        Matcher literals = KEY_SHAPED_LITERAL.matcher(calls.group(1));
                        while (literals.find()) {
                            keys.add(literals.group(1));
                        }
                    }
                }
            }
        }
        return keys;
    }
}
