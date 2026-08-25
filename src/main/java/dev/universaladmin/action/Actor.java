package dev.universaladmin.action;

import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.permission.PermissionNode;
import java.util.Objects;
import java.util.UUID;

/**
 * Who is performing an {@link Action}. {@code playerId} is {@code null} for
 * non-player actors (console, system tasks, and web actors until a web
 * layer exists). Carries a {@link PermissionEvaluator} so authorization
 * ({@link #hasPermission}) is answered the same way regardless of actor kind,
 * instead of every caller reaching for {@code player.hasPermission(...)}
 * directly - see docs/architecture/actions.md.
 */
public record Actor(ActorType type, UUID playerId, String displayName, PermissionEvaluator permissions) {

    public Actor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(permissions, "permissions");
    }

    public static Actor player(UUID playerId, String displayName, PermissionEvaluator permissions) {
        return new Actor(ActorType.PLAYER, playerId, displayName, permissions);
    }

    /** The console is always trusted - see {@link PermissionEvaluator#allowAll()}. */
    public static Actor console() {
        return new Actor(ActorType.CONSOLE, null, "CONSOLE", PermissionEvaluator.allowAll());
    }

    /** Scheduled tasks, migrations, startup routines - always trusted, like the console. */
    public static Actor system(String displayName) {
        return new Actor(ActorType.SYSTEM, null, displayName, PermissionEvaluator.allowAll());
    }

    /**
     * A future web-app session. No web layer exists yet (see ROADMAP.md); this
     * exists so {@code action} already has a place for one to plug in its own
     * {@link PermissionEvaluator} (resolved from the web session, not Bukkit)
     * without another change to this record.
     */
    public static Actor web(String displayName, PermissionEvaluator permissions) {
        return new Actor(ActorType.WEB, null, displayName, permissions);
    }

    public boolean hasPermission(PermissionNode node) {
        return permissions.has(node);
    }
}
