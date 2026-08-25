package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin}'s home screen: one button per built-in module, each
 * gated on that module actually being {@link ModuleState#ENABLED} (config-
 * disabled or failed modules are skipped entirely, not shown disabled) and
 * on the viewer holding the module's permission (hidden, per
 * {@link GuiButton} - see docs/development/gui-framework.md).
 *
 * <p>References each built-in module only by its {@link ModuleId} literal
 * (matching the {@code ModuleId.core(...)} each module class already
 * defines for itself), never by importing the concrete {@code modules.*}
 * class - this package is generic GUI framework and must not depend
 * upward on specific built-in modules; see docs/development/architecture-rules.md's "Package Rules"
 * section (built-in modules live under {@code dev.universaladmin.modules.*},
 * separate from the platform-wide abstractions under {@code dev.universaladmin.*}
 * that this class belongs to).
 *
 * <p>Every target page is a {@link PlaceholderGuiPage} today (see ROADMAP.md
 * Phase 1/2) - a module's real GUI later takes over the same
 * {@link GuiPageId} by registering its own page there instead.
 */
public final class MainMenuPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("main-menu");

    private final ModuleRegistry moduleRegistry;
    private final GuiRegistry guiPages;
    private final List<Entry> entries;

    public MainMenuPage(GuiFramework framework, MessageService messages, ModuleRegistry moduleRegistry, GuiRegistry guiPages) {
        super(ID, framework, messages);
        this.moduleRegistry = moduleRegistry;
        this.guiPages = guiPages;
        this.entries = List.of(
                new Entry(ModuleId.core("players"), PermissionNode.core("players.view"), "players.home"),
                new Entry(ModuleId.core("moderation"), PermissionNode.core("moderation.use"), "moderation.home"),
                new Entry(ModuleId.core("server"), PermissionNode.core("server.view"), "server.home"),
                new Entry(ModuleId.core("worlds"), PermissionNode.core("worlds.view"), "worlds.home"),
                new Entry(ModuleId.core("whitelist"), PermissionNode.core("whitelist.view"), "whitelist.home"),
                new Entry(ModuleId.core("performance"), PermissionNode.core("performance.view"), "performance.home"),
                new Entry(ModuleId.core("audit-log"), PermissionNode.core("audit.view"), "audit-log.home"),
                new Entry(ModuleId.core("settings"), PermissionNode.core("settings.manage"), "settings.home"));
    }

    @Override
    protected Component title(Player viewer) {
        return text("gui.main-menu.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        int slot = GuiLayout.CONTENT_START_SLOT;

        for (Entry entry : entries) {
            if (!isEnabled(entry.moduleId()) || !viewer.hasPermission(entry.permission().value())) {
                continue;
            }
            Module module = moduleRegistry.get(entry.moduleId()).orElseThrow();
            ModuleDescriptor descriptor = module.descriptor();
            Material material = descriptor.icon() != null ? framework.icons().resolve(descriptor.icon()) : Material.PAPER;
            GuiItem item = GuiItem.of(material, Component.text(descriptor.displayName()),
                    List.of(Component.text(descriptor.description())));

            view.place(slot++, new GuiButton(item, entry.permission(),
                    ctx -> guiPages.get(entry.targetPage()).ifPresent(ctx::open)), viewer);
        }

        if (slot == GuiLayout.CONTENT_START_SLOT) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                    GuiItem.of(framework.icons().empty(), text("gui.empty")));
        }
    }

    private boolean isEnabled(ModuleId id) {
        return moduleRegistry.get(id).isPresent() && moduleRegistry.state(id) == ModuleState.ENABLED;
    }

    private record Entry(ModuleId moduleId, PermissionNode permission, String targetPageName) {
        GuiPageId targetPage() {
            return GuiPageId.core(targetPageName);
        }
    }
}
