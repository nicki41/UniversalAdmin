package dev.universaladmin.settings;

import java.util.List;

/**
 * Knows how to turn a raw value from a YAML file (whatever SnakeYAML decoded
 * it to - a {@code String}, {@code Boolean}, {@code Integer}, {@code List<?>},
 * ...) into a typed {@code T}. This is the one place per supported type that
 * parsing logic lives - see {@link SettingTypes} for the built-in set
 * (String, boolean, int, long, double, Duration, enum, string list).
 *
 * <p>The text-facing methods ({@link #parseText}, {@link #format},
 * {@link #suggestions}) and {@link #toYaml} exist for editing a setting
 * in-game (settings GUI, {@code /admin settings set}); their defaults suit
 * most scalar types.
 *
 * @param <T> the parsed value type
 */
public interface SettingType<T> {

    T parse(Object raw) throws SettingParseException;

    /** Human-readable description used in error messages, e.g. {@code "a duration like 30s, 5m, 1h"}. */
    String describe();

    /** Parses what a staff member typed (rather than what YAML decoded) - e.g. {@code a, b, c} for a list. */
    default T parseText(String text) throws SettingParseException {
        return parse(text);
    }

    /** {@code value} as a staff member would type it back, so {@code parseText(format(v))} round-trips. */
    default String format(T value) {
        return String.valueOf(value);
    }

    /** What to write into {@code config.yml} for {@code value} - must be readable by {@link #parse}. */
    default Object toYaml(T value) {
        return value;
    }

    /** Every valid value, for types with a small closed set (booleans, enums); empty otherwise. */
    default List<String> suggestions() {
        return List.of();
    }
}
