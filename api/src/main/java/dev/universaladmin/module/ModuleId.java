package dev.universaladmin.module;

import dev.universaladmin.core.id.Key;

/** Typed identifier for a {@link Module}. See {@link Key} for the namespacing rationale. */
public record ModuleId(Key key) {

    public static ModuleId of(String namespace, String name) {
        return new ModuleId(Key.of(namespace, name));
    }

    /** Shorthand used by UniversalAdmin's own built-in modules (players, moderation, ...). */
    public static ModuleId core(String name) {
        return new ModuleId(Key.core(name));
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
