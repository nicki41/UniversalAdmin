package dev.universaladmin.network;

import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import org.bukkit.permissions.Permissible;

/**
 * Who may see a vanished player in what the network shows - lists, counts,
 * {@code /admin find}, tab completion. Vanish itself belongs to the
 * Moderation module; this only reads {@link NetworkService#vanished} and the
 * same bypass node that module checks, so the network does not depend on it.
 */
public final class NetworkVisibility {

    /** The node the Moderation module's vanish uses for "sees vanished players". */
    public static final PermissionNode SEE_VANISHED = PermissionNode.core("bypass.vanish");

    private NetworkVisibility() {
    }

    /** Whether {@code viewer} may see {@code player}: not vanished, or the viewer sees vanished players. */
    public static boolean canSee(Permissible viewer, NetworkService network, NetworkPlayer player) {
        return player.id() == null || !network.vanished(player.id()) || viewer.hasPermission(SEE_VANISHED.value());
    }

    /** {@link NetworkService#players()} without the players {@code viewer} may not see. */
    public static List<NetworkPlayer> players(Permissible viewer, NetworkService network) {
        return network.players().stream().filter(player -> canSee(viewer, network, player)).toList();
    }
}
