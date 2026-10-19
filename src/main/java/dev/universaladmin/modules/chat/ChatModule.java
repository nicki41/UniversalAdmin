package dev.universaladmin.modules.chat;

import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ValidationError;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.modules.chat.action.ChatActions;
import dev.universaladmin.modules.chat.command.ChatCommands;
import dev.universaladmin.modules.chat.gui.ChatHomePage;
import dev.universaladmin.network.NetworkListener;
import java.time.Duration;
import java.util.Optional;

/**
 * Chat moderation tools: staff chat ({@code /sc}, or a message starting with
 * {@code #}), chat lock, slow mode and clear chat - via commands, a GUI page
 * and audited actions. Works alongside any chat-formatting plugin: it only
 * cancels or reroutes messages, it never reformats public chat.
 */
public final class ChatModule implements Module {

    public static final ModuleId ID = ModuleId.core("chat");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Chat")
            .description("Staff chat, chat lock, slow mode and clear chat.")
            .icon(new GuiIcon("writable_book", "Chat"))
            .menu(ChatHomePage.ID, ChatPermissions.MANAGE)
            .menuOrder(35)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        var platform = context.platform();
        ChatSettings.all().forEach(context::registerSetting);
        ChatPermissions.all().forEach(context::registerPermission);

        ChatService chat = new ChatService();
        context.registerService(ChatService.class, chat);
        String module = ID.key().name();

        context.registerAction(ActionDefinition.builder(new ChatActions.SetLock(true, chat, platform.messages(), platform.scheduler()))
                .permission(ChatPermissions.MANAGE).module(module).auditSummary(input -> "Locked the chat").build());
        context.registerAction(ActionDefinition.builder(new ChatActions.SetLock(false, chat, platform.messages(), platform.scheduler()))
                .permission(ChatPermissions.MANAGE).module(module).auditSummary(input -> "Unlocked the chat").build());
        context.registerAction(ActionDefinition.builder(new ChatActions.SetSlowMode(chat, platform.messages(), platform.scheduler()))
                .permission(ChatPermissions.MANAGE).module(module)
                .validator((ctx, delay) -> delay.compareTo(Duration.ofHours(1)) > 0
                        ? Optional.of(ValidationError.of(ActionResult.FailureReason.VALIDATION, MessageKey.of("chat.slowmode-too-long")))
                        : Optional.empty())
                .auditSummary(delay -> delay.isZero() ? "Turned slow mode off" : "Set slow mode to " + DurationParser.format(delay))
                .build());
        context.registerAction(ActionDefinition.builder(new ChatActions.Clear(platform.messages(), platform.settings(), platform.scheduler()))
                .permission(ChatPermissions.MANAGE).module(module).auditSummary(input -> "Cleared the chat").build());
        // Staff chat itself is not audited - it is conversation, not an admin action.
        ChatActions.StaffMessage staffMessage = new ChatActions.StaffMessage(
                platform.messages(), platform.settings(), platform.scheduler(), context.logger(), platform.network());
        context.registerAction(ActionDefinition.builder(staffMessage)
                .permission(ChatPermissions.STAFF_CHAT).module(module).notAudited().build());
        // Network mode: staff chat from the other servers.
        NetworkListener remoteStaffChat = message -> staffMessage.deliver(
                message.get("sender", "?"), message.get("message", ""), message.source());
        platform.network().subscribe(ChatActions.StaffMessage.NETWORK_TOPIC, remoteStaffChat);
        context.resources().closeable(() -> platform.network().unsubscribe(ChatActions.StaffMessage.NETWORK_TOPIC, remoteStaffChat));
        context.registerAction(ActionDefinition.builder(new ChatActions.ToggleStaffChat(chat))
                .permission(ChatPermissions.STAFF_CHAT).module(module).notAudited().build());

        context.registerListener(new ChatListener(chat, platform.actionExecutor(), platform.settings(), platform.messages()));
        context.registerPage(new ChatHomePage(platform.guiFramework(), platform.messages(), chat, platform.actionExecutor(), platform.scheduler()));
        ChatCommands.all(chat).forEach(context::registerCommand);
    }
}
