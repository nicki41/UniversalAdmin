package dev.universaladmin.modules.moderation;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/**
 * Every {@link PermissionNode} the Moderation module declares, in one place -
 * see docs/user/permissions.md for the documented table these mirror. {@link
 * #USE} gates the main-menu entry/GUI wizard entirely (matches every other
 * module's top-level gate); {@link #VIEW} gates browsing punishment
 * history/lists and detail pages, separately from actually issuing one.
 */
public final class ModerationPermissions {

    private ModerationPermissions() {
    }

    public static final PermissionNode USE = PermissionNode.core("moderation.use");
    public static final PermissionNode VIEW = PermissionNode.core("moderation.view");
    public static final PermissionNode KICK = PermissionNode.core("moderation.kick");
    public static final PermissionNode BAN = PermissionNode.core("moderation.ban");
    public static final PermissionNode TEMPBAN = PermissionNode.core("moderation.tempban");
    public static final PermissionNode IPBAN = PermissionNode.core("moderation.ipban");
    public static final PermissionNode MUTE = PermissionNode.core("moderation.mute");
    public static final PermissionNode TEMPMUTE = PermissionNode.core("moderation.tempmute");
    public static final PermissionNode WARN = PermissionNode.core("moderation.warn");
    public static final PermissionNode UNBAN = PermissionNode.core("moderation.unban");
    public static final PermissionNode UNMUTE = PermissionNode.core("moderation.unmute");
    public static final PermissionNode REMOVE_WARN = PermissionNode.core("moderation.removewarn");
    public static final PermissionNode FREEZE = PermissionNode.core("moderation.freeze");
    public static final PermissionNode UNFREEZE = PermissionNode.core("moderation.unfreeze");
    public static final PermissionNode VANISH = PermissionNode.core("moderation.vanish");
    /** Not under the {@code moderation} namespace on purpose - the exact node requested: {@code universaladmin.bypass.vanish}. */
    public static final PermissionNode BYPASS_VANISH = PermissionNode.core("bypass.vanish");
    public static final PermissionNode GODMODE = PermissionNode.core("moderation.godmode");
    public static final PermissionNode COLLISION = PermissionNode.core("moderation.collision");
    public static final PermissionNode STAFFMODE = PermissionNode.core("moderation.staffmode");
    public static final PermissionNode STAFFMODE_RECOVER = PermissionNode.core("moderation.staffmode.recover");

    /** Registers every node above with {@link PermissionDefault#OP}, matching every other node in the plugin today. */
    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(USE, "Open the Moderation GUI", PermissionDefault.OP),
                new PermissionDefinition(VIEW, "View punishment history, warnings, bans, and mutes", PermissionDefault.OP),
                new PermissionDefinition(KICK, "Kick a player", PermissionDefault.OP),
                new PermissionDefinition(BAN, "Permanently ban a player", PermissionDefault.OP),
                new PermissionDefinition(TEMPBAN, "Temporarily ban a player", PermissionDefault.OP),
                new PermissionDefinition(IPBAN, "Ban a player's IP address", PermissionDefault.OP),
                new PermissionDefinition(MUTE, "Permanently mute a player", PermissionDefault.OP),
                new PermissionDefinition(TEMPMUTE, "Temporarily mute a player", PermissionDefault.OP),
                new PermissionDefinition(WARN, "Warn a player", PermissionDefault.OP),
                new PermissionDefinition(UNBAN, "Revoke an active ban", PermissionDefault.OP),
                new PermissionDefinition(UNMUTE, "Revoke an active mute", PermissionDefault.OP),
                new PermissionDefinition(REMOVE_WARN, "Remove a single warning", PermissionDefault.OP),
                new PermissionDefinition(FREEZE, "Freeze a player", PermissionDefault.OP),
                new PermissionDefinition(UNFREEZE, "Unfreeze a player", PermissionDefault.OP),
                new PermissionDefinition(VANISH, "Toggle your own vanish status", PermissionDefault.OP),
                new PermissionDefinition(BYPASS_VANISH, "See vanished players", PermissionDefault.OP),
                new PermissionDefinition(GODMODE, "Toggle your own godmode status", PermissionDefault.OP),
                new PermissionDefinition(COLLISION, "Toggle your own no-collision status", PermissionDefault.OP),
                new PermissionDefinition(STAFFMODE, "Enter/exit staff mode", PermissionDefault.OP),
                new PermissionDefinition(STAFFMODE_RECOVER, "Manually recover a pending staff-mode snapshot", PermissionDefault.OP))
                .forEach(registry::register);
    }
}
