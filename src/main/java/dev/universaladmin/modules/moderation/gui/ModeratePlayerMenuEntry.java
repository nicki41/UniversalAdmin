package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.gui.PlayerMenuGroup;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.permission.PermissionNode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The "Moderate" button on a player's profile page, opening {@link ModeratePlayerPage} for that player. */
public final class ModeratePlayerMenuEntry implements PlayerMenuEntry {

    private final ModerationGuiContext ctx;

    public ModeratePlayerMenuEntry(ModerationGuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "core:moderation.moderate";
    }

    @Override
    public PlayerMenuGroup group() {
        return PlayerMenuGroup.MODERATE;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public PermissionNode permission() {
        return ModerationPermissions.USE;
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        return GuiItem.of(Material.IRON_SWORD,
                ComponentMessages.render(ctx.messages().get(MessageKey.of("players.gui.profile.moderate"))));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        context.open(new ModeratePlayerPage(ctx, target.id(), target.name()));
    }
}
