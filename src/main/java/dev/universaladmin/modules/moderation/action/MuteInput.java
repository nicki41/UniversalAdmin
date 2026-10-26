package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.status.MuteKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared input for both {@code MUTE} (permanent, {@code expiresAt == null}) and {@code TEMP_MUTE} - see {@link MuteAction}.
 *
 * @param template the punishment template this mute comes from, {@code null} for a direct mute
 * @param kind     where the mute silences the player - {@link MuteKind#FULL} unless chosen (ADR 0022)
 */
public record MuteInput(UUID targetId, String reason, Instant expiresAt, String template, MuteKind kind) {

    public MuteInput {
        kind = kind == null ? MuteKind.FULL : kind;
    }

    public MuteInput(UUID targetId, String reason, Instant expiresAt, String template) {
        this(targetId, reason, expiresAt, template, MuteKind.FULL);
    }

    public MuteInput(UUID targetId, String reason, Instant expiresAt) {
        this(targetId, reason, expiresAt, null, MuteKind.FULL);
    }
}
