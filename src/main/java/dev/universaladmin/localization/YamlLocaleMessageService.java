package dev.universaladmin.localization;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * {@link MessageService} backed by one flat key -&gt; string map per locale,
 * loaded from every {@code lang/*.yml} file present at construction time -
 * new locales just need a file dropped in, no code change. {@code en_US} is
 * the hardcoded fallback locale; every other locale falls back to it for any
 * key it's missing, and a key missing from *both* renders as a visible
 * marker instead of silently vanishing.
 *
 * <p>The active locale is read fresh from {@link CoreSettings#LANGUAGE} on
 * every {@link #get} call rather than cached, so changing {@code general.language}
 * and running {@code /admin reload} takes effect immediately without this
 * class needing its own reload hook.
 *
 * <p>Lang files are only loaded once, at construction - adding a new
 * {@code lang/xx_XX.yml} file requires a restart to pick up, unlike
 * {@code config.yml} settings. Not wired into {@code /admin reload}; see
 * docs/development/settings.md.
 */
public final class YamlLocaleMessageService implements MessageService {

    private static final String FALLBACK_LOCALE = "en_US";

    private final Map<String, Map<String, String>> messagesByLocale;
    private final SettingsService settingsService;
    private final Logger logger;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    public YamlLocaleMessageService(Path langDirectory, SettingsService settingsService, Logger logger) {
        this.messagesByLocale = loadAll(langDirectory);
        this.settingsService = settingsService;
        this.logger = logger;
    }

    @Override
    public String get(MessageKey key, Object... args) {
        String template = resolveTemplate(key);
        return args.length == 0 ? template : MessageFormat.format(template, args);
    }

    private String resolveTemplate(MessageKey key) {
        String activeLocale = settingsService.get(CoreSettings.LANGUAGE);

        String template = messagesByLocale.getOrDefault(activeLocale, Map.of()).get(key.value());
        if (template != null) {
            return template;
        }

        template = messagesByLocale.getOrDefault(FALLBACK_LOCALE, Map.of()).get(key.value());
        if (template != null) {
            return template;
        }

        if (warnedMissingKeys.add(key.value())) {
            logger.warning(() -> "Missing localization key '" + key.value() + "' in locale '" + activeLocale
                    + "' and fallback '" + FALLBACK_LOCALE + "'.");
        }
        return "[missing: " + key.value() + "]";
    }

    private static Map<String, Map<String, String>> loadAll(Path langDirectory) {
        File[] files = langDirectory.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return Map.of();
        }
        return Arrays.stream(files)
                .collect(Collectors.toMap(
                        file -> file.getName().substring(0, file.getName().length() - ".yml".length()),
                        YamlLocaleMessageService::loadFlat));
    }

    private static Map<String, String> loadFlat(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Failed to load locale file " + file, e);
        }
        return config.getKeys(true).stream()
                .filter(config::isString)
                .collect(Collectors.toMap(key -> key, config::getString));
    }
}
