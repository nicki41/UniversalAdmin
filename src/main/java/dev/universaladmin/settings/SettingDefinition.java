package dev.universaladmin.settings;

import java.util.Optional;

/**
 * A registered setting's full static specification: how to parse it
 * ({@link #type()}), what it defaults to, whether it can be changed without
 * a restart, and how to validate it. See {@link SettingRegistry} for where
 * these get registered and {@link YamlSettingsService} for how they get
 * resolved into a live {@link SettingValue}.
 *
 * @param key             namespaced identifier / config path, see {@link SettingKey}
 * @param type            parsing logic, see {@link SettingTypes}
 * @param defaultValue    used when the key is absent from {@code config.yml}, and as the
 *                        fallback when the configured value fails to parse or validate
 * @param description     one-line explanation, shown in docs and (later) a settings GUI
 * @param requiresRestart if true, a changed value is not applied by {@code /admin reload} -
 *                        it is reported as pending and only takes effect on the next start
 * @param validator       extra constraints beyond what {@code type} alone enforces (min/max, ...)
 */
public record SettingDefinition<T>(
        SettingKey<T> key,
        SettingType<T> type,
        T defaultValue,
        String description,
        boolean requiresRestart,
        SettingValidator<T> validator) {

    public SettingDefinition {
        Optional<String> defaultError = validator.validate(defaultValue);
        if (defaultError.isPresent()) {
            throw new IllegalArgumentException(
                    "Default value for " + key + " fails its own validator: " + defaultError.get());
        }
    }

    public static <T> Builder<T> builder(SettingKey<T> key, SettingType<T> type, T defaultValue) {
        return new Builder<>(key, type, defaultValue);
    }

    public static final class Builder<T> {

        private final SettingKey<T> key;
        private final SettingType<T> type;
        private final T defaultValue;
        private String description = "";
        private boolean requiresRestart = false;
        private SettingValidator<T> validator = SettingValidators.none();

        private Builder(SettingKey<T> key, SettingType<T> type, T defaultValue) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<T> requiresRestart(boolean requiresRestart) {
            this.requiresRestart = requiresRestart;
            return this;
        }

        public Builder<T> validator(SettingValidator<T> validator) {
            this.validator = validator;
            return this;
        }

        public SettingDefinition<T> build() {
            return new SettingDefinition<>(key, type, defaultValue, description, requiresRestart, validator);
        }
    }
}
