package dev.universaladmin.module;

/**
 * Lifecycle state of a {@link Module} as tracked by {@link ModuleRegistry}.
 * Transitions are driven exclusively by {@link ModuleManager}:
 *
 * <pre>
 * DISCOVERED --loadAll()--&gt; LOADED --enableAll()--&gt; ENABLED --disableAll()--&gt; DISABLED
 *                  \                       \
 *                   \--(onLoad throws,      \--(onEnable throws, or a
 *                       or an unknown            dependency isn't ENABLED)
 *                       dependency)-------------------------&gt; FAILED
 * </pre>
 *
 * A module that reaches {@code FAILED} stays there - it is never retried and
 * never transitions to {@code DISABLED}, since it was never actually
 * enabled. See docs/architecture/modules.md for how failure isolation works.
 */
public enum ModuleState {
    /** Registered with {@link ModuleRegistry}, nothing has run yet. */
    DISCOVERED,
    /** {@link Module#onLoad} ran successfully. */
    LOADED,
    /** {@link Module#onEnable} ran successfully; the module is active. */
    ENABLED,
    /** {@link Module#onDisable} ran (or was attempted) and resources were released. */
    DISABLED,
    /** {@code onLoad}/{@code onEnable} threw, or a dependency could not be satisfied. */
    FAILED
}
