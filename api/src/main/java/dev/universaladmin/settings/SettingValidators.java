package dev.universaladmin.settings;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/** Built-in {@link SettingValidator}s covering the common "min/max wo sinnvoll" cases. */
public final class SettingValidators {

    private SettingValidators() {
    }

    public static <T> SettingValidator<T> none() {
        return value -> Optional.empty();
    }

    public static SettingValidator<Integer> intRange(int min, int max) {
        return value -> (value < min || value > max)
                ? Optional.of("must be between " + min + " and " + max + " (was " + value + ")")
                : Optional.empty();
    }

    public static SettingValidator<Long> longRange(long min, long max) {
        return value -> (value < min || value > max)
                ? Optional.of("must be between " + min + " and " + max + " (was " + value + ")")
                : Optional.empty();
    }

    public static SettingValidator<Double> doubleRange(double min, double max) {
        return value -> (value < min || value > max)
                ? Optional.of("must be between " + min + " and " + max + " (was " + value + ")")
                : Optional.empty();
    }

    public static SettingValidator<Duration> durationRange(Duration min, Duration max) {
        return value -> (value.compareTo(min) < 0 || value.compareTo(max) > 0)
                ? Optional.of("must be between " + min + " and " + max + " (was " + value + ")")
                : Optional.empty();
    }

    public static SettingValidator<Integer> multipleOf(int factor) {
        return value -> (value % factor != 0)
                ? Optional.of("must be a multiple of " + factor + " (was " + value + ")")
                : Optional.empty();
    }

    public static SettingValidator<String> notBlank() {
        return value -> value.isBlank() ? Optional.of("must not be blank") : Optional.empty();
    }

    public static SettingValidator<String> matches(String regex, String description) {
        Pattern pattern = Pattern.compile(regex);
        return value -> pattern.matcher(value).matches()
                ? Optional.empty()
                : Optional.of("must be " + description + " (was '" + value + "')");
    }
}
