# UniversalAdmin

[![Build](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml/badge.svg)](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A universal admin platform for Paper servers - full in-game GUIs, no
mandatory dependencies, built on an architecture designed for extensibility.

> **Status: Alpha (`0.1.0-alpha`).** Six of the eight built-in modules
> (Players, Moderation, Server, Worlds, Whitelist, Performance) are fully
> usable - own GUI, actions, permissions, audit entries. The Audit Log module
> has its service and GUI; Settings so far only has its core service. There is
> **no** public extension API yet and no web app - both are planned, see
> [Roadmap](#roadmap). Nothing has been published on Modrinth yet.

## Overview

Most admin plugins grow into a pile of independent GUI listeners and
commands, each bringing its own logic, its own permission handling, and often
its own bit of SQL. That's neither testable nor reusable later from a web app
or an extension API.

UniversalAdmin separates business logic from the interface that invokes it:

```
Frontend (GUI / Commands / later Web)
    ↓
Application Services
    ↓
Actions / Domain Logic
    ↓
Repositories / Server Adapters
    ↓
Paper / Database
```

Every mutating operation - kicking a player, changing a gamerule, clearing a
filtered set of entities - runs through the same permission-checked, audited
pipeline, whether it's triggered from the GUI, a command, or later a REST
API. No feature reimplements its own permission handling, its own SQL, or its
own audit logging. Details: [ARCHITECTURE.md](ARCHITECTURE.md).

## Features

Everything listed here is implemented and usable - planned work is under
[Roadmap](#roadmap).

- **Players** - browser (online/offline/last-seen/search), profile page, ~20
  actions (teleport, heal, effects, gamemode, inventory/ender chest editor).
- **Moderation** - kick, ban, tempban, IP ban, mute, tempmute, warn, unban,
  unmute, remove-warn; join and chat enforcement, GUI wizard.
- **Staff Tools** - vanish, freeze, godmode, no-collision, and a staff mode
  with crash-safe snapshot/recovery (`/admin staff recover`).
- **Server** - live dashboard, broadcast (chat/title/actionbar), maintenance
  mode with an allow-list, shutdown/restart with confirmation and a
  configurable countdown.
- **Worlds** - world browser and profile, teleport/spawn/time/weather/
  difficulty, world border management, a dynamic gamerule GUI (reads
  gamerules at runtime, no hardcoded list).
- **Whitelist** - native whitelist wrapping plus its own metadata (reason,
  notes, expiration), ownership model, join check with an hourly sweep.
- **Performance** - TPS/MSPT/memory dashboard, per-world performance, entity
  overview by type and world, short in-memory history, staff alerts on
  configurable thresholds, and a deliberately narrow Entity Clear (never
  players, protected types, preview, confirmation, audited) - explicitly
  **not** an aggressive lag cleaner.
- **Audit Log** - every action is audited automatically; filterable,
  paginated in-game view with a detail page and configurable retention.
- **Settings** - typed, validated settings system for the whole platform; an
  invalid value in `config.yml` falls back to the default instead of crashing
  the server. Its own GUI/commands are still outstanding.
- **Database** - SQLite (default, zero setup) or MySQL/MariaDB; both drivers
  are bundled, all access is asynchronous.
- **No mandatory dependencies** - no Vault, no LuckPerms, no PlaceholderAPI,
  no ProtocolLib required.
- **Bilingual** - `en_US` and `de_DE` out of the box; every visible string
  comes from `lang/<locale>.yml`.

## Requirements

- **Paper**, the version this is built against - currently Paper API `26.2`
  (exact version: `build.gradle.kts`). Spigot/CraftBukkit are not supported.
- **Java 25** on the server (same version as the build toolchain).
- Optional: a MySQL/MariaDB server, if multiple servers should share data.
  Without one, SQLite runs with zero setup.

## Installation

Prebuilt jars are on
[Releases](https://github.com/nicki41/UniversalAdmin/releases) (once the
first release is tagged). Drop the jar into the `plugins/` folder and restart
the server - done.

Build it yourself:

```bash
./gradlew build
```

The result is under `build/libs/universaladmin-core-<version>.jar` and
already bundles the database drivers. Full instructions including
uninstallation: [docs/user/installation.md](docs/user/installation.md).

## Quick Start

1. Drop the jar into `plugins/`, start the server.
2. In-game, type `/admin` (aliases: `/ua`, `/uadmin`). The main menu shows
   every active module page.
3. Runs immediately on SQLite. Configure afterward in
   `plugins/UniversalAdmin/config.yml`, apply changes with `/admin reload`.

| Command | Purpose |
|---|---|
| `/admin` | Main menu (console/command blocks get a status report instead) |
| `/admin reload` | Reloads UniversalAdmin's own `config.yml` (never Bukkit's global `/reload`) |
| `/admin server broadcast\|shutdown\|restart\|cancel ...` | Server control from the console |
| `/admin staff recover` | Manually restores a stuck staff-mode snapshot |

Most functionality is currently GUI-driven. The underlying actions already
run through `ActionExecutor` and are command-ready - only the command
frontends themselves are missing (see [ROADMAP.md](ROADMAP.md)).

## Database

SQLite is the default and needs no setup; the file lives in the plugin
folder. MySQL/MariaDB is optional and configurable in `config.yml` under
`database:`. All access goes through `PreparedStatement`s and runs
asynchronously, never blocking the Paper main thread. Schema changes go
through versioned migrations. Details, backup guidance, and security notes:
[docs/user/database.md](docs/user/database.md).

## Permissions

Every protected action has its own node of the form
`universaladmin.<module>.<node>`, registered at runtime (not statically in
`plugin.yml`), compatible with any standard permission plugin. All nodes
default to `op`. Full list: [docs/user/permissions.md](docs/user/permissions.md).

## Anonymous Statistics

[![Servers](https://telemetry.0nicki.de/v1/badge/servers.svg?plugin=universaladmin)](docs/user/telemetry.md)
[![Players Online](https://telemetry.0nicki.de/v1/badge/players.svg?plugin=universaladmin)](docs/user/telemetry.md)

UniversalAdmin can send anonymous usage statistics to answer three
questions: **how many installations are active**, **how many players are
online across all of them**, and **how versions are distributed**. The
badges above are live, pulled from the same aggregate the numbers above come
from - `nicki41-telemetry`, a separate, generic (not UniversalAdmin-only)
backend project.

**Active by default, reporting to `https://telemetry.0nicki.de`** - a fresh
install starts sending the heartbeat below automatically; see "Switch it off
completely" below to disable it entirely, or point `telemetry.endpoint` at
your own `nicki41-telemetry` instance instead.

A heartbeat consists of exactly seven fields:

```json
{
  "pluginId": "universaladmin",
  "installationId": "0123456789abcdef0123456789abcdef",
  "pluginVersion": "0.1.0-alpha",
  "minecraftVersion": "1.21.4",
  "javaMajorVersion": 25,
  "onlinePlayers": 17,
  "maxPlayers": 100
}
```

Explicitly **never** transmitted: server IP, hostname, domain, port, player
names, player UUIDs, player IPs, chat, commands, world names, coordinates,
other installed plugins, file or database contents, hardware identifiers, MAC
addresses, OS username, or file paths. The installation id is pure random
(128 bits) and derived from nothing - not a hardware fingerprint. The player
count is nothing but a number.

Switch it off completely:

```yaml
telemetry:
  enabled: false
```

Then no request is made at all - there is no hidden fallback and no
"essential" second channel. Full documentation (every field, interval,
failure behavior, open items):
[docs/user/telemetry.md](docs/user/telemetry.md). Nothing is collected that
isn't documented there.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - architecture overview and principles
- [ROADMAP.md](ROADMAP.md) - what exists, what's next
- [CHANGELOG.md](CHANGELOG.md) - change history
- [docs/user/](docs/user/) - installation, configuration, permissions,
  database, audit log, [telemetry](docs/user/telemetry.md), modules
- [docs/development/](docs/development/) - setup, conventions, testing,
  adding a module, the GUI framework, the settings system, and the binding
  [development rules](docs/development/architecture-rules.md)
- [docs/architecture/](docs/architecture/) - deeper architecture docs and
  [ADRs](docs/architecture/decisions/)
- [docs/release/](docs/release/) - [release process](docs/release/releasing.md),
  [licensing](docs/release/licensing.md), Modrinth preparation

Module documentation:
[Players](docs/user/modules/players.md) ·
[Moderation](docs/user/modules/moderation.md) ·
[Staff Tools](docs/user/modules/staff-tools.md) ·
[Server](docs/user/modules/server.md) ·
[Worlds](docs/user/modules/worlds.md) ·
[Whitelist](docs/user/modules/whitelist.md) ·
[Performance](docs/user/modules/performance.md) ·
[Audit Log](docs/user/audit-log.md)

## Extensions

**Planned, not yet available.** There is no public, versioned extension API
today - third-party extensions can't be installed yet.

The core is built for it, though: every built-in module uses exactly the
same abstractions (`Module`, `Action`, `GuiPage`, `Repository`, `Migration`,
`PermissionRegistry`) that will later be open to extensions too. There is
deliberately no "shortcut" for built-ins that an extension couldn't also
take - see
[ADR 0005](docs/architecture/decisions/0005-extension-ready-design.md) and
[docs/architecture/extensions-future.md](docs/architecture/extensions-future.md).

The public extension API is the **next major milestone**.

## Web App

**Planned, not yet available.** An optional web app with a REST API over the
same services/actions as the GUI and commands is planned, but deliberately
deferred until the core is stable - see
[docs/architecture/web-future.md](docs/architecture/web-future.md). The
`web.enabled` key already exists as a reserved, currently no-op setting.

## Roadmap

| Phase | Contents | Status |
|---|---|---|
| Core | Architecture, modules, GUI, actions, audit, settings, storage | largely done |
| Command frontends | `/admin players`, `/admin moderation`, ... | outstanding |
| **Extension API** | `universaladmin-api`, extension loader, SDK | **next milestone** |
| Official extensions | e.g. Vault, Discord integration | outstanding |
| Marketplace | extension distribution | outstanding, format not yet decided |
| Web app | REST API, WebSockets, dashboard | outstanding |
| Multi-server | proxy support (BungeeCord/Velocity) | outstanding |

Full detail: [ROADMAP.md](ROADMAP.md).

## Contributing

Contributions are welcome. Setup, architecture rules, and test expectations:
[CONTRIBUTING.md](CONTRIBUTING.md).

Bug reports and feature requests as a
[GitHub Issue](https://github.com/nicki41/UniversalAdmin/issues), please.

## Security

Please do **not** report security vulnerabilities as a public issue - see
[SECURITY.md](SECURITY.md) for the reporting path.

## License

Apache License 2.0 - see [LICENSE](LICENSE). Commercial use is permitted.
What this means for the planned extension API, community extensions, and a
possible marketplace backend is covered in
[docs/release/licensing.md](docs/release/licensing.md) (not legal advice).
