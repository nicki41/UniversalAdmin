package dev.universaladmin.modules.moderation.action;

import java.time.Instant;
import java.util.UUID;

/** Shared input for both {@code MUTE} (permanent, {@code expiresAt == null}) and {@code TEMP_MUTE} - see {@link MuteAction}. */
public record MuteInput(UUID targetId, String reason, Instant expiresAt) {
}
