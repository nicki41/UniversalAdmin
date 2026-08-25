package dev.universaladmin.modules.moderation.action;

import java.time.Instant;
import java.util.UUID;

/** Shared input for both {@code BAN} (permanent, {@code expiresAt == null}) and {@code TEMP_BAN} - see {@link BanAction}. */
public record BanInput(UUID targetId, String reason, Instant expiresAt) {
}
