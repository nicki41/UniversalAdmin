# Architecture Overview

Extended version of [ARCHITECTURE.md](../../ARCHITECTURE.md). This document
goes package by package through what exists and why.

## The Layers in Code

| Layer | Package(s) | Example |
|---|---|---|
| Frontend | `gui`, `command` (later: web) | `UniversalAdminCommand` |
| Application Services | module-internal, e.g. `modules.players.PlayerService` | `PlayerService` |
| Actions / Domain Logic | `action`, module-internal `modules.*.action` | `GetPlayerProfileAction` |
| Repositories | `storage` (interface), `storage.jdbc`/`modules.*.jdbc` (adapter) | `JdbcPlayerProfileRepository` |
| Paper / Database | Bukkit API, `javax.sql.DataSource` | - |

Each row only knows the row below it through an interface, never a concrete
implementation two rows down. A `GuiPage` knows a `Service`, never a
`Repository`. A `Service` knows a `Repository` interface, never
`Connection`.

## The Composition Root: `dev.universaladmin.core`

- `UniversalAdmin` - holds every shared registry/shared service (plugin
  instance, version, start time, `SettingRegistry`+`SettingsService`,
  scheduler, storage, actions, GUI pages, `GuiFramework` (sessions/icons -
  see [docs/development/gui-framework.md](../development/gui-framework.md)),
  permissions, `ModuleRegistry`, audit, messages, notifications, a generic
  `ServiceRegistry` for services shared across modules). Built once in
  `UniversalAdminPlugin#bootstrapCore` and passed to every module via
  `ModuleContext`.
- `PluginStatus`/`ComponentStatus` - `UniversalAdmin.status()` builds a
  fresh snapshot on every call (version, uptime, active/failed modules from
  `ModuleRegistry`, coarse database/web status). Nothing is cached - two
  calls a minute apart can show different module lists. Used by the
  `/admin` command.
- `ServiceRegistry` - a type-based registry (`Class<T> → T`) for services
  more than one module needs (example: `PlayerService`, which
  `PlayersModule` registers and which e.g. `ModerationModule` can later
  look up without building its own copy).
- `registry.Registry` / `registry.SimpleRegistry` - the generic,
  thread-safe registry implementation `ActionRegistry`, `GuiRegistry`, and
  `PermissionRegistry` build on.
- `id.Key` - a `namespace:name` identifier, the basis for `ModuleId`,
  `ActionId`, `GuiPageId`, `AuditEventType`. The namespace is the collision
  protection for future extensions (`core:players` vs. `myext:players`).

## `module` - the Lifecycle System

`Module` is the interface every built-in module (and later every
extension) implements: a `ModuleDescriptor` (id, name, description,
dependencies, settings/permission namespace, optional GUI icon), plus
`onLoad`/`onEnable`/`onDisable`. `ModuleRegistry` tracks each module's state
(`DISCOVERED`/`LOADED`/`ENABLED`/`DISABLED`/`FAILED`); `ModuleManager`
drives the transitions in dependency order (topological sort over
`ModuleDescriptor.dependencies()`) and isolates a failing module (`FAILED`,
fully logged) instead of aborting the whole server start.
`ModuleResources` (part of `ModuleContext`) automatically releases
listeners/tasks/registry entries a module registers in `onEnable` again on
disable. Full detail: [modules.md](modules.md).

## `action` - Where Business Logic Lives

`Action<I, R>` is a single operation callable from any frontend.
`ActionResult<R>` is a sealed success/failure type instead of exceptions or
`null`. `Actor`/`ActorType` describe *who* is acting (player, console,
system, future: web) without `action` depending on Bukkit types. Detail:
[actions.md](actions.md).

## `gui` and `command` - the Frontends

`GuiPage` is the only place allowed to touch Bukkit inventory APIs.
`UniversalAdminCommand` is the root command (`/admin`, aliases `/ua`,
`/uadmin`) - currently a status command that renders
`UniversalAdmin.status()`, not a feature command. It's the one documented
exception to "a frontend only gets the one dependency it needs": a root
status command legitimately needs read access to the whole platform. Every
later `GuiPage`, by contrast, gets exactly the services/actions it needs
through the constructor, not through runtime access to a global object.
Detail: [gui.md](gui.md).

## `storage` - Persistence

`Repository<T, ID>` is the standard shape for data access, always async
(`CompletableFuture`). `Migration`/`MigrationRunner` version the schema.
`storage.jdbc.DataSourceFactory` is the only place that builds
`HikariConfig` and JDBC URLs. Detail: [storage.md](storage.md).

## `permission`, `audit`, `settings`, `config`, `localization`, `notification`, `scheduler`

One focused cross-cutting service each:

- `permission` - `PermissionNode`/`PermissionDefinition`/`PermissionRegistry`,
  testable independent of Paper; syncing to Bukkit's `PluginManager` only
  happens in bootstrap.
- `audit` - `AuditService`, JDBC implementation in `audit.jdbc`. Every
  action with a visible effect should write an entry here on success.
- `settings` - the typed settings system: `SettingKey`/`SettingType`/
  `SettingDefinition`/`SettingValidator`/`SettingRegistry`/`SettingsService`,
  `CoreSettings` (every core setting), `YamlSettingsService` as the Paper
  adapter, `ReloadConfigAction` for `/admin reload`. Fully replaces
  scattered `config.getString(...)` calls. Detail:
  [../development/settings.md](../development/settings.md).
- `config` - deliberately small: only `ConfigMigration`/`ConfigMigrationRunner`
  for `config.yml` versioning (`config-version`), analogous to
  `storage.Migration` for the database.
- `localization` - `MessageService`/`MessageKey` (Paper-independent,
  returns plain strings), `YamlLocaleMessageService` as the multi-language
  adapter over `lang/*.yml` with a fallback chain (active locale → `en_US`
  → a visible marker), `ComponentMessages` as a thin MiniMessage renderer
  for in-game output.
- `notification` - `NotificationService`, currently only in-game chat
  (`InGameNotificationService`); the interface is already cut so a
  Discord/web channel only has to implement it.
- `scheduler` - `TaskScheduler`/`PaperTaskScheduler`, see
  [threading.md](threading.md).

## `modules.*` - the Built-in Modules

See [modules.md](modules.md) for the full list and
[adding-module.md](../development/adding-module.md) for the guide to
building a new one. `modules.players` is the reference implementation.

## Why One Gradle Project Instead of Four

`universaladmin-api`, `-sdk`, `-web` would today only consist of empty
folders - there are no external extensions and no web app yet that would
compile against them. The separation point is instead marked in code (see
[extensions-future.md](extensions-future.md) and
[web-future.md](web-future.md)) and becomes a real module split once
something exists that needs it. Detail:
[decisions/0006-optional-web-architecture.md](decisions/0006-optional-web-architecture.md).
