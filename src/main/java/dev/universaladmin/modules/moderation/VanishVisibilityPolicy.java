package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.Actor;
import java.util.UUID;

/**
 * Extension point for future vanish tiers ("hidden from everyone except
 * senior staff") - same {@code ServiceRegistry}-lookup-or-default shape as
 * {@link ModerationPolicy}: {@code ModerationModule#onEnable} picks up an
 * already-registered policy before falling back to {@link #bypassPermissionOnly()},
 * so a future rank/hierarchy extension can register a stricter one without
 * this module changing. No rank system exists today, so the default is the
 * one thing that's actually requested: {@code universaladmin.bypass.vanish}.
 */
public interface VanishVisibilityPolicy {

    /** Whether {@code viewer} may see a player vanished at {@code level}. */
    boolean canSee(Actor viewer, UUID vanishedPlayerId, VanishLevel level);

    static VanishVisibilityPolicy bypassPermissionOnly() {
        return (viewer, vanishedPlayerId, level) -> viewer.hasPermission(ModerationPermissions.BYPASS_VANISH);
    }
}
