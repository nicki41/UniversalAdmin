package dev.universaladmin.modules.nick;

import java.time.Instant;
import java.util.UUID;

/**
 * A player's nick as stored in {@code ua_nicks}: the name and skin everyone
 * sees, plus the real name for staff. Existence is the state - a player
 * with a row is nicked (or will be again when they rejoin, see
 * {@link NickSettings#RESTORE_ON_REJOIN}).
 *
 * @param skin the skin they wear, {@code null} for their own
 */
public record NickRecord(UUID playerId, String realName, String name, Skin skin, Instant createdAt) {

    public String skinOf() {
        return skin == null ? null : skin.owner();
    }

    /**
     * A Mojang-signed skin of the account {@code owner} - the textures
     * property clients only accept with its signature.
     */
    public record Skin(String owner, String value, String signature) {
    }
}
