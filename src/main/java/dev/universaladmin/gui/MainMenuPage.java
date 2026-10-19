package dev.universaladmin.gui;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin}'s home screen: one button per {@link ModuleState#ENABLED}
 * module that declares a main-menu entry ({@link ModuleDescriptor#homePage()}),
 * ordered by {@link ModuleDescriptor#menuOrder()} - built-in modules and
 * extensions alike, nothing here knows a module by name. A button is hidden
 * (not shown disabled) when the viewer lacks the module's
 * {@link ModuleDescriptor#menuPermission()}, and a module whose home page is
 * not registered (yet) is skipped rather than shown as a dead button.
 */
public final class MainMenuPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("main-menu");

    private final ModuleRegistry moduleRegistry;
    private final GuiRegistry guiPages;

    public MainMenuPage(GuiFramework framework, MessageService messages, ModuleRegistry moduleRegistry, GuiRegistry guiPages) {
        super(ID, framework, messages);
        this.moduleRegistry = moduleRegistry;
        this.guiPages = guiPages;
    }

    @Override
    protected Component title(Player viewer) {
        return text("gui.main-menu.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        List<ModuleDescriptor> entries = moduleRegistry.withState(ModuleState.ENABLED).stream()
                .map(Module::descriptor)
                .filter(descriptor -> descriptor.homePage() != null && guiPages.get(descriptor.homePage()).isPresent())
                .sorted(Comparator.comparingInt(ModuleDescriptor::menuOrder).thenComparing(ModuleDescriptor::displayName))
                .toList();

        int index = 0;
        for (ModuleDescriptor descriptor : entries) {
            if (index >= GuiLayout.contentSize()) {
                break;
            }
            GuiButton button = new GuiButton(item(descriptor), descriptor.menuPermission(),
                    ctx -> guiPages.get(descriptor.homePage()).ifPresent(ctx::open));
            if (!button.isVisibleTo(viewer)) {
                continue;
            }
            view.place(GuiLayout.contentSlot(index++), button, viewer);
        }

        if (index == 0) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                    GuiItem.of(framework.icons().empty(), text("gui.empty")));
        }
    }

    /**
     * Name and description come from {@code menu.<namespace>.<module>.name}/
     * {@code .description} when the active locale (or an extension's bundled
     * defaults) has them, otherwise from the descriptor's own English text.
     */
    private GuiItem item(ModuleDescriptor descriptor) {
        Material material = descriptor.icon() != null ? framework.icons().resolve(descriptor.icon()) : Material.PAPER;
        String base = "menu." + descriptor.id().key().namespace() + "." + descriptor.id().key().name();
        Component name = localizedOr(base + ".name", descriptor.displayName()).colorIfAbsent(NamedTextColor.GOLD);
        Component description = localizedOr(base + ".description", descriptor.description()).colorIfAbsent(NamedTextColor.GRAY);
        return GuiItem.of(material, name, List.of(description));
    }

    private Component localizedOr(String key, String fallback) {
        MessageKey messageKey = MessageKey.of(key);
        return messages.has(messageKey) ? ComponentMessages.render(messages.get(messageKey)) : Component.text(fallback);
    }
}
