package dev.universaladmin.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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

    private SettingsService settingsWithLocale(String locale) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.LANGUAGE)).thenReturn(locale);
        return settings;
    }

    private void writeLangFile(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }
}
