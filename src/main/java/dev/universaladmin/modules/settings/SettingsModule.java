package dev.universaladmin.modules.settings;

import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;

/**
 * User-facing module over the core {@link dev.universaladmin.settings.SettingsService}.
 * Owns the GUI/command surface for viewing/editing plugin settings in-game -
 * not yet implemented, see ROADMAP.md. The typed settings system itself
 * (registration, validation, reload) already exists - see
 * docs/development/settings.md - only this module's UI is still a skeleton.
 */
public final class SettingsModule implements Module {

    public static final ModuleId ID = ModuleId.core("settings");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Settings")
            .description("Views/edits plugin settings in-game via SettingsService.")
            .icon(new GuiIcon("anvil", "Settings"))
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        context.platform().permissions().register(new PermissionDefinition(
                PermissionNode.core("settings.manage"), "Manage UniversalAdmin settings", PermissionDefault.OP));
    }
}
