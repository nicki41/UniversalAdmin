# Binding Development Rules

This file is the binding short version of the rules this repository is
developed by. It describes decisions that **have already been made** - don't
renegotiate them on every change, build on them instead. If a task
contradicts these rules, that needs to be named explicitly (see "Respecting
the Existing Architecture" at the end) before deviating from them.

Detailed rationale lives in [ARCHITECTURE.md](../../ARCHITECTURE.md), the
[ADRs](../architecture/decisions/), and the rest of the `docs/` tree. This
file is the short version to consult while working; it doesn't replace any
of those.

## Project Goal

UniversalAdmin is a universal admin platform for Paper servers - **not** a
collection of GUI commands. Long-term plan: core plugin, built-in modules, a
public extension API, community extensions, an optional web app, REST API,
WebSockets. Currently (see [ROADMAP.md](../../ROADMAP.md)) the core exists
with eight built-in modules and no public API and no web server yet.

Every architecture decision must be made so it doesn't force a rewrite when
that later expansion happens. See
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md).

## The One Architecture Rule That Explains Everything Else

```
Frontend (GUI / Command / later Web)
    ↓
Application Services
    ↓
Actions / Domain Logic
    ↓
Repositories / Server Adapters
    ↓
Paper / Database
```

Business logic lives **exclusively** in services and actions. Frontends (GUI
click handlers, commands, later web endpoints) call services/actions - they
contain no logic themselves. That's why the same "kick a player" logic can
later be used by a GUI button, an `/admin kick` command, and a REST endpoint
without duplicating code.

Concretely:

- **No business-logic code** in `dev.universaladmin.gui.*` click handlers or
  `dev.universaladmin.command.*` executors - only calls to services or
  `Action`s.
- **No SQL** outside `*Repository` implementations (typically in a `jdbc`
  subpackage). Services and actions only know the `Repository<T, ID>`
  interface, never `Connection`/`Statement`/`DataSource`.
- **No Bukkit event listeners with logic.** A listener translates a Bukkit
  event into a call to a service/action and nothing else.

Reference implementation for this pattern:
[`dev.universaladmin.modules.players`](../../src/main/java/dev/universaladmin/modules/players)
(model → repository → service → action → module). New modules follow this
template - see
[docs/development/adding-module.md](../development/adding-module.md).

## Action Execution and Authorization

- **Frontends never call `Action.execute(...)` directly.** The only way to
  run an action is `ActionExecutor.execute(ActionRequest)` (or the
  `(ActionId, ActionContext, I)` overload) - see
  [docs/architecture/actions.md](../architecture/actions.md). The executor
  handles permission/feature-enabled/self-target/input validation and the
  audit hook; a direct `Action.execute(...)` call bypasses all of that.
- **No scattered `player.hasPermission("...")` calls with a raw string
  literal** for anything that is an action. Permission nodes are declared at
  action-registration time via
  `ActionDefinition.Builder#permission(...)`; checks go through the
  `PermissionEvaluator` every `Actor` carries (`Actor.hasPermission(node)`),
  never directly against a Bukkit `Permissible`.
- **No module builds its own audit logging.** `ActionExecutor` produces the
  `AuditEvent` for every action automatically from
  `ActionDefinition`/`ActionContext`/`ActionResult`; a module supplies at
  most optional `AuditDetails` (reason, old/new value, metadata, ...) via
  `ActionDefinition.Builder#auditDetails(...)` - never its own call to
  `AuditService`/`AuditEventRepository` outside this hook. See
  [docs/user/audit-log.md](../user/audit-log.md).

## Package Rules

- Root package: `dev.universaladmin`.
- Architecture packages (`core`, `module`, `action`, `gui`, `command`,
  `permission`, `storage`, `audit`, `config`, `settings`, `localization`,
  `notification`, `scheduler`) hold the platform-wide abstractions. A module
  implements these interfaces, it doesn't extend them. `settings` is the
  typed settings system (`SettingKey`/`SettingDefinition`/`SettingRegistry`/
  `SettingsService`); `config` is deliberately kept smaller and is only
  responsible for `config.yml` versioning
  (`ConfigMigration`/`ConfigMigrationRunner`) - see
  [docs/development/settings.md](../development/settings.md).
- Built-in modules live under `dev.universaladmin.modules.<name>` (plural
  `modules`, so it's clear: this is a collection of modules, not a core
  class). Each module is self-contained; cross-module access goes through
  `ServiceRegistry`, never a direct import of another module's internal
  class.
- Concrete adapters (JDBC implementations, Bukkit-specific code) live in a
  `jdbc` or the respective adapter subpackage, not in the interface package
  itself. See e.g. `storage/` (interfaces) vs. `storage/jdbc/`
  (Hikari/JDBC).
- There is currently **one** Gradle project (`universaladmin-core`). A split
  into `universaladmin-api`/`-sdk`/`-web` is planned, but not now - rationale
  in
  [docs/architecture/decisions/0006-optional-web-architecture.md](../architecture/decisions/0006-optional-web-architecture.md).
  Don't build empty multi-module scaffolding for it ahead of time.

## Naming Conventions

- Typed IDs instead of raw strings: `ModuleId`, `ActionId`, `GuiPageId`,
  `AuditEventType` are `record`s wrapping `dev.universaladmin.core.id.Key`
  (`namespace:name`, e.g. `core:players`). `PermissionNode` and `MessageKey`
  are their own, simple dotted-string records (see their Javadoc for why
  they don't use `Key` - they follow external conventions like LuckPerms).
- Interfaces without a prefix/suffix (`Repository`, `Module`, `Action`),
  concrete implementations with a descriptive prefix
  (`JdbcPlayerProfileRepository`, `YamlSettingsService`,
  `InGameNotificationService`).
- Domain models are `record`s, not classes with setters. Where state
  changes, a new record is created instead (see
  `PlayerProfile.withLastSeen`).
- `Action` results are `ActionResult<R>` (sealed: `Success`/`Failure`), no
  `null` returns, no swallowed exceptions.

## Threading Rules

Full detail: [docs/architecture/threading.md](../architecture/threading.md).

- **Never make blocking DB calls on the Paper main thread.** Every
  `Repository` method runs through `TaskScheduler.supplyAsync`/`runAsync`
  (virtual threads, see `PaperTaskScheduler`).
- Anything touching the Bukkit API (inventories, entities, world) runs
  through `TaskScheduler.runOnMainThread`.
- The documented exception: `MigrationRunner.runPending()` at plugin start
  in `UniversalAdminPlugin#onEnable`, before players can join - deliberately
  runs **twice** (once before, once after `ModuleManager.enableAll()`, since
  each module only registers its own migration in its own `onEnable`; see
  [docs/architecture/threading.md](../architecture/threading.md)), never
  more often, and not a template for other code.
- **Never trigger or advise a global Bukkit reload** (`Bukkit.reload()` or
  similar) - that bypasses plugin lifecycle and server state in ways
  UniversalAdmin can't control. The only sanctioned reload is
  `/admin reload` (see
  [ReloadConfigAction](../../src/main/java/dev/universaladmin/settings/ReloadConfigAction.java))
  - which only reloads UniversalAdmin's own `config.yml`.

## Configuration & Localization

Full detail: [docs/development/settings.md](../development/settings.md).

- **No `config.getString(...)`/`getInt(...)`/... scattered through the
  code.** Every config value is a registered, typed `SettingDefinition<T>`
  (`SettingKey<T>`, `SettingType<T>`, default, validator,
  `requiresRestart` flag), read via `SettingsService.get(key)`. Core
  settings live in `dev.universaladmin.settings.CoreSettings`; a module
  registers its own settings under its own
  `ModuleDescriptor.settingsNamespace()`, never under `core`.
- **An invalid config value never crashes the server.** `YamlSettingsService`
  falls back to the default on a parse or validation error and logs a clear
  warning - true for the initial start as much as for `/admin reload`.
- **Restart-required settings never change live.** If a value whose
  `SettingDefinition.requiresRestart()` is `true` changes on reload, the old
  value stays active and the change is reported as "pending restart" - don't
  try to apply it live (e.g. database connection parameters).
- **No visible text in code.** Every user-facing string is a `MessageKey`,
  resolved via `MessageService.get(key, args...)` from `lang/<locale>.yml`.
  Fallback chain: active locale → `en_US` → a visible `[missing: ...]`
  marker (with a one-time debug log per key, no spam). Rendering as an
  Adventure `Component` (MiniMessage) only happens in the GUI/command layer
  (`ComponentMessages.render(...)`) - `MessageService` itself only returns
  the resolved string, so the same resolution can later be reused by a web
  view.
- **Never change `config-version` by hand.** Schema changes to
  `config.yml` get a new `ConfigMigration` (`dev.universaladmin.config`),
  analogous to `storage.Migration` for the database - an existing user's
  config is never silently overwritten.

## Module Lifecycle

Full detail: [docs/architecture/modules.md](../architecture/modules.md).

- Every module goes through `DISCOVERED → LOADED → ENABLED` (via
  `ModuleManager.loadAll()`/`enableAll()`) and back to `DISABLED` (via
  `disableAll()`). `ModuleRegistry` only stores state; `ModuleManager` is
  the only place that triggers transitions.
- **A module is never critical, core bootstrap components always are.** If
  `Module#onLoad`/`onEnable` throws, **only that module** is marked
  `FAILED` (with a fully logged stack trace) - everything else starts up
  normally. If something throws during the critical bootstrap phase in
  `UniversalAdminPlugin#bootstrapCore` (config, scheduler, storage +
  migrations, the shared registries), the entire plugin start aborts and
  the plugin disables itself - there is no module-level isolation for that.
- Module dependencies are declared via `ModuleDescriptor.dependencies()`,
  not via direct imports. `ModuleManager` topologically sorts modules so
  dependencies load/enable first. A dependency cycle is a programming error
  in the declared graph, not an isolatable runtime error -
  `loadAll()` throws in that case instead of continuing.
- Resources a module registers in `onEnable` (listeners, scheduler tasks,
  registry entries that should disappear again on disable) belong through
  `context.resources().listener(...)`/`task(...)`/`closeable(...)` - not
  manually rebuilt in `onDisable`. They're released automatically, even if
  `onEnable` itself throws or `onDisable` throws an exception.
- Never swallow errors silently: every `FAILED` transition is logged with
  the module id and the full stack trace (`Level.SEVERE`), never just
  recorded as a state.

## Security

- **Never log secrets** (DB passwords, tokens, future API keys) - not even
  at `FINE`/debug level.
- **No unsafe packet hacks in the core** (no ProtocolLib, no raw packet
  injection). If low-level network access is ever needed for an extension,
  that's solved explicitly outside the core, not retrofitted into it.
- Business-logic errors are carried via `ActionResult.Failure` with a
  `FailureReason`, not via swallowed exceptions or a generic
  `RuntimeException` without context.

## Dependencies

- **No new dependencies without a clear reason**, traceable in a comment or
  a commit/PR. Currently deliberately **no** mandatory dependency on Vault,
  LuckPerms, PlaceholderAPI, or ProtocolLib in the core.
- Database drivers (`sqlite-jdbc`, `mariadb-java-client`) and `HikariCP` are
  the only runtime libraries; they're bundled via the Shadow plugin so they
  don't collide with other plugins on the same server. `mariadb-java-client`
  and `HikariCP` are additionally relocated under `dev.universaladmin.libs.*`
  (see `build.gradle.kts`). `sqlite-jdbc` is deliberately **not** relocated
  - it bundles a native (JNI) library whose compiled binary hardcodes the
  class name `org/sqlite/core/NativeDB` for native method linking;
  relocating that Java class breaks the JNI linking on the first real
  connection attempt (`ClassNotFoundException: org/sqlite/core/NativeDB`),
  even though the build and every other check stay green. It still ships
  bundled (shaded), just under its original package name - a collision risk
  with a different plugin's own sqlite-jdbc copy on the same server is the
  accepted trade-off, same as for any other Bukkit plugin bundling this
  driver.

## Built-in Modules Stay Extension-Friendly

Built-in modules use exactly the same abstractions
(`Module`/`Action`/`GuiPage`/`Repository`/`Migration`/`PermissionRegistry`)
that will later be open to external extensions too. Don't build "shortcut"
behavior for built-ins that an extension couldn't also take - see
[docs/architecture/extensions-future.md](../architecture/extensions-future.md).
There's no public `api`/`sdk` module boundary yet; this rule is the
substitute until there is one.

## Tests

- Every new piece of business logic (service, action, migration with
  non-trivial logic) needs a unit test that runs **without** a live Paper
  server. Repositories are tested against an in-memory fake of the
  respective interface, not mocked, wherever a fake is simpler (see
  `PlayerServiceTest`).
- Migrations are tested against a real (temporary) SQLite database, not
  mocks (see `MigrationRunnerTest`).
- Details and conventions: [docs/development/testing.md](../development/testing.md).

## Keeping Documentation Current

Code changes that touch an architecture decision, module behavior, or a
configuration option update the corresponding file in `docs/` or this file
in the same change - not "later". A new, deliberate architecture decision
gets a new ADR file under `docs/architecture/decisions/`, not just a
discussion in a commit message.

## Respecting the Existing Architecture

This structure was deliberately designed (see the ADRs). A task that wants a
new feature builds **on top of** this architecture - it doesn't rework it at
every opportunity. If the architecture demonstrably doesn't fit a specific
case, that gets named as its own proposal (a new ADR), not silently changed
along with a feature PR.

## Build & Test

```bash
./gradlew build   # compiles, tests, builds the shaded jar (build/libs/universaladmin-core-*.jar)
./gradlew test    # tests only
```

The Java toolchain is 25 (auto-provisioned via `foojay-resolver-convention`
if not present locally). Target server: current stable Paper API (see
`build.gradle.kts` for the exact version).
