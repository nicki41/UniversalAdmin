package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import java.util.List;
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
        GuiPage dialog = new DialogPage(framework, messages, title, description, dangerLevel, onConfirm, onCancel);
        dialog.open(viewer);
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
