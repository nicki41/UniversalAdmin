package dev.universaladmin.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@link SettingsService} backed by a {@link SettingRegistry} and a Bukkit
 * {@link FileConfiguration} supplier. The supplier (rather than a fixed
 * {@code FileConfiguration} instance) is what lets {@link #reload()} pick up
 * whatever {@code UniversalAdminPlugin#reloadConfig()} most recently read
 * from disk, without this class knowing anything about {@code JavaPlugin}.
 *
 * <p>Resolution never throws: a missing key uses the default; a value that
 * fails to parse ({@link SettingType}) or validate ({@link SettingValidator})
 * is logged as a warning and also falls back to the default. See
 * docs/architecture/overview.md for why settings are never allowed to crash
 * the server.
 */
public final class YamlSettingsService implements SettingsService {

    private final SettingRegistry registry;
    private final Supplier<FileConfiguration> configSupplier;
    private final Logger logger;
    private final Map<String, Object> liveValues = new ConcurrentHashMap<>();

    public YamlSettingsService(SettingRegistry registry, Supplier<FileConfiguration> configSupplier, Logger logger) {
        this.registry = registry;
        this.configSupplier = configSupplier;
        this.logger = logger;
        applyAll(configSupplier.get(), true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(SettingKey<T> key) {
        SettingDefinition<T> definition = requireDefinition(key);
        Object value = liveValues.get(key.configPath());
        return value != null ? (T) value : definition.defaultValue();
    }

    @Override
    public <T> SettingValue<T> getValue(SettingKey<T> key) {
        return new SettingValue<>(requireDefinition(key), get(key));
    }

    @Override
    public ConfigReloadResult reload() {
        return applyAll(configSupplier.get(), false);
    }

    private <T> SettingDefinition<T> requireDefinition(SettingKey<T> key) {
        return registry.get(key).orElseThrow(() -> new IllegalArgumentException("Unregistered setting: " + key));
    }

    private ConfigReloadResult applyAll(FileConfiguration config, boolean initialLoad) {
        List<SettingKey<?>> applied = new ArrayList<>();
        List<SettingKey<?>> pendingRestart = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SettingDefinition<?> definition : registry.all()) {
            resolveOne(config, definition, initialLoad, applied, pendingRestart, errors);
        }
        return new ConfigReloadResult(List.copyOf(applied), List.copyOf(pendingRestart), List.copyOf(errors));
    }

    private <T> void resolveOne(
            FileConfiguration config,
            SettingDefinition<T> definition,
            boolean initialLoad,
            List<SettingKey<?>> applied,
            List<SettingKey<?>> pendingRestart,
            List<String> errors) {
        String path = definition.key().configPath();
        T resolved = resolveValue(config, definition, errors);

        if (!initialLoad
                && definition.requiresRestart()
                && liveValues.containsKey(path)
                && !Objects.equals(liveValues.get(path), resolved)) {
            pendingRestart.add(definition.key());
            logger.warning(() -> "Setting " + definition.key()
                    + " changed but requires a restart to take effect - keeping the previous value for now.");
            return;
        }

        liveValues.put(path, resolved);
        applied.add(definition.key());
    }

    private <T> T resolveValue(FileConfiguration config, SettingDefinition<T> definition, List<String> errors) {
        String path = definition.key().configPath();
        if (!config.isSet(path)) {
            return definition.defaultValue();
        }
        Object raw = config.get(path);
        try {
            T parsed = definition.type().parse(raw);
            var validationError = definition.validator().validate(parsed);
            if (validationError.isPresent()) {
                String message = "Invalid value for '" + path + "': " + validationError.get() + ". Using default ("
                        + definition.defaultValue() + ").";
                errors.add(message);
                logger.warning(message);
                return definition.defaultValue();
            }
            return parsed;
        } catch (SettingParseException e) {
            String message = "Could not parse '" + path + "' as " + definition.type().describe() + ": "
                    + e.getMessage() + ". Using default (" + definition.defaultValue() + ").";
            errors.add(message);
            logger.warning(message);
            return definition.defaultValue();
        }
    }
}
