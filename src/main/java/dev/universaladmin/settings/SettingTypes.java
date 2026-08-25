package dev.universaladmin.settings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in {@link SettingType}s every {@link SettingDefinition} is
 * expected to use. Adding a genuinely new primitive type (not just a new
 * validator on an existing one) is rare and deliberate - see
 * docs/architecture/decisions or docs/development/settings.md before adding
 * one here.
 */
public final class SettingTypes {

    private SettingTypes() {
    }

    public static final SettingType<String> STRING = new SettingType<>() {
        @Override
        public String parse(Object raw) {
            return String.valueOf(raw);
        }

        @Override
        public String describe() {
            return "a string";
        }
    };

    public static final SettingType<Boolean> BOOLEAN = new SettingType<>() {
        @Override
        public Boolean parse(Object raw) throws SettingParseException {
            if (raw instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(raw).trim();
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
            throw new SettingParseException("expected true or false (was '" + raw + "')");
        }

        @Override
        public String describe() {
            return "true or false";
        }
    };

    public static final SettingType<Integer> INTEGER = new SettingType<>() {
        @Override
        public Integer parse(Object raw) throws SettingParseException {
            if (raw instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SettingParseException("expected a whole number (was '" + raw + "')");
            }
        }

        @Override
        public String describe() {
            return "a whole number";
        }
    };

    public static final SettingType<Long> LONG = new SettingType<>() {
        @Override
        public Long parse(Object raw) throws SettingParseException {
            if (raw instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SettingParseException("expected a whole number (was '" + raw + "')");
            }
        }

        @Override
        public String describe() {
            return "a whole number";
        }
    };

    public static final SettingType<Double> DOUBLE = new SettingType<>() {
        @Override
        public Double parse(Object raw) throws SettingParseException {
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SettingParseException("expected a number (was '" + raw + "')");
            }
        }

        @Override
        public String describe() {
            return "a number";
        }
    };

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)^(\\d+)\\s*(ms|s|m|h|d)?$");

    /** Accepts a bare number of seconds, or a suffixed value: {@code 30s}, {@code 5m}, {@code 1h}, {@code 2d}. */
    public static final SettingType<Duration> DURATION = new SettingType<>() {
        @Override
        public Duration parse(Object raw) throws SettingParseException {
            if (raw instanceof Number number) {
                return Duration.ofSeconds(number.longValue());
            }
            String text = String.valueOf(raw).trim();
            Matcher matcher = DURATION_PATTERN.matcher(text);
            if (!matcher.matches()) {
                throw new SettingParseException("expected " + describe() + " (was '" + raw + "')");
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
            return switch (unit) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new SettingParseException("unknown duration unit '" + unit + "'");
            };
        }

        @Override
        public String describe() {
            return "a duration like '30s', '5m', '1h', or '2d'";
        }
    };

    public static final SettingType<List<String>> STRING_LIST = new SettingType<>() {
        @Override
        public List<String> parse(Object raw) throws SettingParseException {
            if (!(raw instanceof List<?> list)) {
                throw new SettingParseException("expected a list of strings (was '" + raw + "')");
            }
            List<String> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(String.valueOf(element));
            }
            return List.copyOf(result);
        }

        @Override
        public String describe() {
            return "a list of strings";
        }
    };

    /** Case-insensitive; accepts hyphens as underscores (e.g. {@code "not-op"} for {@code NOT_OP}). */
    public static <E extends Enum<E>> SettingType<E> enumOf(Class<E> enumClass) {
        return new SettingType<>() {
            @Override
            public E parse(Object raw) throws SettingParseException {
                String text = String.valueOf(raw).trim().toUpperCase(Locale.ROOT).replace('-', '_');
                try {
                    return Enum.valueOf(enumClass, text);
                } catch (IllegalArgumentException e) {
                    throw new SettingParseException("expected " + describe() + " (was '" + raw + "')");
                }
            }

            @Override
            public String describe() {
                return "one of " + Arrays.toString(enumClass.getEnumConstants());
            }
        };
    }
}
