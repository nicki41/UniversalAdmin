package dev.universaladmin.settings;

import dev.universaladmin.core.id.Key;

/**
 * Typed identifier for a setting, e.g. {@code core:gui.page-size}.
 *
 * <p>Unlike other typed IDs, {@link #configPath()} is not just an opaque
 * name - it is the literal dotted path into {@code config.yml}
 * ({@code Key.name()} already allows dots, see {@link Key}'s Javadoc, which
 * is exactly the shape a YAML path needs). {@link #namespace()} is who
 * *owns* the setting for registry bookkeeping (core, a built-in module's
 * {@code settingsNamespace}, later an extension id) - it is not part of the
 * YAML path, since the YAML file itself has no such prefix.
 *
 * @param <T> the setting's value type, purely a compile-time safety net -
 *            nothing about it is retained at runtime (see {@link SettingDefinition#type()}
 *            for the runtime parsing/validation logic)
 */
public record SettingKey<T>(Key key) {

    public static <T> SettingKey<T> of(String namespace, String configPath) {
        return new SettingKey<>(Key.of(namespace, configPath));
    }

    public String namespace() {
        return key.namespace();
    }

    /** The dotted path this setting lives at in {@code config.yml}, e.g. {@code "gui.page-size"}. */
    public String configPath() {
        return key.name();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
