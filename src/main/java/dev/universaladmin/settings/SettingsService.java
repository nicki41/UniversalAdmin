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
}
