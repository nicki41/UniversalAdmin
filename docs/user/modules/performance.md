# Performance

The Performance module is monitoring and diagnostics only - it is
deliberately **not** an aggressive "lag cleaner" that acts on the server on
its own. Everything it shows comes from standard, reliable Paper/JVM APIs
(`Server#getTPS()`/`getAverageTickTime()`, `World#getChunkCount()`/
`getEntities()`, `Runtime`), sampled on a timer and cached - never
recomputed on every GUI render. The one mutating operation it offers,
Entity Clear, is narrow, filtered, previewed, and confirmed - see below.

Open via `/admin` → **Performance** (needs `universaladmin.performance.view`).

## Sampling and caching

`PerformanceSamplingService` takes one sample every
`core.performance.refresh-interval` (default `5s`, 1s-10m,
**requires a restart** to change) via a single repeating Bukkit task -
never on GUI render, never on the main thread outside that scheduled tick
(reading `World#getEntities()` across every loaded world is not free; doing
it on every menu open would be exactly the "teure Berechnungen bei jedem
GUI Render" this module is built to avoid). The very first sample is taken
synchronously during `onEnable`, so the dashboard has real numbers
immediately rather than showing zeroes until the first scheduled tick.

Every GUI page reads the cached snapshot; `PerformanceSamplingService` is
also registered in the platform's `ServiceRegistry` for any other module
that wants a read-only look at current server load.

## Dashboard

Read-only, one tile each:

- **TPS** - 1/5/15 minute averages (`Server#getTPS()`)
- **MSPT** - average milliseconds per tick (`Server#getAverageTickTime()`)
- **Used / Max Memory** - `Runtime#totalMemory() - freeMemory()` / `maxMemory()`, plus percentage
- **Online Players**
- **Loaded Chunks** - summed across every loaded world
- **Entities** - summed across every loaded world, **players excluded** (see "Players are not entities" below)
- **World Count**
- **Uptime**

The TPS tile also shows a short in-memory trend average (see "History"
below) when at least one sample exists.

## World Performance

One tile per currently loaded world: players, loaded chunks, entities. Same
small, already-in-memory list as the Worlds module's World Browser - no
pagination, no async load.

## Entity Overview

Entities (never players - see below) grouped two ways:

- **By Type** - every currently loaded entity type, sorted by count,
  paginated. Each tile shows a live count and, for a viewer holding
  `universaladmin.performance.entity-clear`, doubles as the entry point to
  clear just that type (see "Entity Clear" below).
- **By World** - reuses the same World Performance page above; the numbers
  are identical, so there is no separate implementation to keep in sync.

Chunk-level drilldown (which chunk a given entity cluster lives in) is not
implemented in this version - noted here as a known limitation, not
forgotten; the type/world breakdown above is usually enough to point staff
at the right world before they go looking in-game.

### Players are not entities

`World#getEntities()` technically includes players, but every count in
this module (dashboard, per-world, by-type, by-world) explicitly excludes
them - they already have their own "Online Players" tile and permission
model, and folding them into "Entities" would make every one of these
numbers misleading about what's actually contributing to entity-related
lag.

## History

A short, bounded, in-memory-only window (`PerformanceHistory`, 120 samples
- about 10 minutes at the default 5s interval) of TPS/MSPT/memory samples,
used today for the dashboard's "5m avg TPS" line. **Not persisted** - see
ROADMAP.md: long metric history and real charts are a future web-app
feature, not part of this core version.

## Alert Hooks

`PerformanceSamplingService` checks three thresholds after every sample and,
on a breach, notifies every online player holding
`universaladmin.performance.view` via `NotificationService#notifyStaff` -
the same interface a future Discord/web-push channel would implement,
rather than a bespoke transport for this module:

| Alert | Setting | Default |
|---|---|---|
| TPS below threshold | `performance.alerts.tps-threshold` | `18.0` |
| MSPT above threshold | `performance.alerts.mspt-threshold-ms` | `50.0` |
| Memory above threshold | `performance.alerts.memory-threshold-percent` | `90.0` |

An alert fires at most once per `performance.alerts.cooldown` (default
`5m`) while the same threshold stays breached, so a persisting problem
doesn't spam staff every refresh interval - this is deliberately simple
hysteresis, not a full alert engine (no escalation levels, no
acknowledgement, no per-recipient preferences). Alert messages are plain
text (not MiniMessage) because `NotificationService`'s in-game
implementation renders `Notification#message()` literally - matching the
existing precedent (see e.g. `moderation.enforcement.frozen-disconnect`).

## Entity Clear

Deliberately narrow, per its design constraints - never all entities
indiscriminately, never players, dangerous/valuable types excluded by
default, filter + live preview + confirmation + audit every time:

- **Never players.** Hard-coded in `EntityClearFilter`, not just a GUI
  convention - `ClearEntitiesAction` itself refuses to touch a `Player`
  regardless of what a caller requests.
- **Protected types excluded by default.** `performance.entity-clear.protected-types`
  (default: villagers, wandering traders, armor stands, item frames, glow
  item frames, paintings, the ender dragon, the wither, allays) are always
  stripped from the requested type set before anything runs - a caller
  cannot opt back into removing a protected type by asking for it directly.
- **Named/tamed/leashed entities are always skipped**, regardless of type -
  a config typo should not be the only thing standing between a lag-cleanup
  click and someone's named pet.
- **Filter.** Clear a single type (Entity Overview → By Type → click a
  non-protected tile) or "Clear All Non-Protected" (Entity Overview) across
  every currently loaded world - this version does not offer a per-world
  scope from the GUI (`ClearEntitiesAction`'s input supports it for a future
  command/API caller, just not exposed here yet).
- **Preview count.** Shown in the confirmation dialog, computed by the exact
  same `EntityClearFilter` predicate the action itself uses (via
  `PerformanceSamplingService#previewClearCount`) - what a player confirms
  is what actually happens, never an approximation.
- **Confirmation.** A dangerous `ConfirmationDialog`, same as
  shutdown/restart/ban.
- **Audit.** Every clear runs through `ActionExecutor` like any other
  action - actor, the effective type set, world scope, and removed count
  are all on the resulting audit entry, never a bespoke log line.

Permission: `universaladmin.performance.entity-clear`.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.performance.view` | op | View performance diagnostics (dashboard, world/entity breakdown); also who receives alert notifications |
| `universaladmin.performance.entity-clear` | op | Clear non-player entities matching a filter, with preview and confirmation |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `core.performance.refresh-interval` | `5s` | How often the dashboard/world/entity caches refresh (1s-10m). **Requires a restart.** |
| `performance.alerts.tps-threshold` | `18.0` | TPS (1-minute average) below which a staff alert fires |
| `performance.alerts.mspt-threshold-ms` | `50.0` | Average MSPT above which a staff alert fires |
| `performance.alerts.memory-threshold-percent` | `90.0` | Used/max heap memory percentage above which a staff alert fires |
| `performance.alerts.cooldown` | `5m` | Minimum time between two alerts of the same kind |
| `performance.entity-clear.protected-types` | see above | Entity types Entity Clear always refuses to remove |

`core.performance.refresh-interval` predates this module's real
implementation and stays a `core`-namespaced setting; everything else above
is registered under the module's own `performance` settings namespace (see
[docs/development/settings.md](../development/settings.md)). None of the
new settings require a restart.

## Limitations

- No long-term metric history or charts - see "History" above; that is
  intentionally a future web-app feature, not this core version.
- No chunk-level drilldown in the Entity Overview - see that section above.
- Entity Clear has no per-world scope in the GUI yet (all loaded worlds
  only) - the underlying action already accepts one for a future
  command/API frontend.
- No dedicated `/admin performance ...` console command yet - console can
  only be reached through whatever a future command frontend adds; this
  version is fully manageable in-game via the GUI, same situation as the
  Server module's maintenance mode.
