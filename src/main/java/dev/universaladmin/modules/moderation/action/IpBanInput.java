package dev.universaladmin.modules.moderation.action;

import java.time.Instant;
import java.util.UUID;

public record IpBanInput(UUID targetId, String reason, Instant expiresAt) {
}
