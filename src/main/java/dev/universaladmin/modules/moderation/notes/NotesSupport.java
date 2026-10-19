package dev.universaladmin.modules.moderation.notes;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The frontends of staff notes: the "Notes" profile button, the
 * {@code /admin notes} command and the "player with notes joined" alert.
 */
public final class NotesSupport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Everything the notes frontends need. */
    public record Context(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, ActionExecutor actions,
            PlayerNoteRepository notes, SettingsService settings, NotificationService notifications) {
    }

    private NotesSupport() {
    }

    /** "Notes" on a player's profile. */
    public static PlayerMenuEntry menuEntry(Context ctx) {
        return new PlayerMenuEntry() {
            @Override
            public String id() {
                return "core:moderation.notes";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public PermissionNode permission() {
                return ModerationPermissions.NOTES;
            }

            @Override
            public GuiItem icon(Player viewer, PlayerRef target) {
                return GuiItem.of(Material.WRITABLE_BOOK, ComponentMessages.render(ctx.messages().get(MessageKey.of("moderation.notes.gui.button"))));
            }

            @Override
            public void onClick(GuiClickContext context, PlayerRef target) {
                context.open(new NotesPage(ctx.framework(), ctx.messages(), ctx.scheduler(), ctx.notes(), ctx.actions(), target));
            }
        };
    }

    /** {@code /admin notes <player> [add <text>|remove <id>]}. */
    public static final class NotesCommand extends BaseAdminCommand {

        private final Context ctx;

        public NotesCommand(Context ctx) {
            super("notes", ModerationPermissions.NOTES, MessageKey.of("commands.notes.usage"), MessageKey.of("commands.notes.description"));
            this.ctx = ctx;
            aliases("note");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            if (invocation.args().get(0).equalsIgnoreCase("remove")) {
                if (invocation.argCount() < 2) {
                    invocation.usage(this);
                    return;
                }
                invocation.integer(1).ifPresent(id -> invocation.run(NoteActions.REMOVE, (long) id));
                return;
            }
            invocation.withPlayer(0, player -> {
                if (invocation.argCount() > 2 && invocation.args().get(1).equalsIgnoreCase("add")) {
                    String text = invocation.joinArgs(2);
                    if (text.length() > NoteActions.MAX_LENGTH) {
                        invocation.reply("moderation.notes.too-long", NoteActions.MAX_LENGTH);
                        return;
                    }
                    invocation.run(NoteActions.ADD, new NoteActions.AddInput(player.id(), player.name(), text));
                    return;
                }
                ctx.notes().findByTarget(player.id(), 20).whenComplete((list, error) -> {
                    if (error != null) {
                        invocation.reply("command.error.internal");
                    } else if (list.isEmpty()) {
                        invocation.reply("moderation.notes.none", player.name());
                    } else {
                        invocation.line("moderation.notes.header", player.name(), list.size());
                        list.forEach(note -> invocation.line("moderation.notes.entry", String.valueOf(note.id()), note.text(),
                                note.authorName(), TIME.format(note.createdAt())));
                    }
                });
            });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                List<String> options = new java.util.ArrayList<>(Arguments.visiblePlayerNames(invocation.sender()));
                options.add("remove");
                return options;
            }
            return invocation.argCount() == 2 && !invocation.args().get(0).equalsIgnoreCase("remove") ? List.of("add") : List.of();
        }
    }

    /** Tells staff with notes permission when a player with notes joins. */
    public static final class JoinAlertListener implements Listener {

        private final Context ctx;

        public JoinAlertListener(Context ctx) {
            this.ctx = ctx;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            if (!ctx.settings().get(ModerationSettings.NOTES_JOIN_ALERT)) {
                return;
            }
            Player player = event.getPlayer();
            ctx.notes().countByTarget(player.getUniqueId()).thenAccept(count -> {
                if (count > 0) {
                    String text = ctx.messages().get(MessageKey.of("moderation.notes.join-alert"), player.getName(), count);
                    ctx.scheduler().runOnMainThread(() -> ctx.notifications().notifyStaff(ModerationPermissions.NOTES, Notification.info(text)));
                }
            });
        }
    }
}
