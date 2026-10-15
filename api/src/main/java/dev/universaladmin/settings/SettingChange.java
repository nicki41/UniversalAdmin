package dev.universaladmin.settings;

/**
 * Outcome of {@link SettingsService#set}/{@link SettingsService#reset}.
 *
 * @param definition     the setting that was written
 * @param oldValue       the previous live value, formatted like the user would type it
 * @param newValue       the value now stored in {@code config.yml}, formatted the same way
 * @param pendingRestart {@code true} if the setting {@link SettingDefinition#requiresRestart() requires a restart}:
 *                       the new value is saved but the old one stays in effect until then
 */
public record SettingChange(SettingDefinition<?> definition, String oldValue, String newValue, boolean pendingRestart) {
}
