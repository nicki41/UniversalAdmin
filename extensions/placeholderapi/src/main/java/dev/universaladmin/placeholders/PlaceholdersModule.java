package dev.universaladmin.placeholders;

import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;

/**
 * Registers the {@code %universaladmin_...%} expansion with PlaceholderAPI
 * while the module is enabled - so it shows up in {@code /admin modules} and
 * goes away cleanly when the plugin is disabled.
 */
final class PlaceholdersModule implements Module {

    static final ModuleId ID = ModuleId.of("placeholders", "placeholderapi");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Placeholders")
            .description("PlaceholderAPI placeholders: vanish, mutes, warnings, reports, maintenance, network.")
            .icon(new GuiIcon("name_tag", "Placeholders"))
            .build();

    private final UniversalAdminExpansion expansion;

    PlaceholdersModule(UniversalAdminExpansion expansion) {
        this.expansion = expansion;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        if (!expansion.register()) {
            context.logger().warning("PlaceholderAPI refused the %universaladmin_...% placeholders - is another expansion "
                    + "with the identifier 'universaladmin' installed?");
            return;
        }
        context.resources().closeable(expansion::unregister);
    }
}
