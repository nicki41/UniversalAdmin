package dev.universaladmin.settings;

import java.util.Optional;

/**
 * Checks a successfully-parsed value against constraints {@link SettingType}
 * parsing alone can't express - min/max ranges, string patterns, and so on.
 * See {@link SettingValidators} for the built-in set.
 *
 * @param <T> the value type being validated
 */
@FunctionalInterface
public interface SettingValidator<T> {

    /** Returns an error message describing why {@code value} is invalid, or empty if it's fine. */
    Optional<String> validate(T value);

    default SettingValidator<T> and(SettingValidator<T> other) {
        return value -> validate(value).or(() -> other.validate(value));
    }
}
