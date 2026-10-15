package dev.universaladmin.modules.chat.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiClickType;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.chat.ChatPermissions;
import dev.universaladmin.modules.chat.ChatService;
import dev.universaladmin.modules.chat.action.ChatActions;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /admin} → Chat: lock/unlock the chat, cycle slow mode (right-click
 * for a custom value), clear the chat, and switch your own staff chat on or
 * off. Every button runs a {@link ChatActions} action.
 */
public final class ChatHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("chat.home");

    private static final List<Duration> SLOW_PRESETS = List.of(
            Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1));

    private final ChatService chat;
    private final ActionExecutor actions;
    private final TaskScheduler scheduler;

    public ChatHomePage(GuiFramework framework, MessageService messages, ChatService chat, ActionExecutor actions, TaskScheduler scheduler) {
        super(ID, framework, messages);
        this.chat = chat;
        this.actions = actions;
        this.scheduler = scheduler;
    }

    @Override
    protected Component title(Player viewer) {
        return text("chat.gui.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        boolean locked = chat.locked();
        view.place(20, new GuiButton(GuiItem.of(locked ? Material.IRON_DOOR : Material.OAK_DOOR,
                        text(locked ? "chat.gui.unlock" : "chat.gui.lock"),
                        List.of(text(locked ? "chat.gui.lock-status-locked" : "chat.gui.lock-status-open"))),
                ChatPermissions.MANAGE,
                ctx -> run(ctx.viewer(), locked ? ChatActions.UNLOCK : ChatActions.LOCK, null)), viewer);

        Duration slow = chat.slowMode();
        view.place(22, new GuiButton(GuiItem.of(Material.CLOCK, text("chat.gui.slowmode"),
                        List.of(text("chat.gui.slowmode-current", slow.isZero() ? messages.get(
                                MessageKey.of("chat.slowmode-none")) : DurationParser.format(slow)),
                                text("chat.gui.slowmode-hint"))),
                ChatPermissions.MANAGE, ctx -> onSlowMode(ctx, slow)), viewer);

        view.place(24, new GuiButton(GuiItem.of(Material.LAVA_BUCKET, text("chat.gui.clear"), List.of(text("chat.gui.clear-hint"))),
                ChatPermissions.MANAGE,
                ctx -> ConfirmationDialog.open(ctx.viewer(), framework, messages, text("chat.gui.clear"),
                        List.of(text("chat.gui.clear-confirm")), ConfirmationDialog.DangerLevel.WARNING,
                        confirm -> run(confirm.viewer(), ChatActions.CLEAR, null),
                        GuiClickContext::back)), viewer);

        boolean staffChat = chat.staffChatToggled(viewer.getUniqueId());
        view.place(31, new GuiButton(GuiItem.of(staffChat ? Material.LIME_DYE : Material.GRAY_DYE, text("chat.gui.staffchat"),
                        List.of(text(staffChat ? "chat.gui.staffchat-on" : "chat.gui.staffchat-off"))),
                ChatPermissions.STAFF_CHAT, ctx -> run(ctx.viewer(), ChatActions.STAFF_TOGGLE, null)), viewer);
    }

    private void onSlowMode(GuiClickContext ctx, Duration current) {
        Player viewer = ctx.viewer();
        if (ctx.clickType() == GuiClickType.RIGHT || ctx.clickType() == GuiClickType.SHIFT_RIGHT) {
            GuiTextInput.request(viewer, text("chat.gui.slowmode"), text("chat.gui.slowmode-prompt"),
                    current.isZero() ? "" : DurationParser.format(current), text("gui.confirm"), text("gui.cancel"),
                    typed -> {
                        Optional<Duration> parsed = typed.isBlank() || typed.equalsIgnoreCase("off")
                                ? Optional.of(Duration.ZERO)
                                : Arguments.duration(typed);
                        if (parsed.isEmpty()) {
                            viewer.sendMessage(text("command.error.invalid-duration", typed));
                            this.open(viewer);
                            return;
                        }
                        run(viewer, ChatActions.SLOW_MODE, parsed.get());
                    },
                    () -> this.open(viewer));
            return;
        }
        int index = SLOW_PRESETS.indexOf(current);
        Duration next = SLOW_PRESETS.get(index < 0 ? 1 : (index + 1) % SLOW_PRESETS.size());
        run(viewer, ChatActions.SLOW_MODE, next);
    }

    private <I> void run(Player viewer, ActionId id, I input) {
        GuiActions.<I, Object>run(actions, scheduler, messages, viewer, id, input, ignored -> this.open(viewer));
    }
}
