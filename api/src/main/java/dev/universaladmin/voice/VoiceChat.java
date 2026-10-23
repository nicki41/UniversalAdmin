package dev.universaladmin.voice;

import java.util.UUID;

/**
 * A voice chat connected to UniversalAdmin by an extension - the official
 * one is UniversalAdmin Voice Chat, for Simple Voice Chat. Registered in the
 * {@link dev.universaladmin.core.ServiceRegistry} while that extension runs.
 *
 * <p>While one is registered, staff can choose the kind of a mute
 * ({@link dev.universaladmin.status.MuteKind}); the extension enforces the
 * voice part itself, from
 * {@link dev.universaladmin.status.PlayerStatus#voiceMute(UUID)}.
 *
 * @since 1.4
 */
public interface VoiceChat {

    /** The voice chat's name, e.g. {@code "Simple Voice Chat"} - shown to staff. */
    String name();

    /** Whether the player is connected to the voice chat right now. */
    boolean connected(UUID playerId);
}
