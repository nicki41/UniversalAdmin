package dev.universaladmin.core.id;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A namespaced identifier, e.g. {@code core:players} or {@code my-extension:custom-action}.
 *
 * <p>Every registry in UniversalAdmin (modules, actions, GUI pages, audit event
 * types, ...) is keyed by a {@link Key} instead of a bare {@link String}. The
 * namespace exists so that built-in modules and future extensions can never
 * collide on an identifier: {@code core:kick} and {@code my-extension:kick}
 * are different keys.
 */
public record Key(String namespace, String name) {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");

    public Key {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid key namespace '" + namespace + "', expected " + NAMESPACE_PATTERN.pattern());
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid key name '" + name + "', expected " + NAME_PATTERN.pattern());
        }
    }

    public static Key of(String namespace, String name) {
        return new Key(namespace, name);
    }

    /** Shorthand for the {@code core} namespace, used by UniversalAdmin's own built-in modules. */
    public static Key core(String name) {
        return new Key("core", name);
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
