package dev.universaladmin.modules.reports.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.reports.ReportsPermissions;
import dev.universaladmin.permission.PermissionNode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The "Reports" button on a player's profile: every report filed about that player. */
public final class ReportsPlayerMenuEntry implements PlayerMenuEntry {

    private final ReportsGuiContext ctx;

    public ReportsPlayerMenuEntry(ReportsGuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "core:reports.about";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public PermissionNode permission() {
        return ReportsPermissions.VIEW;
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        return GuiItem.of(Material.BELL, ComponentMessages.render(ctx.messages().get(MessageKey.of("reports.gui.profile-button"))));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        context.open(new ReportListPage(ctx, target));
    }
}
