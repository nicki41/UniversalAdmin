package dev.universaladmin.modules.nick;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.status.Nicks;
import java.util.List;

/** Permission nodes of the Nick module. */
public final class NickPermissions {

    /** Nick yourself - another name and skin. */
    public static final PermissionNode USE = PermissionNode.core("nick.use");
    /** Remove the nick of another player. */
    public static final PermissionNode OTHERS = PermissionNode.core("nick.others");
    /** See the real name behind a nick, in menus and {@code /admin nick list}. */
    public static final PermissionNode SEE = Nicks.SEE_REAL_NAME;

    private NickPermissions() {
    }

    static List<PermissionDefinition> all() {
        return List.of(
                new PermissionDefinition(USE, "Nick yourself: play under another name and skin", PermissionDefault.OP),
                new PermissionDefinition(OTHERS, "Remove other players' nicks", PermissionDefault.OP),
                new PermissionDefinition(SEE, "See the real name behind a nick", PermissionDefault.OP));
    }
}
