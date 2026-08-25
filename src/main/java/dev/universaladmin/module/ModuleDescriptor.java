package dev.universaladmin.module;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Static metadata about a {@link Module} - everything knowable about it
 * without running any of its lifecycle methods. {@link ModuleRegistry} and
 * {@link ModuleManager} use this to enforce unique IDs, order modules by
 * {@link #dependencies()}, and (later) render a module list in a GUI/status
 * page without instantiating services.
 *
 * @param id                 unique, namespaced identifier
 * @param displayName        human-readable name shown in GUI/commands, e.g. "Players"
 * @param description        one-line summary of what the module does
 * @param dependencies        other modules that must be {@code ENABLED} before this one can enable
 * @param settingsNamespace   reserved key under which the future Settings module stores this module's config
 * @param permissionNamespace convention: this module's nodes are expected to live under {@code universaladmin.<permissionNamespace>.*}
 * @param icon                optional future GUI/dashboard icon; {@code null} until a GUI framework exists
 */
public record ModuleDescriptor(
        ModuleId id,
        String displayName,
        String description,
        Set<ModuleId> dependencies,
        String settingsNamespace,
        String permissionNamespace,
        GuiIcon icon) {

    public ModuleDescriptor {
        dependencies = Set.copyOf(dependencies);
    }

    public static Builder builder(ModuleId id, String displayName) {
        return new Builder(id, displayName);
    }

    /**
     * Builds a {@link ModuleDescriptor}. Not a general-purpose framework -
     * just a readable way to construct a record with several optional
     * fields; every built-in module uses one in its constructor.
     */
    public static final class Builder {

        private final ModuleId id;
        private final String displayName;
        private String description = "";
        private final Set<ModuleId> dependencies = new LinkedHashSet<>();
        private String settingsNamespace;
        private String permissionNamespace;
        private GuiIcon icon;

        private Builder(ModuleId id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            // Sensible default for built-in modules: the bare module name
            // (e.g. "players"), matching the existing PermissionNode.core(...)
            // convention. Override explicitly if a module needs to differ.
            this.settingsNamespace = id.key().name();
            this.permissionNamespace = id.key().name();
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dependsOn(ModuleId... moduleIds) {
            dependencies.addAll(List.of(moduleIds));
            return this;
        }

        public Builder settingsNamespace(String settingsNamespace) {
            this.settingsNamespace = settingsNamespace;
            return this;
        }

        public Builder permissionNamespace(String permissionNamespace) {
            this.permissionNamespace = permissionNamespace;
            return this;
        }

        public Builder icon(GuiIcon icon) {
            this.icon = icon;
            return this;
        }

        public ModuleDescriptor build() {
            return new ModuleDescriptor(
                    id, displayName, description, dependencies, settingsNamespace, permissionNamespace, icon);
        }
    }
}
