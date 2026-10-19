package dev.universaladmin.action;

import java.util.UUID;

/**
 * A generic, action-independent description of what an {@link Action} input
 * targets - a player, a world, a whitelist entry, ... Extracted from the
 * action-specific input type by {@link ActionDefinition#targetExtractor()}
 * so {@link ActionExecutor} can check self-target restrictions and record a
 * {@code targetId} on the audit event without knowing anything about the
 * input type itself.
 */
public record ActionTarget(String type, String id, String displayName) {

    public static final String PLAYER_TYPE = "player";

    public static ActionTarget player(UUID id, String displayName) {
        return new ActionTarget(PLAYER_TYPE, id.toString(), displayName);
    }

    public static ActionTarget of(String type, String id, String displayName) {
        return new ActionTarget(type, id, displayName);
    }
}
