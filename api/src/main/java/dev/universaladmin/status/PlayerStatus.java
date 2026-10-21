package dev.universaladmin.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What UniversalAdmin knows about a player right now - for displays such as
 * scoreboards, tab lists and placeholders. Provided by the Moderation module
 * through the {@link dev.universaladmin.core.ServiceRegistry}.
 *
 * <p>Every method answers from memory and is safe to call on the main
 * thread, as often as a scoreboard needs it. Mutes, warnings and punishment
 * counts are kept for online players and refreshed when they change (on
 * other servers of a network: within a minute); for offline players they
 * read as empty or 0.
 *
 * @since 1.2
 */
public interface PlayerStatus {

    boolean vanished(UUID playerId);

    boolean staffMode(UUID playerId);

    boolean frozen(UUID playerId);

    boolean godmode(UUID playerId);

    /** The mute in force, if the player is muted. */
    Optional<Mute> mute(UUID playerId);

    /** Warnings that are in force. */
    int activeWarnings(UUID playerId);

    /** Every punishment the player ever received (kicks and warnings included). */
    int punishments(UUID playerId);

    /** How many online players are vanished. */
    int vanishedOnline();

    /** @param expiresAt {@code null} for a permanent mute */
    record Mute(String reason, Instant expiresAt) {

        public boolean permanent() {
            return expiresAt == null;
        }
    }
}
