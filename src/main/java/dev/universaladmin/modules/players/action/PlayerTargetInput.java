package dev.universaladmin.modules.players.action;

import java.util.UUID;

/** Minimal input for a Players action that only needs a target player - see {@link OnlinePlayerAction}. */
public record PlayerTargetInput(UUID targetId) {
}
