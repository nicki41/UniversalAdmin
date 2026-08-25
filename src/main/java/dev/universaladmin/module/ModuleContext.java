package dev.universaladmin.module;

import dev.universaladmin.core.UniversalAdmin;
import java.util.logging.Logger;

/**
 * What a {@link Module} receives on {@link Module#onLoad}/{@link Module#onEnable}/{@link Module#onDisable}.
 *
 * <p>A context is a module-scoped view over the shared {@link UniversalAdmin}
 * container: the same registries and services every module (built-in or,
 * later, external) uses, plus a logger already prefixed with the module id
 * and a {@link ModuleResources} for anything that needs cleanup on disable.
 *
 * <p>One context is built per module by {@link ModuleManager} and reused
 * across all three lifecycle calls, so resources registered in
 * {@code onEnable} are the same ones released after {@code onDisable}.
 */
public record ModuleContext(ModuleId moduleId, UniversalAdmin platform, Logger logger, ModuleResources resources) {

    static ModuleContext of(ModuleId moduleId, UniversalAdmin platform) {
        Logger moduleLogger = Logger.getLogger(platform.logger().getName() + "." + moduleId);
        ModuleResources resources = new ModuleResources(platform.plugin(), moduleId);
        return new ModuleContext(moduleId, platform, moduleLogger, resources);
    }
}
