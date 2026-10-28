package dev.universaladmin.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The switches of the main menu's "My status" row - see {@link StatusToggle}.
 *
 * @since 1.7
 */
public final class StatusToggleRegistry {

    private final Map<String, StatusToggle> toggles = new ConcurrentHashMap<>();

    /** @throws IllegalStateException if a toggle with the same id is already registered */
    public void register(StatusToggle toggle) {
        if (toggles.putIfAbsent(toggle.id(), toggle) != null) {
            throw new IllegalStateException("Status toggle already registered: " + toggle.id());
        }
    }

    public void unregister(String id) {
        toggles.remove(id);
    }

    /** Every toggle, sorted by {@link StatusToggle#order()} and then id. */
    public List<StatusToggle> toggles() {
        return toggles.values().stream()
                .sorted(Comparator.comparingInt(StatusToggle::order).thenComparing(StatusToggle::id))
                .toList();
    }
}
