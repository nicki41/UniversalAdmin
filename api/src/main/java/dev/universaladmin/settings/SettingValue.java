package dev.universaladmin.settings;

/** A setting's current resolved value alongside its static {@link SettingDefinition}. */
public record SettingValue<T>(SettingDefinition<T> definition, T value) {
}
