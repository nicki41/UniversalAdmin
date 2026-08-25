package dev.universaladmin.settings;

/**
 * Knows how to turn a raw value from a YAML file (whatever SnakeYAML decoded
 * it to - a {@code String}, {@code Boolean}, {@code Integer}, {@code List<?>},
 * ...) into a typed {@code T}. This is the one place per supported type that
 * parsing logic lives - see {@link SettingTypes} for the built-in set
 * (String, boolean, int, long, double, Duration, enum, string list).
 *
 * @param <T> the parsed value type
 */
public interface SettingType<T> {

    T parse(Object raw) throws SettingParseException;

    /** Human-readable description used in error messages, e.g. {@code "a duration like 30s, 5m, 1h"}. */
    String describe();
}
