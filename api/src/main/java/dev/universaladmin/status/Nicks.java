package dev.universaladmin.status;

import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff shown under another name and skin - the Nick module's nicks. For
 * displays such as tab lists, name tags and scoreboards that must follow a
 * nick, and for code that records players and needs their real identity.
 * Provided by the Nick module through the
 * {@link dev.universaladmin.core.ServiceRegistry}; absent while that module
 * is disabled, in which case nobody is nicked.
 *
 * <p>Every method answers from memory and is safe to call from any thread.
 * A nicked player's {@code Player#getName()} returns the nick, everywhere -
 * {@link dev.universaladmin.core.PlayerNames#real} gives the real name back.
 * In network mode the nicks of players on the other servers are known too
 * (at the latest within a minute of a change there).
 *
 * @since 1.6
 */
public interface Nicks {

    /** Who may see the real name behind a nick - in UniversalAdmin's menus and wherever an extension shows one. */
    PermissionNode SEE_REAL_NAME = PermissionNode.of("universaladmin.nick.see");

    /** The nick the player is shown under right now, on this server or another server of the network. */
    Optional<Nick> nick(UUID playerId);

    /** The nicked player shown under {@code name} right now (case-insensitive) - not someone who is really called that. */
    Optional<Nick> byName(String name);

    /** Every nick shown right now. */
    List<Nick> active();

    /**
     * @param playerId the nicked player
     * @param name     the name everyone sees
     * @param realName the player's own name
     * @param skinOf   the account whose skin they wear, {@code null} for their own
     * @param server   the server they are on, {@code null} without network mode
     */
    record Nick(UUID playerId, String name, String realName, String skinOf, String server) {
    }
}
