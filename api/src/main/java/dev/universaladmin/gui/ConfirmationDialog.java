package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A generic yes/no confirmation, rendered as a small inventory rather than
 * the Paper client Dialog API - see the "Confirmations" section of
 * docs/development/gui-framework.md for why (no free text is needed here,
 * so the simpler, already-established inventory GUI is enough; the Dialog
 * API is reserved for {@link GuiTextInput}, which genuinely needs it).
 *
 * <p>Ephemeral by design: unlike {@link AbstractGuiPage} subclasses, a
 * confirmation is parameterized per call site (what exactly is being
 * confirmed) and is never registered in {@code GuiRegistry} - a click
 * handler opens one directly with {@link #open}.
 *
 * <p><b>Navigation:</b> opening a dialog records "reopen the page the viewer
 * is looking at right now" as the newest history entry, so
 * {@code confirmCtx -> confirmCtx.back()} returns to exactly the page the
 * dialog was opened from. If a handler navigates on its own instead (e.g.
 * {@code this.open(viewer)}), that entry is dropped again so it can never
 * resurface as a stale "back" target later.
 *
 * <p><b>Single use:</b> the first click on confirm or cancel wins; any
 * further click on the same dialog instance (a double click, a lagging
 * client) is ignored, so a confirmed dangerous action can never run twice.
 */
public final class ConfirmationDialog {

    /** How strongly the confirm button is visually flagged - see {@link IconProvider#confirm}. */
    public enum DangerLevel {
        NORMAL,
        WARNING,
        DANGEROUS
    }

    private ConfirmationDialog() {
    }

    /**
     * Opens a confirmation for {@code viewer}.
     *
     * @param description shown as lore under the title; each element is one line
     * @param onConfirm   run (on the main thread, from the click handler) if confirmed
     * @param onCancel    run if cancelled - typically {@code ctx -> ctx.back()} to return to the calling page
     */
    public static void open(
            Player viewer,
            GuiFramework framework,
            MessageService messages,
            Component title,
            List<Component> description,
            DangerLevel dangerLevel,
            GuiButton.ClickHandler onConfirm,
            GuiButton.ClickHandler onCancel) {
        GuiSession session = framework.sessions().sessionFor(viewer.getUniqueId());
        Runnable backToCaller = callerReopener(viewer);
        if (backToCaller != null) {
            session.pushHistory(backToCaller);
        }
        AtomicBoolean handled = new AtomicBoolean();
        GuiPage dialog = new DialogPage(framework, messages, title, description, dangerLevel,
                once(handled, session, backToCaller, onConfirm), once(handled, session, backToCaller, onCancel));
        dialog.open(viewer);
    }

    /** "Reopen whatever UniversalAdmin page {@code viewer} has open right now", or {@code null} if none. */
    private static Runnable callerReopener(Player viewer) {
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof GuiView view) {
            GuiPage caller = view.page();
            return () -> caller.open(viewer);
        }
        return null;
    }

    private static GuiButton.ClickHandler once(
            AtomicBoolean handled, GuiSession session, Runnable backToCaller, GuiButton.ClickHandler handler) {
        return ctx -> {
            if (!handled.compareAndSet(false, true)) {
                return;
            }
            handler.handle(ctx);
            if (backToCaller != null) {
                // Still on top: the handler didn't go back() - it navigated
                // (or stayed) on its own, so the entry would only ever be a
                // stale duplicate from here on.
                session.removeIfMostRecent(backToCaller);
            }
        };
    }

    private static final class DialogPage extends AbstractGuiPage {

        private static final int ROWS = 3;
        private static final int DESCRIPTION_SLOT = 13;
        private static final int CONFIRM_SLOT = 11;
        private static final int CANCEL_SLOT = 15;

        private final Component title;
        private final List<Component> description;
        private final DangerLevel dangerLevel;
        private final GuiButton.ClickHandler onConfirm;
        private final GuiButton.ClickHandler onCancel;

        private DialogPage(
                GuiFramework framework,
                MessageService messages,
                Component title,
                List<Component> description,
                DangerLevel dangerLevel,
                GuiButton.ClickHandler onConfirm,
                GuiButton.ClickHandler onCancel) {
            super(GuiPageId.core("internal.confirmation"), framework, messages, ROWS);
            this.title = title;
            this.description = description;
            this.dangerLevel = dangerLevel;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        @Override
        protected Component title(Player viewer) {
            return title;
        }

        @Override
        protected boolean refreshable() {
            return false;
        }

        @Override
        protected void renderContent(GuiRenderContext context) {
            GuiView view = context.view();
            Player viewer = context.viewer();
            view.place(DESCRIPTION_SLOT, GuiItem.of(framework.icons().pageIndicator(), Component.empty(), description));
            view.place(CONFIRM_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().confirm(dangerLevel), text("gui.confirm")), onConfirm),
                    viewer);
            view.place(CANCEL_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().cancel(), text("gui.cancel")), onCancel),
                    viewer);
        }
    }
}
