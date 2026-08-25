package dev.universaladmin.modules.whitelist.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.WhitelistPermissions;
import dev.universaladmin.modules.whitelist.WhitelistSettings;
import dev.universaladmin.modules.whitelist.action.AddWhitelistEntryInput;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingParseException;
import dev.universaladmin.settings.SettingTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Add wizard: search offline player -&gt; select -&gt; reason (optional) -&gt;
 * notes (optional) -&gt; expiration (optional) -&gt; confirm -&gt; action. One
 * page with chained private methods, the same "wizard" shape {@code
 * ModeratePlayerPage} establishes - the accumulated data ({@code target},
 * {@code reason}, {@code notes}) is carried purely as method parameters.
 */
public final class WhitelistAddPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("whitelist.add");

    private final WhitelistGuiContext ctx;

    public WhitelistAddPage(WhitelistGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("whitelist.gui.add.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                new GuiButton(GuiItem.of(Material.COMPASS, text("whitelist.gui.add.search")), WhitelistPermissions.ADD,
                        clickCtx -> promptSearch(clickCtx.viewer())),
                viewer);
    }

    private void promptSearch(Player viewer) {
        GuiTextInput.request(viewer, text("whitelist.gui.add.title"), text("whitelist.gui.add.prompt-search"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    if (submitted == null || submitted.isBlank()) {
                        this.open(viewer);
                        return;
                    }
                    search(viewer, submitted.trim());
                },
                () -> this.open(viewer));
    }

    private void search(Player viewer, String query) {
        // Bukkit.getOfflinePlayers() itself must run on the main thread like
        // any other Bukkit API call (see docs/architecture/threading.md) -
        // this method is already on the main thread (called from
        // GuiTextInput's submit callback), so the snapshot is captured here
        // and only the CPU-only filter/sort/limit below runs off-thread.
        OfflinePlayer[] snapshot = Bukkit.getOfflinePlayers();
        ctx.scheduler().supplyAsync(() -> {
            int max = ctx.settings().get(WhitelistSettings.SEARCH_MAX_RESULTS);
            String needle = query.toLowerCase(Locale.ROOT);
            return Arrays.stream(snapshot)
                    .filter(op -> op.getName() != null && op.getName().toLowerCase(Locale.ROOT).contains(needle))
                    .sorted(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER))
                    .limit(max)
                    .toList();
        }).thenAccept(matches -> ctx.scheduler().runOnMainThread(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (matches.isEmpty()) {
                viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("whitelist.gui.add.no-matches"))));
                this.open(viewer);
                return;
            }
            SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("whitelist.gui.add.select-player"), matches,
                    op -> GuiItem.playerHead(op, Component.text(op.getName()), List.of()),
                    (selectCtx, op) -> promptReason(viewer, op.getUniqueId(), op.getName()));
        }));
    }

    private void promptReason(Player viewer, UUID targetId, String targetName) {
        GuiTextInput.request(viewer, text("whitelist.gui.add.title", targetName), text("whitelist.gui.add.prompt-reason"), "",
                text("gui.confirm"), text("gui.cancel"),
                reason -> promptNotes(viewer, targetId, targetName, blank(reason) ? null : reason.trim()),
                () -> this.open(viewer));
    }

    private void promptNotes(Player viewer, UUID targetId, String targetName, String reason) {
        GuiTextInput.request(viewer, text("whitelist.gui.add.title", targetName), text("whitelist.gui.add.prompt-notes"), "",
                text("gui.confirm"), text("gui.cancel"),
                notes -> promptExpiration(viewer, targetId, targetName, reason, blank(notes) ? null : notes.trim()),
                () -> this.open(viewer));
    }

    private void promptExpiration(Player viewer, UUID targetId, String targetName, String reason, String notes) {
        if (!viewer.hasPermission(WhitelistPermissions.TEMPORARY.value())) {
            confirmAdd(viewer, targetId, targetName, reason, notes, null);
            return;
        }
        GuiTextInput.request(viewer, text("whitelist.gui.add.title", targetName), text("whitelist.gui.add.prompt-expiration"), "",
                text("gui.confirm"), text("gui.cancel"),
                expiration -> {
                    if (blank(expiration)) {
                        confirmAdd(viewer, targetId, targetName, reason, notes, null);
                        return;
                    }
                    try {
                        Duration duration = SettingTypes.DURATION.parse(expiration.trim());
                        confirmAdd(viewer, targetId, targetName, reason, notes, Instant.now().plus(duration));
                    } catch (SettingParseException e) {
                        WhitelistGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                    }
                },
                () -> this.open(viewer));
    }

    private void confirmAdd(Player viewer, UUID targetId, String targetName, String reason, String notes, Instant expiresAt) {
        AddWhitelistEntryInput input = new AddWhitelistEntryInput(targetId, targetName, reason, notes, expiresAt);
        Runnable apply = () -> WhitelistGuiActions.runAction(ctx, viewer, WhitelistActionIds.ADD, input,
                () -> new WhitelistMembersListPage(ctx).open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("whitelist.gui.add.title", targetName),
                List.of(text("whitelist.gui.add.confirm", targetName)), ConfirmationDialog.DangerLevel.NORMAL,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
