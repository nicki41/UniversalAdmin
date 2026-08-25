package dev.universaladmin.gui;

import dev.universaladmin.core.id.Key;

/** Typed identifier for a {@link GuiPage}, e.g. {@code core:players.list}. */
public record GuiPageId(Key key) {

    public static GuiPageId of(String namespace, String name) {
        return new GuiPageId(Key.of(namespace, name));
    }

    public static GuiPageId core(String name) {
        return new GuiPageId(Key.core(name));
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
