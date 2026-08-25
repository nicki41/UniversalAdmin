package dev.universaladmin.settings;

/**
 * Thrown by a {@link SettingType} when a raw YAML value can't be parsed.
 * Checked, not a {@code RuntimeException}: a malformed value in a user's
 * {@code config.yml} is an expected, recoverable situation (see
 * {@link YamlSettingsService}, which catches this and falls back to the
 * setting's default instead of crashing), not a programming error.
 */
public final class SettingParseException extends Exception {

    public SettingParseException(String message) {
        super(message);
    }
}
