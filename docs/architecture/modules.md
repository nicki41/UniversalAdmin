# Modules

## What a module is

A [`Module`](../../src/main/java/dev/universaladmin/module/Module.java) is
a self-contained unit of functionality: Players, Moderation, later an
external "Discord Integration" extension. A module registers itself with
the shared registries (actions, GUI pages, permissions, migrations) and
otherwise contains no logic - the logic lives in what it registers.

```java
public interface Module {
    ModuleDescriptor descriptor();
    default void onLoad(ModuleContext context) {}
    void onEnable(ModuleContext context);
    default void onDisable(ModuleContext context) {}
}
```

`ModuleContext` is the module-scoped view over the shared platform: the
same registries/services every module uses, a logger prefixed with the
module id, and a [`ModuleResources`](#resource-cleanup) for anything that
needs to be released on disable.

## `ModuleDescriptor` - static metadata

Everything knowable about a module without running any of its lifecycle
methods lives in [`ModuleDescriptor`](../../src/main/java/dev/universaladmin/module/ModuleDescriptor.java),
built once via a small builder:

```java
private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Players")
        .description("Tracks a profile per player (name history, first/last seen).")
        .dependsOn(SomeOtherModule.ID)   // only if actually needed - see "Dependencies" below
        .permissionNamespace("players")  // defaults to the module id's bare name
        .settingsNamespace("players")    // defaults to the module id's bare name
        .icon(new GuiIcon("PLAYER_HEAD", "Players"))  // optional, see below
        .build();
```

- `permissionNamespace` documents that this module's nodes are expected
  under `universaladmin.<permissionNamespace>.*` (see
  [PermissionNode.core(...)](../../src/main/java/dev/universaladmin/permission/PermissionNode.java));
  not enforced today. `settingsNamespace` is the namespace a module would
  register its own settings under (`SettingKey.of(descriptor().settingsNamespace(), ...)`,
  see [docs/development/settings.md](../development/settings.md)) - `Players`
  is the first built-in module to actually use this (`PlayersSettings.GUI_MAX_RESULTS`,
  registered via `context.platform().settingRegistry()` in `PlayersModule#onEnable`),
  alongside the platform-level `core` settings.
- `icon` is a [`GuiIcon`](../../src/main/java/dev/universaladmin/module/GuiIcon.java)
  placeholder (a plain material-key string, no Bukkit dependency) resolved
  by the GUI framework's `IconProvider` for a module's main-menu button and
  its own pages - see [gui.md](gui.md) and `PlayersModule`'s
  `new GuiIcon("player_head", "Players")` for a real, in-use example.

## Lifecycle states

```
DISCOVERED --loadAll()--> LOADED --enableAll()--> ENABLED --disableAll()--> DISABLED
     \                        \
      \--(unknown dependency)  \--(onLoad/onEnable threw, or a
          -----> FAILED             dependency isn't ENABLED) -----> FAILED
```

[`ModuleRegistry`](../../src/main/java/dev/universaladmin/module/ModuleRegistry.java)
stores every registered module and its current
[`ModuleState`](../../src/main/java/dev/universaladmin/module/ModuleState.java).
It is pure bookkeeping - only
[`ModuleManager`](../../src/main/java/dev/universaladmin/module/ModuleManager.java)
drives transitions. `ModuleRegistry` lives on `UniversalAdmin`
(`platform.moduleRegistry()`) so anything with a platform reference (the
`/admin` status command, a future GUI module list) can read module state
without depending on `ModuleManager` itself.

A module that reaches `FAILED` stays there for the rest of the plugin's
run - it is never retried and never transitions to `DISABLED`, since it was
never actually enabled.

## Why two phases (`onLoad` then `onEnable`)

Most modules only need `onEnable`. `onLoad` exists for the rare case where
a module must prepare something *before* dependency resolution runs (e.g.
before any module's `onEnable` - which may read another module's
registered service - executes). `ModuleManager` calls `onLoad` for every
module (in dependency order) before calling `onEnable` for any of them.

## Dependencies and ordering

A module declares dependencies on other modules via
`ModuleDescriptor.dependencies()` (set with `.dependsOn(ModuleId...)` on
the builder) - never via a direct Java import of another module's internal
class (see [Cross-Module-Kommunikation](#cross-module-communication)).
`ModuleManager` topologically sorts modules so a dependency always
loads/enables before its dependent, using Kahn's algorithm with
registration order as the tie-break for independent modules.

None of the eight built-in modules declare a dependency today - none
actually needs one yet, and declaring one without a real reason would be
exactly the kind of speculative coupling [Entwicklungsregeln](../development/architecture-rules.md) warns
against. The machinery is exercised by
[`ModuleManagerTest`](../../src/test/java/dev/universaladmin/module/ModuleManagerTest.java)
today; a real built-in dependency (e.g. Moderation eventually depending on
Players) is expected once Moderation actually calls into `PlayerService`.

**A dependency cycle is not the same as a missing/failed dependency.** A
cycle means the declared module graph is broken - `ModuleManager.loadAll()`
throws `IllegalStateException` instead of trying to route around it, since
no ordering exists that could satisfy it. A dependency that was never
registered, or that itself ended up `FAILED`, only fails the *dependent*
module (see below) - the rest of the server keeps starting.

## Failure isolation: what's critical, what isn't

A **module is never critical** - see [`ModuleManager`](../../src/main/java/dev/universaladmin/module/ModuleManager.java)'s
class Javadoc. If `onLoad` or `onEnable` throws, or a declared dependency
never reached `ENABLED`, that module (and only that module) is marked
`FAILED`, the exception is logged in full at `SEVERE`, and every other
module still gets its chance to load/enable.

**Core bootstrap components are critical.** `UniversalAdminPlugin#bootstrapCore`
constructs, in order: config-version migration, `SettingRegistry`+`SettingsService`,
`TaskScheduler`, `StorageService` (including running pending migrations),
and the shared registries
(`ServiceRegistry`, `ActionRegistry`, `GuiRegistry`, `PermissionRegistry`,
`ModuleRegistry`), plus `AuditService`, `MessageService`,
`NotificationService`. If *any* of this throws, there is no module-level
isolation that makes sense - nothing in the plugin can function without a
working config/scheduler/storage layer - so the whole plugin fails to
start: the exception is logged with context, and
`getServer().getPluginManager().disablePlugin(this)` is called. This is
the one and only place startup aborts entirely instead of isolating a
failure.

## Resource cleanup

A module should never leak a Bukkit listener, a scheduled task, or a
registry entry past its own lifetime.
[`ModuleResources`](../../src/main/java/dev/universaladmin/module/ModuleResources.java)
(one instance per module, reachable via `context.resources()`) tracks
anything registered through it and releases everything - in reverse
registration order, one failure never blocking the rest - when the module
is disabled, **or immediately if `onEnable` itself throws**, so a module
that fails halfway through registering listeners never leaks the ones it
already added.

```java
@Override
public void onEnable(ModuleContext context) {
    context.resources().listener(new MyJoinListener(playerService));
    context.resources().task(myRepeatingTask);
    context.resources().closeable(() -> context.platform().actions().unregister(MY_ACTION_ID));
}
```

A module's own `onDisable` is still the right place for cleanup that isn't
expressible as a tracked resource (closing an internal cache, for
example) - `ModuleResources` handles the common cases so `onDisable` does
not have to repeat them.

## Built-in modules

| Module | Package | Status |
|---|---|---|
| Players | `dev.universaladmin.modules.players` | Complete: player browser GUI (online/offline/last-seen/search), profile page, ~20 actions (teleport, heal, effects, gamemode, inventory/ender chest editor), fine-grained permissions, audit - see [docs/user/modules/players.md](../user/modules/players.md) |
| Moderation | `dev.universaladmin.modules.moderation` | Complete: punishment repository/service (kick/ban/tempban/ipban/mute/tempmute/warn/freeze/unban/unmute/removewarn/unfreeze), join/chat enforcement, GUI wizard, fine-grained permissions, audit, plus vanish/godmode/no-collision/staff mode (crash-safe snapshot/recovery, `/admin staff recover`) - see [docs/user/modules/moderation.md](../user/modules/moderation.md) and [docs/user/modules/staff-tools.md](../user/modules/staff-tools.md) |
| Server | `dev.universaladmin.modules.server` | Complete: live dashboard (version/uptime/players/memory/CPU/DB status/modules), broadcast (message/title/actionbar), its own maintenance-mode system (repository/service, join enforcement, allow-list), shutdown/restart with dangerous-action confirmation and a configurable countdown + cancel, `/admin server ...`, fine-grained permissions, audit - see [docs/user/modules/server.md](../user/modules/server.md) |
| Worlds | `dev.universaladmin.modules.worlds` | Complete: world browser/profile (environment/seed*/spawn/border/players/chunks/entities/time/weather/difficulty), teleport/spawn/time/weather/difficulty actions, world border management, a dynamic gamerule GUI (reads `World#getGameRules()` at runtime, no rule hardcoded), fine-grained permissions (seed kept separate), audit, no database of its own (everything Bukkit-persisted) - see [docs/user/modules/worlds.md](../user/modules/worlds.md) |
| Whitelist | `dev.universaladmin.modules.whitelist` | Complete: native whitelist wrapping (enable/disable/list/add/remove) plus its own metadata (added-by/at, reason, notes, expiration), time-limited entries with a join check and hourly sweep, a strict ownership model (automated paths never touch entries set by someone else), fine-grained permissions, audit - see [docs/user/modules/whitelist.md](../user/modules/whitelist.md) |
| Performance | `dev.universaladmin.modules.performance` | Complete: cached TPS/MSPT/memory/world/entity sampling on a configurable interval (`PerformanceSamplingService`, never recomputed per GUI render), dashboard, per-world performance view, entity overview (grouped by type/world), short in-memory history, staff alerts on TPS/MSPT/memory thresholds (`NotificationService#notifyStaff`), a deliberately narrow Entity Clear (never players, configurable protected types, preview, confirmation, audit), fine-grained permissions - see [docs/user/modules/performance.md](../user/modules/performance.md) |
| Audit Log | `dev.universaladmin.modules.auditlog` | Skeleton, GUI/commands missing (the service itself already exists as the core service `dev.universaladmin.audit`) |
| Settings | `dev.universaladmin.modules.settings` | Skeleton, GUI/commands missing (the service already exists as the core service `dev.universaladmin.settings.SettingsService`) |

"Skeleton" means: implements `Module`, compiles, registers an example
permission, but has no repository/service/action/GUI chain yet. Building
it out follows [adding-module.md](../development/adding-module.md) with
`players` as the template. All eight are registered in
`UniversalAdminPlugin#registerBuiltInModules` - that's the **only** place
that knows about built-in modules; nothing else in bootstrap hardcodes
individual modules. Each one is only registered if its `modules.<name>`
setting (see
[docs/user/configuration.md](../user/configuration.md#modules)) is `true`
- a module disabled via config never reaches `ModuleRegistry` and
accordingly never shows up as `FAILED` either; for that run it simply
doesn't exist.

### Why Audit Log and Settings Are *Also* Modules

Audit logging and configuration are core services (`AuditService`,
`SettingsService`) that exist from the start because other modules need
them (every action should be able to audit; every module may want to read
settings). The `AuditLogModule`/`SettingsModule` are still their own
modules because they get their own GUI/command surface - "exists as a
service" and "has its own user interface" are different things, and module
registration, like any visible functionality, is modeled consistently
through `Module` instead of as a special case.

## Cross-module communication

A module never directly accesses another module's internal class. If
Moderation needs, say, `PlayerService` from Players, it looks it up via
`context.platform().services().require(PlayerService.class)` - which only
works once Players has successfully enabled. If that's mandatory (not just
"nice to have if present"), the dependency additionally belongs in
`ModuleDescriptor.dependencies()`, so `ModuleManager` guarantees the
ordering and a failed Players module cleanly marks the dependent module as
`FAILED` instead of crashing with a `NoSuchElementException` from
`ServiceRegistry.require`.

## Extension-ready, on purpose

Nothing about `Module`/`ModuleDescriptor`/`ModuleRegistry`/`ModuleManager`
is built-in-only. This is the same lifecycle a future external extension
would go through - no JAR loader, no classloader isolation, no public SDK
yet (that's explicitly out of scope for now, see
[extensions-future.md](extensions-future.md)), but the internal contract
itself does not need to change when that arrives. See
[decisions/0005-extension-ready-design.md](decisions/0005-extension-ready-design.md).
