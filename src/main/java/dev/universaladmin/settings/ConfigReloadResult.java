package dev.universaladmin.settings;

import java.util.List;

/**
 * Outcome of a {@link SettingsService#reload()}.
 *
 * @param applied          settings whose live value was updated from the freshly-read config
 * @param pendingRestart   settings whose config value changed but {@link SettingDefinition#requiresRestart()}
 *                         is true - their old value is still in effect until the next restart
 * @param validationErrors human-readable messages for values that failed to parse/validate and
 *                         fell back to their default (see docs/architecture/overview.md - settings
 *                         never crash the server, they fall back and report)
 */
public record ConfigReloadResult(
        List<SettingKey<?>> applied, List<SettingKey<?>> pendingRestart, List<String> validationErrors) {
}
