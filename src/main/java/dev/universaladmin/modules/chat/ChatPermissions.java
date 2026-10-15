package dev.universaladmin.modules.chat;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;

/** Permission nodes of the Chat module. */
public final class ChatPermissions {

    /** Lock/unlock the chat, set slow mode, clear the chat. */
    public static final PermissionNode MANAGE = PermissionNode.core("chat.manage");
    /** Read and write staff chat. */
    public static final PermissionNode STAFF_CHAT = PermissionNode.core("chat.staff");
    /** Chat while the chat is locked or slowed, and keep your chat history when it is cleared. */
    public static final PermissionNode BYPASS = PermissionNode.core("chat.bypass");

    private ChatPermissions() {
    }

    static List<PermissionDefinition> all() {
        return List.of(
                new PermissionDefinition(MANAGE, "Lock the chat, set slow mode and clear the chat", PermissionDefault.OP),
                new PermissionDefinition(STAFF_CHAT, "Read and write the staff chat", PermissionDefault.OP),
                new PermissionDefinition(BYPASS, "Chat while locked/slowed and keep chat history when it is cleared", PermissionDefault.OP));
    }
}
