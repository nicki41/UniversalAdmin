package dev.universaladmin.modules.moderation.action;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared input for both {@code BAN} (permanent, {@code expiresAt == null}) and {@code TEMP_BAN} - see {@link BanAction}.
 *
 * @param template the punishment template this ban comes from, {@code null} for a direct ban
 */
public record BanInput(UUID targetId, String reason, Instant expiresAt, String template) {

    public BanInput(UUID targetId, String reason, Instant expiresAt) {
        this(targetId, reason, expiresAt, null);
    }
}
