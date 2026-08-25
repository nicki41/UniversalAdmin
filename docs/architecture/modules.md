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

| Modul | Package | Status |
|---|---|---|
| Players | `dev.universaladmin.modules.players` | Vollständig: Player-Browser-GUI (online/offline/zuletzt gesehen/Suche), Profilseite, ~20 Actions (Teleport, Heilen, Effekte, Gamemode, Inventar/Enderchest-Editor), feingranulare Permissions, Audit - siehe [docs/user/modules/players.md](../user/modules/players.md) |
| Moderation | `dev.universaladmin.modules.moderation` | Vollständig: Punishment-Repository/-Service (kick/ban/tempban/ipban/mute/tempmute/warn/freeze/unban/unmute/removewarn/unfreeze), Join-/Chat-Enforcement, GUI-Wizard, feingranulare Permissions, Audit, plus Vanish/Godmode/No-Collision/Staff-Mode (Crash-sicheres Snapshot/Recovery, `/admin staff recover`) - siehe [docs/user/modules/moderation.md](../user/modules/moderation.md) und [docs/user/modules/staff-tools.md](../user/modules/staff-tools.md) |
| Server | `dev.universaladmin.modules.server` | Vollständig: Live-Dashboard (Version/Uptime/Spieler/Memory/CPU/DB-Status/Module), Broadcast (Message/Title/Actionbar), eigenes Maintenance-Mode-System (Repository/Service, Join-Enforcement, Allow-List), Shutdown/Restart mit Dangerous-Confirmation und konfigurierbarem Countdown + Cancel, `/admin server ...`, feingranulare Permissions, Audit - siehe [docs/user/modules/server.md](../user/modules/server.md) |
| Worlds | `dev.universaladmin.modules.worlds` | Vollständig: World-Browser/-Profil (Environment/Seed*/Spawn/Border/Players/Chunks/Entities/Time/Weather/Difficulty), Teleport/Spawn/Time/Weather/Difficulty-Actions, World-Border-Verwaltung, dynamisches Gamerule-GUI (liest `World#getGameRules()` zur Laufzeit, kein Rule fest verdrahtet), feingranulare Permissions (Seed separat), Audit, keine eigene Datenbank (alles Bukkit-persistiert) - siehe [docs/user/modules/worlds.md](../user/modules/worlds.md) |
| Whitelist | `dev.universaladmin.modules.whitelist` | Vollständig: natives Whitelist-Wrapping (enable/disable/list/add/remove) plus eigene Metadaten (added-by/at, reason, notes, Ablauf), befristete Einträge mit Join-Check und stündlichem Sweep, striktes Ownership-Modell (automatische Pfade fassen nie fremd gesetzte Einträge an), feingranulare Permissions, Audit - siehe [docs/user/modules/whitelist.md](../user/modules/whitelist.md) |
| Performance | `dev.universaladmin.modules.performance` | Vollständig: gecachtes TPS/MSPT/Memory/World/Entity-Sampling auf konfigurierbarem Intervall (`PerformanceSamplingService`, nie pro GUI-Render neu berechnet), Dashboard, World-Performance-Ansicht, Entity-Overview (nach Typ/Welt gruppiert), kurze In-Memory-Historie, Staff-Alerts bei TPS/MSPT/Memory-Schwellenwerten (`NotificationService#notifyStaff`), eng gefasstes Entity Clear (nie Spieler, konfigurierbare geschützte Typen, Preview, Confirmation, Audit), feingranulare Permissions - siehe [docs/user/modules/performance.md](../user/modules/performance.md) |
| Audit Log | `dev.universaladmin.modules.auditlog` | Skelett, GUI/Commands fehlen (der Service selbst existiert schon als Core-Service `dev.universaladmin.audit`) |
| Settings | `dev.universaladmin.modules.settings` | Skelett, GUI/Commands fehlen (der Service existiert schon als Core-Service `dev.universaladmin.settings.SettingsService`) |

"Skelett" heißt: implementiert `Module`, kompiliert, registriert eine
Beispiel-Permission, hat aber noch keine Repository/Service/Action/GUI-
Kette. Der Ausbau folgt [adding-module.md](../development/adding-module.md)
mit `players` als Vorlage. Alle acht werden in
`UniversalAdminPlugin#registerBuiltInModules` registriert - das ist die
**einzige** Stelle, die built-in Module kennt; nichts sonst im Bootstrap
hardcoded einzelne Module. Jedes wird nur registriert, wenn sein
`modules.<name>`-Setting (siehe
[docs/user/configuration.md](../user/configuration.md#modules)) auf `true`
steht - ein per Config deaktiviertes Modul erreicht `ModuleRegistry` nie
und taucht dementsprechend auch nicht als `FAILED` auf, es existiert für
diesen Lauf schlicht nicht.

### Warum Audit Log und Settings *auch* Module sind

Audit-Logging und Konfiguration sind Core-Services (`AuditService`,
`SettingsService`), die von Anfang an existieren, weil andere Module sie
brauchen (jede Action soll auditieren können; jedes Modul kann Settings lesen
wollen). Die `AuditLogModule`/`SettingsModule` sind trotzdem eigene Module,
weil sie eine eigene GUI-/Command-Oberfläche bekommen - "gibt es als
Service" und "hat eine eigene Nutzeroberfläche" sind unterschiedliche
Dinge, und die Modul-Registrierung ist, wie jede sichtbare Funktionalität,
konsistent über `Module` modelliert statt als Sonderfall.

## Cross-module communication

Ein Modul greift nie direkt auf eine interne Klasse eines anderen Moduls
zu. Braucht Moderation z. B. `PlayerService` aus Players, schlägt es das
über `context.platform().services().require(PlayerService.class)` nach -
das funktioniert nur, wenn Players zuvor erfolgreich enabled hat. Ist das
zwingend (nicht nur "praktisch, falls vorhanden"), gehört die Abhängigkeit
zusätzlich in `ModuleDescriptor.dependencies()`, damit `ModuleManager` die
Reihenfolge garantiert und ein fehlgeschlagenes Players-Modul das
abhängige Modul sauber als `FAILED` markiert statt mit einer
`NoSuchElementException` aus `ServiceRegistry.require` abzustürzen.

## Extension-ready, on purpose

Nothing about `Module`/`ModuleDescriptor`/`ModuleRegistry`/`ModuleManager`
is built-in-only. This is the same lifecycle a future external extension
would go through - no JAR loader, no classloader isolation, no public SDK
yet (that's explicitly out of scope for now, see
[extensions-future.md](extensions-future.md)), but the internal contract
itself does not need to change when that arrives. See
[decisions/0005-extension-ready-design.md](decisions/0005-extension-ready-design.md).
