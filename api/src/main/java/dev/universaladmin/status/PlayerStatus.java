package dev.universaladmin.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What UniversalAdmin knows about a player right now - for displays such as
 * scoreboards, tab lists and placeholders, and for extensions that enforce a
 * mute elsewhere (a voice chat). Provided by the Moderation module through
 * the {@link dev.universaladmin.core.ServiceRegistry}.
 *
 * <p>Every method answers from memory and is safe to call from any thread,
 * as often as a scoreboard or a stream of voice packets needs it. Mutes,
 * warnings and punishment counts are kept for online players and refreshed
 * when they change (on other servers of a network: as soon as the action is
 * announced there, at the latest within a minute); for offline players they
 * read as empty or 0.
 *
 * @since 1.2
 */
public interface PlayerStatus {

    boolean vanished(UUID playerId);

    boolean staffMode(UUID playerId);

    boolean frozen(UUID playerId);

    boolean godmode(UUID playerId);

    /**
     * A mute in force, if the player is muted at all - of any
     * {@link MuteKind}. With several, a full mute comes first.
     */
    Optional<Mute> mute(UUID playerId);

    /**
     * The mute that silences the player in the text chat, if any.
     *
     * @since 1.4
     */
    default Optional<Mute> chatMute(UUID playerId) {
        return mute(playerId).filter(mute -> mute.kind().coversChat());
    }

    /**
     * The mute that silences the player in a voice chat, if any.
     *
     * @since 1.4
     */
    default Optional<Mute> voiceMute(UUID playerId) {
        return mute(playerId).filter(mute -> mute.kind().coversVoice());
    }

    /** Warnings that are in force. */
    int activeWarnings(UUID playerId);

    /** Every punishment the player ever received (kicks and warnings included). */
    int punishments(UUID playerId);

    /** How many online players are vanished. */
    int vanishedOnline();

    /**
     * @param expiresAt {@code null} for a permanent mute
     * @param kind      where the mute silences the player ({@code @since 1.4})
     */
    record Mute(String reason, Instant expiresAt, MuteKind kind) {

        public Mute {
            kind = kind == null ? MuteKind.FULL : kind;
        }

        /** A {@link MuteKind#FULL full} mute - the only kind before 1.4. */
        public Mute(String reason, Instant expiresAt) {
            this(reason, expiresAt, MuteKind.FULL);
        }

        public boolean permanent() {
            return expiresAt == null;
        }
    }
}
