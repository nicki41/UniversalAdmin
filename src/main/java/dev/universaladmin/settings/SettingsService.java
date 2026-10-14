package dev.universaladmin.settings;

/**
 * Typed, validated access to every registered setting's current value. This
 * is the one thing services/actions/modules depend on instead of reading
 * {@code config.yml} directly - see docs/development/settings.md.
 *
 * <p>Reading a setting never fails: an unset key returns the definition's
 * default, and a value that fails to parse or validate is logged and also
 * falls back to the default (see {@link YamlSettingsService}).
 */
public interface SettingsService {

    /** @throws IllegalArgumentException if {@code key} was never registered with the {@link SettingRegistry} */
    <T> T get(SettingKey<T> key);

    /** Like {@link #get}, but also returns the {@link SettingDefinition} (description, default, ...). */
    <T> SettingValue<T> getValue(SettingKey<T> key);

    /**
     * Re-reads {@code config.yml} and re-resolves every registered setting.
     * Never throws for a bad value - see {@link ConfigReloadResult}. Runs
     * synchronously wherever it's called from; see {@link dev.universaladmin.settings.ReloadConfigAction}
     * for why the actual {@code /admin reload} command runs this off the
     * main thread.
     */
    ConfigReloadResult reload();

    /**
     * Parses {@code rawValue} as typed by a staff member (see
     * {@link SettingType#parseText}), validates it, writes it to
     * {@code config.yml} and - unless the setting requires a restart -
     * applies it immediately. Comments and every other key in the file are
     * preserved.
     *
     * @throws IllegalArgumentException if no setting is registered at {@code configPath}
     * @throws SettingParseException    if the value does not parse or fails validation (nothing is written)
     * @throws UnsupportedOperationException for a read-only implementation (the default)
     */
    default SettingChange set(String configPath, String rawValue) throws SettingParseException {
        throw new UnsupportedOperationException("These settings are read-only");
    }

    /** Removes the setting from {@code config.yml}, so its default applies again - same rules as {@link #set}. */
    default SettingChange reset(String configPath) {
        throw new UnsupportedOperationException("These settings are read-only");
    }

    /** The current value of the setting at {@code configPath}, formatted for display - never for a sensitive setting. */
    default String display(SettingDefinition<?> definition) {
        return definition.sensitive() ? "******" : formatCurrent(definition);
    }

    private <T> String formatCurrent(SettingDefinition<T> definition) {
        return definition.type().format(get(definition.key()));
    }
}
