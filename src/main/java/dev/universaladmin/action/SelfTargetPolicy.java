package dev.universaladmin.action;

/**
 * Whether an {@link Action} may be run with a target that resolves to the
 * same player as the {@link Actor} - e.g. a player kicking/banning
 * themselves. Most actions have no meaningful notion of "self" and use
 * {@link #ALLOWED} (the default); actions that do (kick, ban, teleport-to)
 * opt into {@link #FORBIDDEN} via {@link ActionDefinition.Builder#forbidSelfTarget()}.
 */
public enum SelfTargetPolicy {
    ALLOWED,
    FORBIDDEN
}
