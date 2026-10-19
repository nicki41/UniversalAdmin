package dev.universaladmin.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlLocaleMessageServiceTest {

    @Test
    void resolvesFromTheActiveLocale(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "greeting: \"Hello, {0}!\"");
        writeLangFile(langDir, "de_DE.yml", "greeting: \"Hallo, {0}!\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("de_DE"), Logger.getLogger("test"));

        assertEquals("Hallo, Welt!", service.get(MessageKey.of("greeting"), "Welt"));
    }

    @Test
    void fallsBackToEnUsWhenTheKeyIsMissingInTheActiveLocale(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "only-in-english: \"English only\"");
        writeLangFile(langDir, "de_DE.yml", "some-other-key: \"...\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("de_DE"), Logger.getLogger("test"));

        assertEquals("English only", service.get(MessageKey.of("only-in-english")));
    }

    @Test
    void showsAVisibleMarkerAndLogsOnlyOnceWhenTheKeyIsMissingEverywhere(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "greeting: \"Hello!\"");
        Logger logger = Logger.getLogger("YamlLocaleMessageServiceTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), logger);

        String first = service.get(MessageKey.of("totally.missing"));
        String second = service.get(MessageKey.of("totally.missing"));

        assertEquals("[missing: totally.missing]", first);
        assertEquals(first, second);
        assertEquals(1, records.size());
    }

    @Test
    void substitutesParametersIntoTheResolvedTemplate(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "summary: \"{0} has {1} new messages.\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), Logger.getLogger("test"));

        assertEquals("Steve has 3 new messages.", service.get(MessageKey.of("summary"), "Steve", 3));
    }

    @Test
    void fallsBackToTheBundledCopyOfTheSameLocaleForKeysMissingFromTheOverrideFile(@TempDir Path langDir) throws IOException {
        // The data-folder copy is written once and never updated - a key
        // added in a later version must still resolve from the jar's copy.
        writeLangFile(langDir, "de_DE.yml", "old-key: \"Alt (angepasst)\"");
        YamlLocaleMessageService service = new YamlLocaleMessageService(langDir,
                bundled(Map.of("lang/de_DE.yml", "old-key: \"Alt\"\nnew-key: \"Neu\"")),
                List.of("de_DE"), settingsWithLocale("de_DE"), Logger.getLogger("test"));

        assertEquals("Alt (angepasst)", service.get(MessageKey.of("old-key")));
        assertEquals("Neu", service.get(MessageKey.of("new-key")));
    }

    @Test
    void prefersTheActiveLocaleOverTheEnglishFallbackAcrossLayers(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "greeting: \"Hello (override)\"");
        YamlLocaleMessageService service = new YamlLocaleMessageService(langDir,
                bundled(Map.of("lang/de_DE.yml", "greeting: \"Hallo\"", "lang/en_US.yml", "greeting: \"Hello\"")),
                List.of("en_US", "de_DE"), settingsWithLocale("de_DE"), Logger.getLogger("test"));

        assertEquals("Hallo", service.get(MessageKey.of("greeting")));
    }

    @Test
    void escapesMiniMessageTagsInStringArguments(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "reason: \"<red>Reason: {0}\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), Logger.getLogger("test"));

        String resolved = service.get(MessageKey.of("reason"), "<click:run_command:/op me>hi");

        assertEquals("<red>Reason: \\<click:run_command:/op me>hi", resolved);
        assertEquals("Reason: <click:run_command:/op me>hi",
                PlainTextComponentSerializer.plainText().serialize(ComponentMessages.render(resolved)));
    }

    @Test
    void substitutesExplicitlyTrustedMarkupVerbatim(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "wrapper: \"[{0}]\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), Logger.getLogger("test"));

        assertEquals("[<green>ok</green>]", service.get(MessageKey.of("wrapper"), MessageService.markup("<green>ok</green>")));
    }

    @Test
    void registeredDefaultsAreOverriddenByTheServerOwnersFile(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "myext.title: \"Custom title\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), Logger.getLogger("test"));
        service.registerDefaults("en_US", Map.of("myext.title", "Default title", "myext.body", "Default body"));

        assertEquals("Custom title", service.get(MessageKey.of("myext.title")));
        assertEquals("Default body", service.get(MessageKey.of("myext.body")));
    }

    @Test
    void aBrokenOverrideFileIsSkippedInsteadOfFailingStartup(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "greeting: [unclosed");
        YamlLocaleMessageService service = new YamlLocaleMessageService(langDir,
                bundled(Map.of("lang/en_US.yml", "greeting: \"Hello\"")),
                List.of("en_US"), settingsWithLocale("en_US"), quietLogger());

        assertEquals("Hello", service.get(MessageKey.of("greeting")));
    }

    @Test
    void reloadPicksUpEditedOverrideFiles(@TempDir Path langDir) throws IOException {
        writeLangFile(langDir, "en_US.yml", "greeting: \"Hello\"");
        YamlLocaleMessageService service =
                new YamlLocaleMessageService(langDir, settingsWithLocale("en_US"), Logger.getLogger("test"));
        writeLangFile(langDir, "en_US.yml", "greeting: \"Howdy\"");

        service.reload();

        assertEquals("Howdy", service.get(MessageKey.of("greeting")));
    }

    private Function<String, InputStream> bundled(Map<String, String> resources) {
        return name -> resources.containsKey(name)
                ? new ByteArrayInputStream(resources.get(name).getBytes(StandardCharsets.UTF_8))
                : null;
    }

    private Logger quietLogger() {
        Logger logger = Logger.getLogger("YamlLocaleMessageServiceTest-quiet-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private SettingsService settingsWithLocale(String locale) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.LANGUAGE)).thenReturn(locale);
        return settings;
    }

    private void writeLangFile(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }
}
