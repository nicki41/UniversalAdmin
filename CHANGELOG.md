# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/). Unreleased
changes accumulate under `[Unreleased]`; each release moves them into a
dated, versioned section below it (see
[docs/release/releasing.md](docs/release/releasing.md)).

## [Unreleased]

## [0.1.0-alpha.1] - 2026-08-26

### Added

- **Anonymous usage statistics** (`dev.universaladmin.telemetry`):
  `InstallationIdentity`/`InstallationIdentityStore` (128 bits from
  `SecureRandom`, derived from nothing, persisted in
  `installation-id.yml`), `TelemetryPayload` (exactly six fields),
  `TelemetryClient` with `HttpTelemetryClient` (JDK HTTP client, short
  timeouts, no redirects, response discarded) and `NoOpTelemetryClient`,
  `TelemetryService` (enabled check on every heartbeat, player counts read
  on the main thread, the request runs in the background, failures are
  swallowed and only warned once per run), `TelemetryScheduler` (a
  delayed start, an interval with jitter, its own daemon thread), and
  `TelemetryBootstrap` (wiring with three outcomes: off / no endpoint /
  active). New settings `telemetry.enabled` (switchable live),
  `telemetry.endpoint` (empty by default - **nothing** is sent by
  default), and `telemetry.interval`. Full documentation including
  everything that's never collected:
  [docs/user/telemetry.md](docs/user/telemetry.md).
- Tests for telemetry with zero real network requests: id
  generation/persistence, "disabled means zero requests", a payload
  without player identities, serialization, an endpoint failure with no
  side effect, jitter bounds, scheduler cleanup.
- `LICENSE` (Apache-2.0, unmodified official license text) and a
  licensing decision with rationale in
  [docs/release/licensing.md](docs/release/licensing.md).
- `docs/development/architecture-rules.md` - the binding architecture,
  package, threading, settings, module, security, dependency, and test
  rules as regular developer documentation.
- `docs/release/releasing.md` - a reproducible release process
  (version → CHANGELOG → build → commit → tag → push).
- `.github/workflows/release.yml` - a `v*` tag automatically produces a
  GitHub release: a tag-vs-project-version check, a build including
  tests, the shaded jar plus a SHA-256 file as assets, prerelease
  detection for `-alpha`/`-beta`/`-rc`. `contents: write`, no additional
  secrets. Also callable directly (`workflow_call`, with a `tag` input) by
  `auto-release.yml` below, not only by its own tag-push trigger.
- `.github/workflows/auto-release.yml` - releases automatically after any
  push to `main` that changes more than docs/CI metadata: bumps the
  `-alpha.N` counter in `build.gradle.kts`, moves the `CHANGELOG.md`
  `[Unreleased]` section into a dated one, commits, tags, and calls
  `release.yml`. Skips a beta/rc/stable version bump (a bigger decision
  than this workflow makes on its own) and its own release commits (no
  loop). See [docs/release/releasing.md](docs/release/releasing.md).
- A `printVersion` Gradle task as the single source of the project
  version for CI (the tag check, artifact naming).
- Issue forms (`bug_report.yml`, `feature_request.yml`) and
  `ISSUE_TEMPLATE/config.yml` pointing at private vulnerability reporting
  instead of public security issues.

- Project architecture defined and documented (see ARCHITECTURE.md,
  docs/architecture/, ADRs under docs/architecture/decisions/).
- Gradle setup (Java 25 toolchain, Shadow plugin, SQLite/MySQL drivers,
  JUnit 5).
- Plugin bootstrap (`UniversalAdminPlugin`) with manual dependency wiring.
- Core abstractions: `Module`, `Action`/`ActionResult`, `GuiPage`,
  `Repository`/`Migration`, `PermissionRegistry`, `ServiceRegistry`,
  `AuditService`, `MessageService`, `NotificationService`, `TaskScheduler`.
- Eight module skeletons: Players, Moderation, Server, Worlds, Whitelist,
  Performance, Audit Log, Settings. `Players` as the complete reference
  implementation (repository → service → action, including a migration).
- Test infrastructure with example tests (registry, permission
  validation, a service with an in-memory fake, migrations against a
  real SQLite database).

- Core bootstrap lifecycle (`onLoad`/`onEnable`/`onDisable`) with a clear
  separation between critical core components (config, scheduler,
  storage/migrations, shared registries - failures abort the whole
  plugin start) and individual modules (failures are isolated, see
  `ModuleManager`).
- Internal module lifecycle system: `ModuleDescriptor` (metadata
  including dependencies, settings/permission namespace, an optional GUI
  icon placeholder), `ModuleState`
  (`DISCOVERED`/`LOADED`/`ENABLED`/`DISABLED`/`FAILED`), `ModuleRegistry`
  (state tracking), `ModuleManager` (dependency ordering, failure
  isolation), `ModuleResources` (automatic release of
  listeners/tasks/registry entries on disable). See
  docs/architecture/modules.md.
- `PluginStatus`/`ComponentStatus` - a live snapshot of version, uptime,
  active/failed modules, coarse database/web status.
- `/admin` command (aliases `/ua`, `/uadmin`) as a status placeholder,
  rendering `PluginStatus`.
- `unregister(...)` on `ActionRegistry`/`GuiRegistry`/`PermissionRegistry`
  for module-disable cleanup.
- Tests for the module lifecycle system (`ModuleManagerTest`):
  registration, a duplicate module id, lifecycle ordering, failure
  isolation, disable cleanup, dependency ordering, cycle detection, a
  missing dependency.

- Typed settings system (`dev.universaladmin.settings`):
  `SettingKey<T>`/`SettingType<T>`/`SettingValidator<T>`/`SettingDefinition<T>`/
  `SettingValue<T>`/`SettingRegistry`/`SettingsService`, with built-in
  types for string/boolean/int/long/double/Duration/enum/string list,
  validators for min/max/regex/multiple-of, and namespacing between core
  settings and future module/extension settings. See
  docs/development/settings.md and ADR 0007.
- `CoreSettings` - registers the complete `config.yml` tree (`general`,
  `database`, `gui`, `audit`, `modules`, `performance`, `maintenance`,
  `web`); `modules.*` now actually controls whether a built-in module
  gets registered at all.
- `config.yml` versioning: `config-version`, `ConfigMigration`/
  `ConfigMigrationRunner` (`dev.universaladmin.config`) - an existing
  user config gets migrated on future schema changes instead of
  overwritten.
- A safe `/admin reload` (`ReloadConfigAction`, permission
  `universaladmin.reload`) - only ever reloads UniversalAdmin's own
  `config.yml`, never Bukkit's global `/reload`. Restart-required
  settings are reported as "pending restart" on change instead of
  applied live.
- Multi-language message system: `lang/en_US.yml` (default/fallback) and
  `lang/de_DE.yml`, `YamlLocaleMessageService` with a fallback chain
  (active locale → `en_US` → a visible `[missing: ...]` marker with a
  one-time debug log), `ComponentMessages` as a MiniMessage renderer for
  in-game output, kept separate from plain string resolution (for web
  reuse).
- Tests for the settings/localization system: defaults, invalid values
  (parse/validation errors → fallback instead of a crash), enum parsing,
  duration parsing, duplicate setting keys, locale fallback, parameter
  interpolation.
- `DatabaseHealth` (`DISCONNECTED`/`CONNECTING`/`READY`/`FAILED`),
  tracked by `StorageService` and mapped onto the existing
  `ComponentStatus` in `UniversalAdmin#status()` - see
  docs/architecture/storage.md#health.
- SQLite pragmas in `DataSourceFactory` (`journal_mode=WAL`,
  `synchronous=NORMAL`, `foreign_keys=on`, `busy_timeout=5000`).
- `Transactions` (`dev.universaladmin.storage`) - a helper for repository
  methods that need to run multiple statements atomically (commit/
  rollback) over one connection.
- `StorageException` - a generic unchecked wrapper for `SQLException` in
  the storage foundation itself (`StorageService`, `Transactions`).
- `docs/user/database.md` - user documentation for SQLite vs.
  MySQL/MariaDB (when which, creating a MySQL user, behavior on startup
  failure, backups).
- Tests: `StorageServiceTest` (SQLite init, health states, failure on an
  invalid path), `TransactionsTest` (commit, rollback on error),
  `JdbcPlayerProfileRepositoryTest` (the repository foundation end to
  end against a real SQLite database), database-type parsing in
  `SettingTypesTest`, an explicit "no duplicate migration run" assertion
  in `MigrationRunnerTest`.
- A reusable in-game GUI framework (`dev.universaladmin.gui`):
  `AbstractGuiPage`/`AbstractListGuiPage` (navigation, permission-driven
  visibility, pagination, async loading with loading/empty/error state),
  `GuiSession`/`GuiSessionManager` (per-player state with no held
  `Player` references, cleanup on disconnect/a real inventory close),
  `GuiListener` as the single, centrally registered click/drag handler,
  `GuiLayout`/`Pagination` as pure, tested slot/pagination logic,
  `IconProvider`/`MaterialIconProvider` as the central material
  resolution, `ConfirmationDialog` (danger level NORMAL/WARNING/
  DANGEROUS), `SelectionDialog`, `GuiTextInput` (a free-text/search flow
  via the Paper dialog API instead of packet hacks/anvil/sign) - see
  docs/development/gui-framework.md.
- `MainMenuPage`: the `/admin` main menu with one button per built-in
  module, filtered by actual `ModuleState.ENABLED` and permission;
  `PlaceholderGuiPage` as the "not built yet" target page per module. A
  new permission `universaladmin.menu.open`.
- `ModuleDescriptor.icon()` set for all eight built-in modules
  (previously `null` everywhere, see `GuiIcon`).
- Tests for the GUI framework: `PaginationTest`, `GuiLayoutTest`,
  `GuiSessionTest`, `GuiSessionManagerTest`, `GuiButtonVisibilityTest`,
  `GuiClickContextTest` (the navigation stack), `GuiListenerTest`
  (click cancel/dispatch, close/quit cleanup).
- `GuiView#onChange` - a live-sync hook `GuiListener` runs one tick after a
  click/drag it let through, on top of the existing `onClose`. Used by the
  Players module's inventory/ender chest editors and the new Staff-Mode
  Ender Chest Inspector tool so an edit mirrors onto the real target
  immediately instead of only when the viewer closes the GUI.
- Staff-Mode **Ender Chest Inspector** tool (`EnderChestInspectorPage`),
  alongside a now-live (not read-only) Inventory Inspector - both gated on
  the Players module's own `...inventory.edit`/`...enderchest.edit`
  permissions. See [docs/user/modules/staff-tools.md](docs/user/modules/staff-tools.md).
- Staff-Mode **Teleport Picker** tool: opens a player list, then a
  "teleport to them"/"bring them" choice, routed through the Players
  module's own audited `TeleportPlayerAction` - replaces Random Teleport.
- Staff-Mode target tracking now searches by look direction within a
  configurable range (`moderation.staffmode.target-range-blocks`, default
  40) instead of a 6-block, wall-blocked ray trace, and sends a persistent
  actionbar naming the current target. The Player Inspector tool's head
  always shows the current target now, regardless of which tool is held.
- Per-action success messages in the Moderation GUI (`ModerationGuiActions`)
  - naming the target, reason, and duration where relevant - instead of a
  single generic "Done." for every action.
- Audit log GUI filtering by actor, module, and time range
  (`AuditLogFilterPage`), on top of the existing success/failure toggle -
  the query layer already supported all of this, only the GUI was missing.
  See [docs/user/audit-log.md](docs/user/audit-log.md).

### Changed

- The binding development rules now live under
  `docs/development/architecture-rules.md`; every reference in source
  code and documentation points there.
- `README.md` brought to a public-facing state (status, features,
  requirements, quick start, database, permissions, anonymous
  statistics, extensions/web app explicitly marked as planned, license).
- `CONTRIBUTING.md` and `SECURITY.md` reworked: setup, architecture,
  tests, style, PR flow and private vulnerability reporting respectively,
  a telemetry/privacy section, and supported versions.
- `.github/workflows/build.yml` runs on `push`/`pull_request` against
  `main`, sets up JDK 25 (build toolchain) and 21 (runs Gradle), uploads
  the jar under a versioned artifact name.
- The PR template gained checklist items for secrets, localization,
  threading, and telemetry documentation.
- `.gitignore` gained build/IDE/runtime/database/secret patterns as well
  as purely local notes files.
- Target Paper version set to 26.2 (`paper-api:26.2.build.115-stable`).
  Paper 26.2 is only published for JVM 25+, so the Java toolchain was
  raised from the originally planned version 21 to 25 - see
  `build.gradle.kts` and `plugin.yml` (`api-version: '26.2'`).
- `Module` is now two-phase (`onLoad` then `onEnable`) instead of just
  `onEnable`; `id()`/`displayName()` are replaced by `descriptor()`
  (`ModuleDescriptor`). All eight built-in modules updated.
- `UniversalAdminCommand` now shows a `PluginStatus` report instead of
  just the list of loaded modules, and is registered under `/admin`
  instead of `/ua`.
- `ConfigService`/`YamlConfigService` removed, replaced by
  `SettingsService`/`YamlSettingsService` (see above).
  `UniversalAdmin.config()` is now `UniversalAdmin.settings()`
  (`SettingsService`) plus `UniversalAdmin.settingRegistry()`
  (`SettingRegistry`).
- `messages_en_US.yml` (a file at the plugin root) replaced by
  `lang/en_US.yml`/`lang/de_DE.yml`; `YamlMessageService` (one fixed
  locale) replaced by `YamlLocaleMessageService` (multiple locales, a
  fallback chain, reads the active locale live from `general.language`).
- `/admin` (no argument) now opens `MainMenuPage` for a player instead
  of the text status report; for console/command blocks, the status
  report is unchanged. `/admin reload` unchanged. `UniversalAdmin`'s
  constructor gained a new required field, `GuiFramework`
  (`UniversalAdmin#guiFramework()`).

- **Players** complete: a player browser GUI (online/offline/last-seen/
  search), a profile page, ~20 actions (teleport, heal, effects,
  gamemode, inventory/ender chest editor), fine-grained permissions,
  audit. See [docs/user/modules/players.md](docs/user/modules/players.md).
- **Moderation** complete: a punishment repository/service (kick/ban/
  tempban/ipban/mute/tempmute/warn/freeze/unban/unmute/removewarn/
  unfreeze), join/chat enforcement, a GUI wizard, fine-grained
  permissions, audit, plus vanish/godmode/no-collision/staff mode
  (crash-safe snapshot/recovery, `/admin staff recover`). See
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md) and
  [docs/user/modules/staff-tools.md](docs/user/modules/staff-tools.md).
- **Server** complete: a live dashboard (version/uptime/players/memory/
  CPU/DB status/modules), broadcast (message/title/actionbar), its own
  maintenance-mode system (repository/service, join enforcement,
  allow-list), shutdown/restart with dangerous-action confirmation and a
  configurable countdown + cancel, `/admin server ...`, fine-grained
  permissions, audit. See [docs/user/modules/server.md](docs/user/modules/server.md).
- **Worlds** complete: a world browser/profile (environment/seed/spawn/
  border/players/chunks/entities/time/weather/difficulty),
  teleport/spawn/time/weather/difficulty actions, world border
  management, a dynamic gamerule GUI (reads `World#getGameRules()` at
  runtime), fine-grained permissions (seed kept separate), audit. See
  [docs/user/modules/worlds.md](docs/user/modules/worlds.md).
- **Whitelist** complete: native whitelist wrapping (enable/disable/
  list/add/remove) plus its own metadata (added-by/at, reason, notes,
  expiration), time-limited entries with a join check and an hourly
  sweep, a strict ownership model, fine-grained permissions, audit. See
  [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md).
- **Audit Log** complete (GUI side): a full `AuditEvent` (actor/action/
  module/target/source/success/reason/old-new value/world position/
  metadata/correlation id), automatically populated by `ActionExecutor`,
  a filtered, paginated `AuditService#query`, a working GUI (a list with
  a success/failure filter, a detail page), hourly configurable
  retention. `/admin audit` commands still open. See
  [docs/user/audit-log.md](docs/user/audit-log.md).
- **Performance** complete: cached TPS/MSPT/memory/world/entity sampling
  on a configurable interval (never recomputed per GUI render), a
  dashboard, a per-world performance view, an entity overview (grouped
  by type/world), a short in-memory history, staff alerts on TPS/MSPT/
  memory thresholds, a narrowly scoped Entity Clear (never players,
  configurable protected types, preview, confirmation, audit),
  fine-grained permissions. See
  [docs/user/modules/performance.md](docs/user/modules/performance.md).

### Fixed

- **Critical: every module-owned table was never created**
  (`player_profiles`, `punishments`, `server_maintenance_state`,
  `whitelist_entries`, `vanish_state`, ...) - `UniversalAdminPlugin`
  only called `storage.migrations().runPending()` once, before any
  module was even enabled. But every module only registers its own
  migration(s) *during* its own `onEnable` - without a second
  `runPending()` call afterward, those migrations never ran. Affected
  every module except Audit Log (whose two migrations were registered
  before that one, single call) - among other things, the cause of
  "storage error" in the Moderation/punishment module, "unexpected
  error" in maintenance mode, and broken whitelist/vanish state on join.
  See [docs/architecture/threading.md](docs/architecture/threading.md).
- `worlds.gui.action` existed twice as a sibling key in
  `lang/en_US.yml`/`de_DE.yml` (once for success/error messages, once
  for button labels) - YAML doesn't really allow that, Bukkit's loader
  silently takes the last entry (a "duplicate keys found" warning at
  startup). Button labels renamed to `worlds.gui.buttons`.
- Player lists (Players, Whitelist, audit log detail) showed the generic
  Steve head everywhere instead of the real player skin - `GuiItem`
  gained a new `playerHead(OfflinePlayer, ...)` factory method
  (`SkullMeta#setOwningPlayer`), now used by every player-representing
  tile.
- **Critical: the plugin didn't start at all on a real Paper server**
  (`HikariConfig: Failed to load driver class dev.universaladmin.libs.sqlite.JDBC`).
  Two interacting Shadow-plugin issues, neither detectable by the test
  suite (which runs against unshaded, unminimized dependencies) - only
  surfaced by an actual server start:
  - `shadowJar { minimize() }` stripped both JDBC drivers from the jar
    entirely, because they're only loaded reflectively via
    `HikariConfig#setDriverClassName` - invisible to `minimize()`'s
    static-reachability analysis. Both drivers are now explicitly
    excluded from minimization.
  - `org.sqlite` was being relocated like any other dependency - which
    breaks the JNI linking of the bundled native SQLite library
    (`ClassNotFoundException: org/sqlite/core/NativeDB`), since its
    compiled binary hardcodes the original class name. `org.sqlite` is
    now deliberately no longer relocated (but still ships bundled) - see
    [docs/development/architecture-rules.md](docs/development/architecture-rules.md)
    and `build.gradle.kts`.
  - A new Gradle task, `verifyShadedJarDrivers` (runs as part of
    `check`/`build`): opens a real SQLite connection exclusively through
    the finished, built jar (no other classpath entry), so this exact
    class of failure can never come back unnoticed again. See
    `ShadedJarDriverSmokeTestMain`.
- `PlayerProfileIndexMigration`/`ModerationPunishmentIndexMigration`
  used `CREATE INDEX IF NOT EXISTS`, which real MySQL (unlike
  SQLite/MariaDB) doesn't support - removed, since `MigrationRunner`
  already guarantees every migration runs at most once. See
  [docs/architecture/storage.md#dialect-differences](docs/architecture/storage.md#dialect-differences).
- Freezing a player sent the target two notices for one click of the
  Freeze Tool - `PlayerInteractEntityEvent` fires once per hand for a
  single physical right-click, and `StaffModeGuardListener` dispatched on
  both. Now only the main-hand firing is handled.
- `core:performance.clear-entities` failed to record its audit event on
  every run (`IllegalArgumentException: Unsupported audit metadata value
  type ... ListN`) - its metadata put the selected entity types in
  directly as a `List`, which the audit metadata codec only ever accepted
  String/Number/Boolean/null for. Now joined into a single string, same as
  the value already shown in the audit summary.
