package dev.universaladmin.core;

import dev.universaladmin.module.ModuleId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A point-in-time snapshot of the plugin's health, built fresh on every call
 * to {@link UniversalAdmin#status()} - never cached, so {@link #uptime()}
 * and the module lists are always current. Used today by the {@code /admin}
 * command's status output; a future GUI/dashboard status page would render
 * the same snapshot.
 *
 * @param version        the plugin version from {@code plugin.yml}
 * @param startedAt      when the plugin finished its critical bootstrap phase
 * @param totalModules   every module registered, regardless of current state
 * @param activeModules  modules currently {@code ENABLED}
 * @param failedModules  modules currently {@code FAILED}
 * @param database       see {@link ComponentStatus} - reflects successful bootstrap, not a live probe
 * @param web            always {@code NOT_IMPLEMENTED} today; see docs/architecture/web-future.md
 */
public record PluginStatus(
        String version,
        Instant startedAt,
        int totalModules,
        List<ModuleId> activeModules,
        List<ModuleId> failedModules,
        ComponentStatus database,
        ComponentStatus web) {

    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }
}
