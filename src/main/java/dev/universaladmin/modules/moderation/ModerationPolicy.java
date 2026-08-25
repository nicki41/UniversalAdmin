package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.Actor;
import java.util.UUID;

/**
 * Extension point for a future staff-hierarchy rule ("a moderator can't
 * punish an admin") - docs/development/architecture-rules.md asks this module to prepare the hook without
 * building the actual hierarchy logic yet, since nothing in the codebase has
 * a rank/group concept today (see docs/architecture/decisions/0005-extension-ready-design.md
 * for why built-in modules stay on the same extension seams a future
 * extension would use).
 *
 * <p>{@link ModerationModule#onEnable} looks this up via {@code
 * ServiceRegistry} before falling back to {@link #allowAll()}: a future
 * extension that depends on the moderation module (see {@code
 * ModuleDescriptor#dependsOn}) can register its own {@link ModerationPolicy}
 * first, and every punishing action here picks it up automatically. Kept as
 * a plain per-action check rather than a new hook on {@code ActionExecutor}/
 * {@code ActionDefinition} (core) - nothing else needs this capability yet,
 * see docs/development/architecture-rules.md's "Built-in Modules Stay Extension-Friendly" guidance.
 */
public interface ModerationPolicy {

    /** Whether {@code actor} is allowed to apply {@code type} to {@code targetId}. */
    boolean canPunish(Actor actor, PunishmentType type, UUID targetId);

    static ModerationPolicy allowAll() {
        return (actor, type, targetId) -> true;
    }
}
