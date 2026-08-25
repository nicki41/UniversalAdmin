package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * "Not built yet" landing page for a module whose real GUI doesn't exist
 * yet - see the "Main Menu Skeleton" section of docs/development/gui-framework.md.
 * One reusable class instead of eight near-identical ones; a module's real
 * GUI later replaces this by registering its own page under the same
 * {@link GuiPageId}, since {@code GuiRegistry} only allows one owner per id.
 */
public final class PlaceholderGuiPage extends AbstractGuiPage {

    private final String moduleDisplayName;

    public PlaceholderGuiPage(GuiPageId id, GuiFramework framework, MessageService messages, String moduleDisplayName) {
        super(id, framework, messages);
        this.moduleDisplayName = moduleDisplayName;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(moduleDisplayName);
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        context.view().place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                GuiItem.of(framework.icons().empty(), text("gui.placeholder.body", moduleDisplayName)));
    }
}
