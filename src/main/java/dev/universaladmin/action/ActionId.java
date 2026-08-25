package dev.universaladmin.action;

import dev.universaladmin.core.id.Key;

/** Typed identifier for an {@link Action}, e.g. {@code core:players.kick}. */
public record ActionId(Key key) {

    public static ActionId of(String namespace, String name) {
        return new ActionId(Key.of(namespace, name));
    }

    public static ActionId core(String name) {
        return new ActionId(Key.core(name));
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
