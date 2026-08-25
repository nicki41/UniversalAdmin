package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Pick-one-from-a-list, built directly on {@link AbstractListGuiPage} - the
 * pagination/async-loading/empty-state machinery a selection needs is
 * exactly what that base class already provides, so this class is only the
 * adapter from "a list and a callback" to a one-off {@link GuiPage}.
 *
 * <p>Like {@link ConfirmationDialog}, ephemeral: opened directly, never
 * registered in {@code GuiRegistry}.
 */
public final class SelectionDialog {

    private SelectionDialog() {
    }

    /**
     * Opens a selection of {@code options} for {@code viewer}.
     *
     * @param renderer maps one option to how it appears in the list
     * @param onSelect called (on the main thread) with the click context and the chosen option
     */
    public static <T> void open(
            Player viewer,
            GuiFramework framework,
            MessageService messages,
            TaskScheduler scheduler,
            Component title,
            List<T> options,
            Function<T, GuiItem> renderer,
            BiConsumer<GuiClickContext, T> onSelect) {
        GuiPage dialog = new SelectionPage<>(framework, messages, scheduler, title, options, renderer, onSelect);
        dialog.open(viewer);
    }

    private static final class SelectionPage<T> extends AbstractListGuiPage<T> {

        private final Component title;
        private final List<T> options;
        private final Function<T, GuiItem> renderer;
        private final BiConsumer<GuiClickContext, T> onSelect;

        private SelectionPage(
                GuiFramework framework,
                MessageService messages,
                TaskScheduler scheduler,
                Component title,
                List<T> options,
                Function<T, GuiItem> renderer,
                BiConsumer<GuiClickContext, T> onSelect) {
            super(GuiPageId.core("internal.selection"), framework, messages, scheduler);
            this.title = title;
            this.options = List.copyOf(options);
            this.renderer = renderer;
            this.onSelect = onSelect;
        }

        @Override
        protected Component title(Player viewer) {
            return title;
        }

        @Override
        protected CompletableFuture<List<T>> loadItems(Player viewer) {
            return CompletableFuture.completedFuture(options);
        }

        @Override
        protected GuiItem render(T item) {
            return renderer.apply(item);
        }

        @Override
        protected void onSelect(GuiClickContext context, T item) {
            onSelect.accept(context, item);
        }
    }
}
