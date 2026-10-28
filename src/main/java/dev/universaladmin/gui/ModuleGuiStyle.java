package dev.universaladmin.gui;

import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleRegistry;
import java.util.Optional;
import org.bukkit.Material;

/**
 * {@link GuiStyle} from the modules themselves: a page belongs to the module
 * whose id its own id starts with ({@code core:players.online} belongs to
 * {@code core:players}), and takes that module's colour and icon from its
 * {@link ModuleDescriptor}. A page of an extension whose ids do not follow
 * that pattern is matched by namespace alone. The main menu is the start.
 */
public final class ModuleGuiStyle implements GuiStyle {

    private final ModuleRegistry modules;
    private final IconProvider icons;
    private final GuiRegistry pages;

    public ModuleGuiStyle(ModuleRegistry modules, IconProvider icons, GuiRegistry pages) {
        this.modules = modules;
        this.icons = icons;
        this.pages = pages;
    }

    @Override
    public GuiGlass accent(GuiPageId page) {
        if (page.equals(MainMenuPage.ID)) {
            return GuiGlass.PURPLE;
        }
        return module(page).map(ModuleDescriptor::color).orElse(GuiGlass.GRAY);
    }

    @Override
    public Material icon(GuiPageId page) {
        if (page.equals(MainMenuPage.ID)) {
            return Material.NETHER_STAR;
        }
        return module(page).filter(descriptor -> descriptor.icon() != null)
                .map(descriptor -> icons.resolve(descriptor.icon()))
                .orElse(Material.BOOK);
    }

    @Override
    public Optional<GuiPage> home() {
        return pages.get(MainMenuPage.ID);
    }

    private Optional<ModuleDescriptor> module(GuiPageId page) {
        String namespace = page.key().namespace();
        String name = page.key().name();
        String prefix = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        Optional<ModuleDescriptor> exact = modules.all().stream().map(Module::descriptor)
                .filter(descriptor -> descriptor.id().key().namespace().equals(namespace)
                        && descriptor.id().key().name().equals(prefix))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return modules.all().stream().map(Module::descriptor)
                .filter(descriptor -> descriptor.id().key().namespace().equals(namespace))
                .findFirst();
    }
}
