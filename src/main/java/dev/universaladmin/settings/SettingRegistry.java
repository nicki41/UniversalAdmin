package dev.universaladmin.settings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link SettingDefinition} known to the platform, keyed by
 * {@link SettingKey#configPath()} - not by the full namespaced key. Two
 * definitions can never share a config path even if they come from
 * different namespaces, because a config path is a literal location in
 * {@code config.yml}: only one setting can live at {@code gui.page-size}
 * regardless of which module or extension registered it.
 *
 * <p>Core settings are registered by {@link CoreSettings}; a built-in
 * module or, later, an extension registers its own under its
 * {@code ModuleDescriptor.settingsNamespace()}. Nothing here is Paper-
 * specific, so it's usable and testable without a running server.
 */
public final class SettingRegistry {

    private final Map<String, SettingDefinition<?>> byConfigPath = new LinkedHashMap<>();

    public synchronized <T> void register(SettingDefinition<T> definition) {
        String path = definition.key().configPath();
        SettingDefinition<?> existing = byConfigPath.get(path);
        if (existing != null) {
            throw new IllegalStateException("Setting already registered for config path '" + path
                    + "' (existing owner: " + existing.key() + ", new: " + definition.key() + ")");
        }
        byConfigPath.put(path, definition);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<SettingDefinition<T>> get(SettingKey<T> key) {
        return Optional.ofNullable((SettingDefinition<T>) byConfigPath.get(key.configPath()));
    }

    public Collection<SettingDefinition<?>> all() {
        return List.copyOf(byConfigPath.values());
    }
}
