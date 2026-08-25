# Roadmap

A rough build-out plan, not a dated sprint plan. Order within a phase is a
recommendation, not a hard requirement. Every phase builds on the structure
laid out in [ARCHITECTURE.md](ARCHITECTURE.md) - not around it.

## Phase 0 - Architecture & Scaffolding

- [x] Package structure, core abstractions (`Module`, `Action`, `GuiPage`,
      `Repository`, `Migration`, registries)
- [x] Gradle setup (Java 25 toolchain, Shadow plugin, SQLite/MySQL drivers)
- [x] Plugin bootstrap that compiles and starts
- [x] Eight module skeletons, `Players` as the complete reference implementation
- [x] Test infrastructure (JUnit 5, in-memory fakes, real SQLite migration tests)
- [x] Baseline documentation (this document, ARCHITECTURE.md,
      docs/development/architecture-rules.md, ADRs)
- [x] Core bootstrap lifecycle (`onLoad`/`onEnable`/`onDisable`, critical
      vs. isolated failure handling) and the internal module system
      (`ModuleRegistry`/`ModuleManager`, lifecycle states, dependency
      resolution, `ModuleResources` cleanup) - see
      [docs/architecture/modules.md](docs/architecture/modules.md)
- [x] `/admin` status command (aliases `/ua`, `/uadmin`), `PluginStatus` snapshot
- [x] Central action authorization (`ActionDefinition`/`ActionExecutor`):
      permission/feature-enabled/self-target/input validation before every
      action, `PermissionEvaluator` as the central permission resolver
      (`Actor`-carried instead of scattered `hasPermission(...)`),
      `ActionEvent`s (`Executing`/`Executed`/`Failed`), the undo contract
      (`ReversibleAction`), the audit hook - see
      [docs/architecture/actions.md](docs/architecture/actions.md)
- [x] Typed settings system (`SettingKey`/`SettingDefinition`/
      `SettingRegistry`/`SettingsService`, namespacing for core/module/
      future extensions, validation with a fallback instead of a crash), a
      full `config.yml` (general/database/gui/audit/modules/performance/
      maintenance/web), config versioning (`config-version` +
      `ConfigMigrationRunner`), a safe `/admin reload` - see
      [docs/development/settings.md](docs/development/settings.md)
- [x] Multi-language message system (`en_US`/`de_DE`, a fallback chain,
      parameter substitution, MiniMessage rendering for in-game output) -
      see [docs/user/configuration.md](docs/user/configuration.md#localization)

- [x] Reusable in-game GUI framework (`AbstractGuiPage`/
      `AbstractListGuiPage`, sessions, pagination, permission-driven
      visibility, async loading, confirmation/selection dialogs, text
      input via the Paper dialog API) plus a main-menu skeleton with a
      placeholder page per built-in module - see
      [docs/development/gui-framework.md](docs/development/gui-framework.md)
- [x] Central audit system: a full `AuditEvent` (actor/action/module/
      target/source/success/reason/old-new value/world position/metadata/
      correlation id), automatically populated by `ActionExecutor` instead
      of feature-specific logging; `AuditSchemaMigrationV2` + a filtered,
      paginated `AuditService#query`; a working audit log GUI (a list with
      a success/failure filter, a detail page); hourly, configurable
      retention (`audit.retention-days`) - see
      [docs/user/audit-log.md](docs/user/audit-log.md). That already
      covers the "audit log GUI/commands" item from Phase 2 below for the
      GUI side; commands follow later if needed.

## Phase 1 - Making Players & Moderation Usable

- [x] A `PlayerJoinEvent`/`PlayerQuitEvent` listener that only calls
      `PlayerService.getOrCreateProfile` (no logic leaking into the
      listener) - see `PlayerActivityListener`
- [x] A punishment repository + service for Moderation
      (kick/ban/tempban/ipban/mute/tempmute/warn/unban/unmute/removewarn),
      including a migration, join/chat enforcement, and a GUI wizard, see
      [docs/user/modules/moderation.md](docs/user/modules/moderation.md)
- [x] The first real GUI page (a player list) replacing the
      `core:players.home` placeholder page - built out into a full player
      browser/profile/actions/inventory editor, see
      [docs/user/modules/players.md](docs/user/modules/players.md)
- [ ] The first real subcommands under `/admin players`, `/admin moderation`
      (the Players and Moderation actions from this release are already
      command-ready, since they run through `ActionExecutor` - only the
      command frontend itself is still missing)
- [x] `/admin server broadcast|shutdown|restart|cancel` (a console path to
      the server permissions, since the console has no GUI - see
      [docs/user/modules/server.md](docs/user/modules/server.md))
- [x] Audit log entries for every moderation action via `AuditService`
      (like Players: every mutating action is automatically audited
      through `ActionExecutor`)

## Phase 2 - Remaining Built-in Modules

- [x] Server (dashboard, broadcasts, maintenance mode, shutdown/restart
      with confirmation/countdown - see
      [docs/user/modules/server.md](docs/user/modules/server.md); TPS/MSPT
      remains the Performance module's job further below)
- [x] Worlds (browser/profile, spawn/time/weather/difficulty, border, a
      dynamic gamerule GUI - see
      [docs/user/modules/worlds.md](docs/user/modules/worlds.md)). Loading/
      unloading a world was not part of the current scope and is still
      open; delete/clone/reset deliberately stay out of the core, see its
      "Dangerous Features" section.
- [x] Whitelist (native whitelist wrapping plus its own table with
      reason/notes/expiration, an ownership model, a join check + an
      hourly sweep - see
      [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md))
- [x] Performance (cached TPS/MSPT/memory/world/entity sampling,
      dashboard, per-world performance, an entity overview by type/world,
      a short in-memory history, staff alerts on thresholds, a narrowly
      scoped Entity Clear with preview/confirmation/audit - see
      [docs/user/modules/performance.md](docs/user/modules/performance.md))
- [ ] A settings GUI/commands over `SettingsService` (see
      [docs/development/settings.md](docs/development/settings.md) - the
      typed system already exists, only the GUI/command surface for it is
      still missing)
- [x] An audit log GUI via `AuditService` (see Phase 0) - `/admin audit`
      commands are still open

## Phase 2.5 - Public Release

- [x] Public GitHub repository, Apache-2.0 license (`LICENSE`), see
      [docs/release/licensing.md](docs/release/licensing.md)
- [x] CI: build and tests on every push/PR against `main`
- [x] Automated releases: a `v*` tag produces a build, tests, a GitHub
      release, the jar, and its SHA-256 - see
      [docs/release/releasing.md](docs/release/releasing.md)
- [x] Anonymous usage statistics with full documentation and an opt-out
      (see [docs/user/telemetry.md](docs/user/telemetry.md)); there is no
      official endpoint yet, so nothing is sent by default
- [ ] An official telemetry endpoint (backend), including a privacy policy
      and a retention decision
- [ ] Screenshots and a first Modrinth upload (checklist in
      [docs/release/modrinth.md](docs/release/modrinth.md))
- [ ] The first tagged alpha release

## Phase 3 - Proxy Support

- [ ] A BungeeCord/Velocity messaging channel for cross-server actions
      (e.g. a global kick, shared whitelist status)
- [ ] Clarify what's configured proxy-wide vs. per server

## Phase 4 - Public Extension API (Next Milestone)

- [ ] Extract a `universaladmin-api` Gradle module (see
      [docs/architecture/decisions/0006-optional-web-architecture.md](docs/architecture/decisions/0006-optional-web-architecture.md))
- [ ] A stable, versioned interface for everything listed in
      [docs/architecture/extensions-future.md](docs/architecture/extensions-future.md)
      (modules, GUI pages, actions, permissions, migrations, ...)
- [ ] An extension loader (own jars in `plugins/UniversalAdmin/extensions/`
      or standalone Bukkit plugins with `depend: [UniversalAdmin]` -
      decision open, see extensions-future.md)
- [ ] `universaladmin-sdk` with an example extension and documentation

## Phase 5 - Community/Official Extensions and Marketplace

- [ ] First official extensions as a proof of concept for the API (e.g. a
      Vault integration, a Discord integration - see the project brief for
      the full list of possible extensions)
- [ ] An extension registry/directory (format open: a simple list vs. a
      dedicated service)

## Phase 6 - Optional Web App

- [ ] A `universaladmin-web` module (see
      [docs/architecture/web-future.md](docs/architecture/web-future.md))
- [ ] A REST API over the same services/actions as the GUI and commands
- [ ] WebSockets/live updates for dashboard widgets
- [ ] Web-side authentication (separate from the Minecraft account
      system, details open)

## Deliberately Deferred

These items aren't forgotten, they're deliberately not part of the current
phases because they can only be meaningfully decided once there's real
usage pressure:

- The concrete extension distribution format (a marketplace? a plain
  GitHub listing?)
- The web app framework choice
- Whether/how Folia support becomes necessary
