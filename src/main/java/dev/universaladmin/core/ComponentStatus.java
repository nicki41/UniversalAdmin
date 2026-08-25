package dev.universaladmin.core;

/**
 * Coarse health of a core component, as reported by {@link PluginStatus}.
 * Deliberately simple - a real health probe (e.g. an actual DB ping) is
 * future work; today's values reflect whether the component was
 * successfully constructed during bootstrap, not a live check.
 */
public enum ComponentStatus {
    ONLINE,
    DEGRADED,
    OFFLINE,
    /** The component doesn't exist yet (e.g. the web app - see docs/architecture/web-future.md). */
    NOT_IMPLEMENTED
}
