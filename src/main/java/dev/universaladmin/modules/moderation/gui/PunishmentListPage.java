package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentQuery;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One reusable, async, paginated punishment list - backs every list the
 * "Moderation Hauptseite" needs (Active/Recent/Warnings/Bans/Mutes) plus a
 * single target's history, each just a different {@link PunishmentQuery} and
 * title instead of five near-identical hand-rolled page classes. Uses
 * {@link AbstractListGuiPage} directly (unlike {@code AuditLogListPage} in
 * the Audit Log module) since none of these views need a persistent filter
 * toggle in the chrome row - the query is fixed per page instance.
 */
public final class PunishmentListPage extends AbstractListGuiPage<Punishment> {

    public static final GuiPageId ACTIVE_ID = GuiPageId.core("moderation.list.active");
    public static final GuiPageId RECENT_ID = GuiPageId.core("moderation.list.recent");
    public static final GuiPageId WARNINGS_ID = GuiPageId.core("moderation.list.warnings");
    public static final GuiPageId BANS_ID = GuiPageId.core("moderation.list.bans");
    public static final GuiPageId MUTES_ID = GuiPageId.core("moderation.list.mutes");
    public static final GuiPageId FROZEN_ID = GuiPageId.core("moderation.list.frozen");
    public static final GuiPageId TARGET_HISTORY_ID = GuiPageId.core("moderation.list.target-history");
    public static final GuiPageId TARGET_WARNINGS_ID = GuiPageId.core("moderation.list.target-warnings");

    private static final int BATCH_LIMIT = 200;

    private final ModerationGuiContext ctx;
    private final String titleKey;
    private final PunishmentQuery query;

    public PunishmentListPage(ModerationGuiContext ctx, GuiPageId id, String titleKey, PunishmentQuery query) {
        super(id, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.titleKey = titleKey;
        this.query = query;
    }

    public static PunishmentListPage active(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, ACTIVE_ID, "moderation.gui.list.active-title", PunishmentQuery.active(BATCH_LIMIT));
    }

    public static PunishmentListPage recent(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, RECENT_ID, "moderation.gui.list.recent-title", PunishmentQuery.recent(BATCH_LIMIT));
    }

    public static PunishmentListPage warnings(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, WARNINGS_ID, "moderation.gui.list.warnings-title",
                PunishmentQuery.ofTypes(Set.of(PunishmentType.WARN), BATCH_LIMIT));
    }

    public static PunishmentListPage bans(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, BANS_ID, "moderation.gui.list.bans-title",
                PunishmentQuery.ofTypes(Set.of(PunishmentType.BAN, PunishmentType.TEMP_BAN, PunishmentType.IP_BAN), BATCH_LIMIT));
    }

    public static PunishmentListPage mutes(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, MUTES_ID, "moderation.gui.list.mutes-title",
                PunishmentQuery.ofTypes(Set.of(PunishmentType.MUTE, PunishmentType.TEMP_MUTE), BATCH_LIMIT));
    }

    public static PunishmentListPage frozen(ModerationGuiContext ctx) {
        return new PunishmentListPage(ctx, FROZEN_ID, "moderation.gui.list.frozen-title",
                PunishmentQuery.ofTypes(Set.of(PunishmentType.FREEZE), BATCH_LIMIT));
    }

    public static PunishmentListPage forTarget(ModerationGuiContext ctx, UUID targetId) {
        return new PunishmentListPage(ctx, TARGET_HISTORY_ID, "moderation.gui.list.target-title", PunishmentQuery.forTarget(targetId, BATCH_LIMIT));
    }

    public static PunishmentListPage warningsForTarget(ModerationGuiContext ctx, UUID targetId) {
        return new PunishmentListPage(ctx, TARGET_WARNINGS_ID, "moderation.gui.list.target-warnings-title",
                new PunishmentQuery(targetId, Set.of(PunishmentType.WARN), null, BATCH_LIMIT));
    }

    @Override
    protected Component title(Player viewer) {
        return text(titleKey);
    }

    @Override
    protected CompletableFuture<List<Punishment>> loadItems(Player viewer) {
        return ctx.punishmentService().history(query);
    }

    @Override
    protected GuiItem render(Punishment punishment) {
        Material material = materialFor(punishment.type());
        Component name = Component.text(
                punishment.type().name() + " - " + punishment.targetLastKnownName(),
                punishment.active() ? NamedTextColor.RED : NamedTextColor.GRAY);
        String reason = punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("common.none"))
                : punishment.reason();
        List<Component> lore = List.of(
                text("moderation.gui.list.field-reason", reason),
                text("moderation.gui.list.field-by", punishment.actorName()),
                text("moderation.gui.list.field-created", ModerationFormat.instant(punishment.createdAt(), ctx.settings())),
                text("moderation.gui.list.field-expires", ModerationFormat.expiry(punishment.expiresAt(), ctx.settings(), messages)),
                punishment.active() ? text("moderation.gui.list.status-active") : text("moderation.gui.list.status-inactive"));
        return GuiItem.of(material, name, lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, Punishment punishment) {
        if (context.viewer().hasPermission(ModerationPermissions.VIEW.value())) {
            context.open(new PunishmentDetailPage(ctx, punishment));
        }
    }

    private static Material materialFor(PunishmentType type) {
        return switch (type) {
            case KICK -> Material.LEATHER_BOOTS;
            case BAN -> Material.BARRIER;
            case TEMP_BAN -> Material.IRON_BARS;
            case IP_BAN -> Material.REDSTONE_BLOCK;
            case MUTE, TEMP_MUTE -> Material.NOTE_BLOCK;
            case WARN -> Material.BOOK;
            case FREEZE -> Material.PACKED_ICE;
        };
    }
}
