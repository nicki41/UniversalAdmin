package dev.universaladmin.modules.moderation.notes;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiClickType;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Every staff note about one player, newest first; "add" in the top row, right-click a note to delete it. */
public final class NotesPage extends AbstractListGuiPage<PlayerNote> {

    private static final GuiPageId ID = GuiPageId.core("moderation.notes");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int ADD_SLOT = GuiLayout.REFRESH_SLOT + 2;

    private final PlayerNoteRepository notes;
    private final ActionExecutor actions;
    private final TaskScheduler scheduler;
    private final PlayerRef target;

    public NotesPage(GuiFramework framework, MessageService messages, TaskScheduler scheduler, PlayerNoteRepository notes,
            ActionExecutor actions, PlayerRef target) {
        super(ID, framework, messages, scheduler);
        this.notes = notes;
        this.actions = actions;
        this.scheduler = scheduler;
        this.target = target;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.notes.gui.title", target.name());
    }

    @Override
    protected void renderControls(GuiRenderContext context) {
        context.view().place(ADD_SLOT, GuiButton.of(GuiItem.of(Material.WRITABLE_BOOK, text("moderation.notes.gui.add")),
                ctx -> promptAdd(ctx.viewer())), context.viewer());
    }

    @Override
    protected CompletableFuture<List<PlayerNote>> loadItems(Player viewer) {
        return notes.findByTarget(target.id(), 200);
    }

    @Override
    protected GuiItem render(PlayerNote note) {
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(note.text())) {
            lore.add(Component.text(line, NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(text("moderation.notes.gui.by", note.authorName(), TIME.format(note.createdAt())));
        lore.add(text("moderation.notes.gui.delete-hint"));
        return GuiItem.of(Material.PAPER, Component.text("#" + note.id(), NamedTextColor.GOLD), lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, PlayerNote note) {
        if (context.clickType() != GuiClickType.RIGHT && context.clickType() != GuiClickType.SHIFT_RIGHT) {
            return;
        }
        Player viewer = context.viewer();
        ConfirmationDialog.open(viewer, framework, messages, text("moderation.notes.gui.delete-title"),
                List.of(Component.text(note.text(), NamedTextColor.GRAY)), ConfirmationDialog.DangerLevel.WARNING,
                confirm -> GuiActions.runAndReport(actions, scheduler, messages, viewer, NoteActions.REMOVE, note.id(),
                        null, () -> this.open(viewer)),
                GuiClickContext::back);
    }

    private void promptAdd(Player viewer) {
        GuiTextInput.request(viewer, text("moderation.notes.gui.add"), text("moderation.notes.gui.prompt", target.name()), "",
                text("gui.confirm"), text("gui.cancel"),
                typed -> {
                    if (typed.isBlank()) {
                        this.open(viewer);
                        return;
                    }
                    GuiActions.runAndReport(actions, scheduler, messages, viewer, NoteActions.ADD,
                            new NoteActions.AddInput(target.id(), target.name(), typed), null, () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > 40) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
