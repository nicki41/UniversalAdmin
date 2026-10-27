package dev.universaladmin.update;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plugins the update check and {@code /admin update} cover - UniversalAdmin
 * itself from startup, extensions as they register (and leave again when
 * they are disabled). Read on every check, so a change applies to the next
 * one; safe from any thread.
 */
public final class UpdateTargets {

    /** UniversalAdmin's own repository - its releases carry the core jar as {@link #CORE_ASSET_PREFIX}{@code <version>.jar}. */
    public static final String CORE_REPOSITORY = "universaladmin-mc/UniversalAdmin";
    public static final String CORE_ASSET_PREFIX = "universaladmin-core-";

    private final Map<String, UpdateTarget> targets = new ConcurrentHashMap<>();

    /** Adds {@code target}, replacing one with the same name. */
    public void add(UpdateTarget target) {
        targets.put(target.name(), target);
    }

    public void remove(String name) {
        targets.remove(name);
    }

    /** Every target, UniversalAdmin first, then by name. */
    public List<UpdateTarget> all() {
        return targets.values().stream()
                .sorted((a, b) -> a.repository().equals(CORE_REPOSITORY) ? -1
                        : b.repository().equals(CORE_REPOSITORY) ? 1 : a.name().compareToIgnoreCase(b.name()))
                .toList();
    }
}
