package dev.universaladmin.extension;

import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleState;

/**
 * One extension-contributed module, as listed by {@link ExtensionManager#extensions()}.
 *
 * @param pluginName    the owning plugin's name
 * @param pluginVersion the owning plugin's version
 * @param moduleId      the module it registered
 * @param state         the module's current state
 */
public record ExtensionInfo(String pluginName, String pluginVersion, ModuleId moduleId, ModuleState state) {
}
