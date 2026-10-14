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
 * <p>Settings are resolved once at construction, and lazily on first read
 * for definitions registered later (module and extension settings) - so a
 * module's value in {@code config.yml} applies from the start, without
 * needing an {@code /admin reload}.
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
    private final Runnable configSaver;
    private final Logger logger;
    private final Map<String, Object> liveValues = new ConcurrentHashMap<>();

    /**
     * @param configSaver persists {@code configSupplier.get()} to disk after an in-game change -
     *                    typically {@code JavaPlugin::saveConfig}
     */
    public YamlSettingsService(
            SettingRegistry registry, Supplier<FileConfiguration> configSupplier, Runnable configSaver, Logger logger) {
        this.registry = registry;
        this.configSupplier = configSupplier;
        this.configSaver = configSaver;
        this.logger = logger;
        applyAll(configSupplier.get(), true);
    }

    /** Read-only variant (in-game changes are applied but never saved) - for tests and tools. */
    public YamlSettingsService(SettingRegistry registry, Supplier<FileConfiguration> configSupplier, Logger logger) {
        this(registry, configSupplier, () -> { }, logger);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(SettingKey<T> key) {
        SettingDefinition<T> definition = requireDefinition(key);
        Object value = liveValues.get(key.configPath());
        return value != null ? (T) value : resolveLate(definition);
    }

    /**
     * First read of a setting registered after this service was built -
     * every module and extension registers its settings in its own
     * lifecycle, long after the core settings were resolved at startup.
     * Resolved from the current file exactly like at startup (invalid value
     * -> logged, default used) and cached, so it is not re-parsed per read.
     */
    private synchronized <T> T resolveLate(SettingDefinition<T> definition) {
        Object existing = liveValues.get(definition.key().configPath());
        if (existing != null) {
            @SuppressWarnings("unchecked")
            T cached = (T) existing;
            return cached;
        }
        T resolved = resolveValue(configSupplier.get(), definition, new ArrayList<>());
        liveValues.put(definition.key().configPath(), resolved);
        return resolved;
    }

    @Override
    public <T> SettingValue<T> getValue(SettingKey<T> key) {
        return new SettingValue<>(requireDefinition(key), get(key));
    }

    @Override
    public synchronized ConfigReloadResult reload() {
        return applyAll(configSupplier.get(), false);
    }

    @Override
    public synchronized SettingChange set(String configPath, String rawValue) throws SettingParseException {
        SettingDefinition<?> definition = registry.byPath(configPath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + configPath));
        return write(definition, rawValue);
    }

    @Override
    public synchronized SettingChange reset(String configPath) {
        SettingDefinition<?> definition = registry.byPath(configPath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + configPath));
        return resetOne(definition);
    }

    private <T> SettingChange write(SettingDefinition<T> definition, String rawValue) throws SettingParseException {
        T parsed = definition.type().parseText(rawValue);
        var validationError = definition.validator().validate(parsed);
        if (validationError.isPresent()) {
            throw new SettingParseException(validationError.get());
        }
        String oldValue = definition.type().format(get(definition.key()));
        FileConfiguration config = configSupplier.get();
        config.set(definition.key().configPath(), definition.type().toYaml(parsed));
        configSaver.run();
        if (!definition.requiresRestart()) {
            liveValues.put(definition.key().configPath(), parsed);
        }
        return new SettingChange(definition, oldValue, definition.type().format(parsed), definition.requiresRestart());
    }

    private <T> SettingChange resetOne(SettingDefinition<T> definition) {
        String oldValue = definition.type().format(get(definition.key()));
        FileConfiguration config = configSupplier.get();
        config.set(definition.key().configPath(), null);
        configSaver.run();
        if (!definition.requiresRestart()) {
            liveValues.put(definition.key().configPath(), definition.defaultValue());
        }
        return new SettingChange(definition, oldValue, definition.type().format(definition.defaultValue()),
                definition.requiresRestart());
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
