package dev.universaladmin.voicechat;

import dev.universaladmin.status.PlayerStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Decides whether a player's microphone packets get through: not while a
 * voice or full mute is in force, and not while vanished if
 * {@code voicechat.silence-vanished} is on. Answers from
 * {@link PlayerStatus}, which reads from memory - safe for the voice chat's
 * own thread and its packet rate.
 */
final class VoiceGate {

    /** Why packets are dropped. */
    enum Reason {
        MUTED,
        VANISHED
    }

    /** @param mute the mute in force for {@link Reason#MUTED}, {@code null} otherwise */
    record Block(Reason reason, PlayerStatus.Mute mute) {
    }

    private final PlayerStatus status;
    private final BooleanSupplier silenceVanished;

    VoiceGate(PlayerStatus status, BooleanSupplier silenceVanished) {
        this.status = status;
        this.silenceVanished = silenceVanished;
    }

    /**
     * @param inStaffGroup whether the player talks in the staff voice group -
     *                     nobody else hears that, so vanish needs no silence there
     */
    Optional<Block> check(UUID playerId, boolean inStaffGroup) {
        Optional<PlayerStatus.Mute> mute = status.voiceMute(playerId);
        if (mute.isPresent()) {
            return Optional.of(new Block(Reason.MUTED, mute.get()));
        }
        if (!inStaffGroup && silenceVanished.getAsBoolean() && status.vanished(playerId)) {
            return Optional.of(new Block(Reason.VANISHED, null));
        }
        return Optional.empty();
    }
}
