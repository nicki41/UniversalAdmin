package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.gui.PlayerMenuGroup;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.gui.ModeratePlayerPage.WizardType;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One punishment as a button in the moderation row of a player's profile -
 * warn, temp mute, kick, temp ban and freeze (unfreeze while they are
 * frozen). The same steps as on the Moderate page, which stays first in the
 * row for everything else; afterwards the profile is shown again.
 */
public final class ModerationQuickEntry implements PlayerMenuEntry {

    private final ModerationGuiContext ctx;
    private final WizardType type;
    private final WizardType lift;
    private final Predicate<UUID> active;
    private final int order;

    private ModerationQuickEntry(ModerationGuiContext ctx, WizardType type, WizardType lift, Predicate<UUID> active, int order) {
        this.ctx = ctx;
        this.type = type;
        this.lift = lift;
        this.active = active;
        this.order = order;
    }

    /** The row's buttons after "Moderate"; {@code frozen} tells whether a player is frozen right now. */
    public static List<PlayerMenuEntry> all(ModerationGuiContext ctx, Predicate<UUID> frozen) {
        Predicate<UUID> never = id -> false;
        return List.of(
                new ModerationQuickEntry(ctx, WizardType.WARN, null, never, 11),
                new ModerationQuickEntry(ctx, WizardType.TEMP_MUTE, null, never, 12),
                new ModerationQuickEntry(ctx, WizardType.KICK, null, never, 13),
                new ModerationQuickEntry(ctx, WizardType.TEMP_BAN, null, never, 14),
                new ModerationQuickEntry(ctx, WizardType.FREEZE, WizardType.UNFREEZE, frozen, 15));
    }

    /** The player's punishment history, in the profile's "view" row. */
    public static PlayerMenuEntry history(ModerationGuiContext ctx) {
        return new PlayerMenuEntry() {
            @Override
            public String id() {
                return "core:moderation.history";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public PlayerMenuGroup group() {
                return PlayerMenuGroup.VIEW;
            }

            @Override
            public PermissionNode permission() {
                return ModerationPermissions.VIEW;
            }

            @Override
            public GuiItem icon(Player viewer, PlayerRef target) {
                return GuiItem.of(Material.WRITTEN_BOOK,
                        ComponentMessages.render(ctx.messages().get(MessageKey.of("moderation.gui.moderate.history"))));
            }

            @Override
            public void onClick(GuiClickContext context, PlayerRef target) {
                context.open(PunishmentListPage.forTarget(ctx, target.id()));
            }
        };
    }

    @Override
    public String id() {
        return "core:moderation.quick." + type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public PlayerMenuGroup group() {
        return PlayerMenuGroup.MODERATE;
    }

    @Override
    public PermissionNode permission() {
        return type.permission();
    }

    @Override
    public boolean requiresOnline() {
        return type == WizardType.KICK;
    }

    @Override
    public boolean visible(Player viewer, PlayerRef target) {
        WizardType shown = shown(target);
        return shown == type || viewer.hasPermission(shown.permission().value());
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        WizardType shown = shown(target);
        return GuiItem.of(shown.material(), ComponentMessages.render(ctx.messages().get(MessageKey.of(shown.labelKey()))));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        ModeratePlayerPage.start(ctx, context, target.id(), target.name(), shown(target));
    }

    private WizardType shown(PlayerRef target) {
        return lift != null && active.test(target.id()) ? lift : type;
    }
}
