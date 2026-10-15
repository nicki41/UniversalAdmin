package dev.universaladmin.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The buttons other modules and extensions add to a player's profile page -
 * see {@link PlayerMenuEntry}. Replaces per-module service links (the old
 * {@code ModerationPlayerLink}): any module, built-in or not, contributes the
 * same way, and the Players module never imports the contributor.
 */
public final class PlayerMenuRegistry {

    private final Map<String, PlayerMenuEntry> entries = new ConcurrentHashMap<>();

    /** @throws IllegalStateException if an entry with the same id is already registered */
    public void register(PlayerMenuEntry entry) {
        if (entries.putIfAbsent(entry.id(), entry) != null) {
            throw new IllegalStateException("Player menu entry already registered: " + entry.id());
        }
    }

    public void unregister(String id) {
        entries.remove(id);
    }

    /** Every entry, sorted by {@link PlayerMenuEntry#order()} and then id. */
    public List<PlayerMenuEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparingInt(PlayerMenuEntry::order).thenComparing(PlayerMenuEntry::id))
                .toList();
    }
}
