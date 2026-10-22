package dev.universaladmin.modules.moderation.action;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared input for both {@code MUTE} (permanent, {@code expiresAt == null}) and {@code TEMP_MUTE} - see {@link MuteAction}.
 *
 * @param template the punishment template this mute comes from, {@code null} for a direct mute
 */
public record MuteInput(UUID targetId, String reason, Instant expiresAt, String template) {

    public MuteInput(UUID targetId, String reason, Instant expiresAt) {
        this(targetId, reason, expiresAt, null);
    }
}
