package dev.universaladmin.modules.whitelist.action;

import java.time.Instant;
import java.util.UUID;

/** {@code reason}/{@code notes}/{@code expiresAt} are all optional. */
public record AddWhitelistEntryInput(UUID playerId, String playerName, String reason, String notes, Instant expiresAt) {
}
