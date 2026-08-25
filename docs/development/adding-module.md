# Adding a New Module (or Building Out a Skeleton)

Seven of the eight built-in modules (all except `players`) are currently
skeletons: they implement `Module`, register an example permission, and
nothing else. This guide uses
[`dev.universaladmin.modules.players`](../../src/main/java/dev/universaladmin/modules/players)
as the complete template - the same steps apply for a brand-new module.

## 1. Domain Model

An immutable `record` per entity.

```java
public record PlayerProfile(UUID id, String lastKnownName, Instant firstJoin, Instant lastSeen) {
    public PlayerProfile withLastSeen(String name, Instant seenAt) { ... }
}
```

## 2. Repository Interface

Extends `dev.universaladmin.storage.Repository<T, ID>`, with
module-specific extra methods where needed.

```java
public interface PlayerProfileRepository extends Repository<PlayerProfile, UUID> {}
```

## 3. Migration

Creates the table. Version 1000 and up (see
[storage.md](../architecture/storage.md) for the version ranges) - check
the highest version already used in the target module before picking a new
number. Handle dialect differences (SQLite vs. MySQL/MariaDB) via
`connection.getMetaData().getDatabaseProductName()`, see
`PlayerProfileMigration`/`AuditSchemaMigration` as an example.

## 4. JDBC Repository Implementation

In a `jdbc` subpackage, async through the module's `TaskScheduler`,
`PreparedStatement` with bound parameters (never string concatenation).
Its own unchecked exception for SQL errors (the `PlayerStorageException`
pattern).

## 5. Application Service

The actual business logic. Only knows the repository *interface*.

```java
public final class PlayerService {
    private final PlayerProfileRepository repository;
    public PlayerService(PlayerProfileRepository repository) { this.repository = repository; }
    public CompletableFuture<PlayerProfile> getOrCreateProfile(UUID playerId, String currentName) { ... }
}
```

## 6. (Optional) Action

Only if the operation should be callable from multiple frontends or needs
to be audited/authorized (see [actions.md](../architecture/actions.md)).

```java
public final class GetPlayerProfileAction implements Action<UUID, PlayerProfile> {
    public static final ActionId ID = ActionId.core("players.get-profile");
    // ...
}
```

## 7. Module Class

A `ModuleDescriptor` (static metadata - see
[modules.md](../architecture/modules.md#moduledescriptor---static-metadata)),
an `onLoad` that registers **only** the migration(s), and an `onEnable`
that wires up the rest: build the repository/service, expose the service
across modules via `ServiceRegistry` if needed, register actions/
permissions.

**Registering the migration belongs in `onLoad`, not `onEnable`.**
`ModuleManager.loadAll()` calls `onLoad` for **every** module before
`enableAll()` calls `onEnable` for any of them - and
`UniversalAdminPlugin#onEnable` calls `storage.migrations().runPending()`
exactly in between. If a module registers its migration in `onEnable`
instead, the table doesn't exist yet at the point where that same
`onEnable` already builds its repository/service - for a service that
triggers a database read immediately when built (even asynchronously/
fire-and-forget), that's a guaranteed `no such table` error, not a
theoretical risk (see
[docs/architecture/threading.md](../architecture/threading.md)).

```java
public final class PlayersModule implements Module {
    public static final ModuleId ID = ModuleId.core("players");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Players")
            .description("Tracks a profile per player (name history, first/last seen).")
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        context.platform().storage().migrations().register(new PlayerProfileMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        var repository = new JdbcPlayerProfileRepository(
                context.platform().storage().dataSource(), context.platform().scheduler());
        var service = new PlayerService(repository);
        context.platform().services().register(PlayerService.class, service);

        context.platform().permissions().register(new PermissionDefinition(
                PermissionNode.core("players.view"), "View player profiles", PermissionDefault.OP));
        context.platform().actions().register(ActionDefinition.builder(new GetPlayerProfileAction(service))
                .permission(PermissionNode.core("players.view"))
                .notAudited()
                .build());
    }
}
```

Only if the module *requires* another built-in module (not "uses it if
present"): `.dependsOn(OtherModule.ID)` on the builder. Don't declare a
dependency without a real reason - see
[modules.md](../architecture/modules.md#dependencies-and-ordering).

If the module registers a Bukkit listener, a scheduler task, or a registry
entry in `onEnable` that should disappear again on disable, that goes
through `context.resources()` instead of manual bookkeeping in
`onDisable` - see
[modules.md](../architecture/modules.md#resource-cleanup):

```java
context.resources().listener(new MyJoinListener(service));
```

## 8. Register in `UniversalAdminPlugin#registerBuiltInModules()`

That's the only place in bootstrap that knows built-in modules by name.
`ModuleManager` then brings them into dependency order itself via
`loadAll()`/`enableAll()` - so the registration order here only has to
guarantee anything when a real `ModuleDescriptor.dependsOn(...)`
dependency is missing that one would otherwise have to rely on.

## 9. Tests

At least one test for the service against an in-memory fake of the
repository (see [testing.md](testing.md) and `PlayerServiceTest`). Test a
migration against a real, temporary SQLite database if it contains
non-trivial SQL. The module's own lifecycle/registration behavior (wrong
state transition, failure isolation, cleanup) is already generically
covered by
[`ModuleManagerTest`](../../src/test/java/dev/universaladmin/module/ModuleManagerTest.java)
- a new module doesn't need its own test for that.

## 10. GUI/Command Frontend

`players` is by now the complete template for this too: see
[`dev.universaladmin.modules.players.gui`](../../src/main/java/dev/universaladmin/modules/players/gui)
(`PlayerBrowserHomePage` as the entry page, further pages like
`AuditLogDetailPage`) and [gui-framework.md](gui-framework.md). Short
version: a page extends `AbstractGuiPage`/`AbstractListGuiPage` instead of
implementing `GuiPage` by hand, gets its services through the constructor
(plus `GuiFramework`), and registers itself in `onEnable` under the same
`GuiPageId` a `PlaceholderGuiPage` currently occupies
(`core:<module>.home`, see `UniversalAdminPlugin#registerMainMenu`) - that
placeholder registration then has to be removed for the respective module
(see the removal of the `players.home` line there as an example). The rule
stays the same: the click handler/command executor only calls the
service/action.

## 11. Update Documentation

`docs/architecture/modules.md` (the module's status row), `ROADMAP.md`
(the checked-off item), and `docs/user/permissions.md` if applicable (a
new permission node).
