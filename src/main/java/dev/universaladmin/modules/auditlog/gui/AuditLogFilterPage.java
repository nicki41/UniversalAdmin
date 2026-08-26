package dev.universaladmin.modules.auditlog.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiSession;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The actor/module/time-range filter menu for {@link AuditLogListPage} - its
 * own success/failure toggle stays a one-click hopper in the list's chrome
 * row (see {@code AuditLogListPage.Filter}); the dimensions here need more
 * than one click to pick, so they get a page of their own instead of
 * crowding the list's chrome. Every choice here is stored into the same
 * {@link GuiSession} {@link AuditLogListPage} reads from (see its
 * {@code ATTR_*} constants) and then hands control straight back to the list
 * via {@link dev.universaladmin.gui.GuiClickContext#back()} - see
 * docs/user/audit-log.md. Ephemeral, never registered in {@code GuiRegistry}.
 */
public final class AuditLogFilterPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("audit-log.filter");

    private static final List<String> MODULES =
            List.of("moderation", "players", "worlds", "whitelist", "performance", "server", "settings", "audit-log");

    private final TaskScheduler scheduler;

    public AuditLogFilterPage(GuiFramework framework, MessageService messages, TaskScheduler scheduler) {
        super(ID, framework, messages);
        this.scheduler = scheduler;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("audit.gui.filter.page-title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        GuiSession session = context.session();

        placeActorButtons(view, viewer, session);
        placeModuleButtons(view, viewer, session);
        placeTimeRangeButtons(view, viewer, session);
    }

    private void placeActorButtons(GuiView view, Player viewer, GuiSession session) {
        UUID currentActor = session.attribute(AuditLogListPage.ATTR_ACTOR_ID)
                .filter(UUID.class::isInstance).map(UUID.class::cast).orElse(null);
        String currentName = currentActor == null
                ? messages.get(MessageKey.of("audit.gui.filter.any"))
                : Bukkit.getOfflinePlayer(currentActor).getName();

        view.place(GuiLayout.contentSlot(0), GuiButton.of(
                GuiItem.of(Material.PLAYER_HEAD, text("audit.gui.filter.pick-actor"), List.of(currentLine(currentName))),
                ctx -> pickOnlineActor(ctx.viewer())), viewer);
        view.place(GuiLayout.contentSlot(1), GuiButton.of(
                GuiItem.of(Material.NAME_TAG, text("audit.gui.filter.enter-actor")),
                ctx -> promptActorName(ctx.viewer())), viewer);
        if (currentActor != null) {
            view.place(GuiLayout.contentSlot(2), GuiButton.of(
                    GuiItem.of(Material.BARRIER, text("audit.gui.filter.clear-actor")),
                    ctx -> {
                        ctx.session().removeAttribute(AuditLogListPage.ATTR_ACTOR_ID);
                        ctx.back();
                    }), viewer);
        }
    }

    private void placeModuleButtons(GuiView view, Player viewer, GuiSession session) {
        String currentModule = session.attribute(AuditLogListPage.ATTR_MODULE)
                .filter(String.class::isInstance).map(String.class::cast).orElse(null);
        String currentName = currentModule == null ? messages.get(MessageKey.of("audit.gui.filter.any")) : currentModule;

        view.place(GuiLayout.contentSlot(9), GuiButton.of(
                GuiItem.of(Material.CHEST, text("audit.gui.filter.pick-module"), List.of(currentLine(currentName))),
                this::pickModule), viewer);
        if (currentModule != null) {
            view.place(GuiLayout.contentSlot(10), GuiButton.of(
                    GuiItem.of(Material.BARRIER, text("audit.gui.filter.clear-module")),
                    ctx -> {
                        ctx.session().removeAttribute(AuditLogListPage.ATTR_MODULE);
                        ctx.back();
                    }), viewer);
        }
    }

    private void placeTimeRangeButtons(GuiView view, Player viewer, GuiSession session) {
        view.place(GuiLayout.contentSlot(18), timeRangeButton("audit.gui.filter.time.last-hour", Duration.ofHours(1)), viewer);
        view.place(GuiLayout.contentSlot(19), timeRangeButton("audit.gui.filter.time.last-24h", Duration.ofDays(1)), viewer);
        view.place(GuiLayout.contentSlot(20), timeRangeButton("audit.gui.filter.time.last-7d", Duration.ofDays(7)), viewer);
        view.place(GuiLayout.contentSlot(21), GuiButton.of(
                GuiItem.of(Material.CLOCK, text("audit.gui.filter.time.all-time")),
                ctx -> {
                    ctx.session().removeAttribute(AuditLogListPage.ATTR_FROM);
                    ctx.session().removeAttribute(AuditLogListPage.ATTR_TO);
                    ctx.back();
                }), viewer);

        boolean timeRangeSet = session.attribute(AuditLogListPage.ATTR_FROM).isPresent() || session.attribute(AuditLogListPage.ATTR_TO).isPresent();
        if (timeRangeSet) {
            view.place(GuiLayout.contentSlot(22), GuiButton.of(
                    GuiItem.of(Material.BARRIER, text("audit.gui.filter.clear-time")),
                    ctx -> {
                        ctx.session().removeAttribute(AuditLogListPage.ATTR_FROM);
                        ctx.session().removeAttribute(AuditLogListPage.ATTR_TO);
                        ctx.back();
                    }), viewer);
        }
    }

    private GuiButton timeRangeButton(String labelKey, Duration lookback) {
        return GuiButton.of(GuiItem.of(Material.CLOCK, text(labelKey)), ctx -> {
            ctx.session().setAttribute(AuditLogListPage.ATTR_FROM, Instant.now().minus(lookback));
            ctx.session().removeAttribute(AuditLogListPage.ATTR_TO);
            ctx.back();
        });
    }

    private void pickOnlineActor(Player viewer) {
        List<Player> candidates = List.copyOf(Bukkit.getOnlinePlayers());
        SelectionDialog.open(viewer, framework, messages, scheduler,
                text("audit.gui.filter.pick-actor"), candidates,
                p -> GuiItem.playerHead(p, Component.text(p.getName()), List.of()),
                (clickCtx, chosen) -> {
                    clickCtx.session().setAttribute(AuditLogListPage.ATTR_ACTOR_ID, chosen.getUniqueId());
                    clickCtx.back();
                });
    }

    private void pickModule(GuiClickContext outerCtx) {
        SelectionDialog.open(outerCtx.viewer(), framework, messages, scheduler,
                text("audit.gui.filter.pick-module"), MODULES,
                module -> GuiItem.of(Material.CHEST, Component.text(module)),
                (clickCtx, chosenModule) -> {
                    clickCtx.session().setAttribute(AuditLogListPage.ATTR_MODULE, chosenModule);
                    clickCtx.back();
                });
    }

    /**
     * Resolves the typed name via the local cache only, never the blocking
     * network-lookup overload (see docs/architecture/threading.md) - a name
     * not already known to the server is reported back rather than risking
     * a main-thread stall. Reopens this page itself afterward (not "back"):
     * {@link GuiTextInput} replaces the inventory view with a Paper dialog,
     * so there is no {@link dev.universaladmin.gui.GuiClickContext} to
     * navigate with in its callback - same shape as every other {@code
     * GuiTextInput} caller in this codebase (e.g. {@code WorldProfilePage}).
     */
    private void promptActorName(Player viewer) {
        GuiTextInput.request(viewer, text("audit.gui.filter.enter-actor"), text("audit.gui.filter.enter-actor-label"), "",
                text("gui.confirm"), text("gui.cancel"),
                name -> {
                    OfflinePlayer resolved = name.isBlank() ? null : Bukkit.getOfflinePlayerIfCached(name);
                    if (resolved == null) {
                        viewer.sendMessage(text("audit.gui.filter.actor-not-found"));
                    } else {
                        framework.sessions().sessionFor(viewer.getUniqueId()).setAttribute(AuditLogListPage.ATTR_ACTOR_ID, resolved.getUniqueId());
                    }
                    this.open(viewer);
                },
                () -> this.open(viewer));
    }

    private Component currentLine(String value) {
        return Component.text(messages.get(MessageKey.of("audit.gui.filter.current"), value), NamedTextColor.GRAY);
    }
}
