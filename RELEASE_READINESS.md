# Release Readiness - 1.1.0

Status of the 1.1.0 release - the first stable release (1.0.0 was folded
into it). What a manual pass covers is in the
[test plan](docs/release/test-plan.md).

## Overview

| Item | Status |
|---|---|
| **Version** | `1.1.0` (`build.gradle.kts`; `./gradlew -q printVersion`) |
| **Build** | `./gradlew clean build` green - all projects, including `verifyShadedJarDrivers` (real SQLite connection through the finished jar) |
| **Tests** | unit and integration tests for services, actions, migrations (real SQLite), settings, localization, commands' building blocks, live inventory sync, the network repository, the Discord extension and the Velocity plugin |
| **Supported server** | Paper/Purpur 1.21.7 - 1.21.11 and 26.1.1 - 26.3, Java 21+ - see [installation.md](docs/user/installation.md#supported-versions) and [supported-versions.txt](supported-versions.txt) |
| **Modules** | Players, Moderation (+ staff tools, notes), Reports, Chat, Server, Worlds, Whitelist, Performance, Audit Log, Settings; Network (with network mode) |
| **Commands** | every module, `/admin help`, shortcuts - [commands.md](docs/user/commands.md) |
| **Extension API** | `universaladmin-api` 1.1, semantically versioned - [ADR 0010](docs/architecture/decisions/0010-extension-api.md) |
| **Official extensions** | UniversalAdmin Discord; proxy plugin UniversalAdmin Velocity |
| **Release pipeline** | tag → GitHub release (plugin, Discord extension, Velocity plugin, API jars, checksums) + Modrinth versions for every supported game version; new Paper versions tested weekly - [releasing.md](docs/release/releasing.md) |
| **Telemetry** | on by default, fully documented, single switch `telemetry.enabled` - [telemetry.md](docs/user/telemetry.md) |
| **Upgrade path** | 0.1.0-alpha → 1.1.0 automatic: config migration v3, new tables, layered language files - see the changelog |

## Verified

Automated tests:

- Message escaping (no MiniMessage injection through arguments), layered
  language files, broken language files do not stop startup, de_DE has every
  en_US key, no `MessageFormat` apostrophe traps, every key used in code
  exists, success messages of actions.
- Settings: lazy resolution of module settings, in-game set/reset with
  validation, restart-required handling, config migrations v2 and v3.
- Every migration together against a fresh and an upgraded SQLite database;
  a migration added later still runs.
- Reports rules, chat rules, SemVer ordering for the update check, click
  throttle, Discord payload escaping/mentions/URL check, the three-way
  merge of the live inventory editor, the network tables (heartbeats,
  players, messages), the Velocity ban lookup and ban screen.

On real servers (boot test: every module enabled, about 20 console
commands, no errors, no missing texts):

- Paper 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.1, 26.1.2, 26.2,
  26.3. Paper 1.21.4 refuses the plugin cleanly (`api-version`).
- A network of two Paper 26.2 servers behind Velocity 4.3 on one MariaDB:
  network list with TPS, `/admin find`, staff chat and `/admin alert`
  across servers, a ban issued on one server for a player known only on the
  other, audit entries tagged with the server, servers starting together on
  an empty database, the Velocity plugin starting before the database.

## Not verifiable without game clients

These need the manual pass in the [test plan](docs/release/test-plan.md):

- Every GUI page and dialog (Paper dialog API), the live inventory editor
  with two players.
- Proxy messaging with players online: moving players between servers,
  arrival teleports, kicking from the whole network, the list of servers
  without UniversalAdmin, the Velocity ban screen.
- Shortcut registration clashes with other plugins (EssentialsX),
  permission sync into LuckPerms, chat-formatting plugins.
- Real Discord webhook delivery and rate limiting.

## Known limitations

- Shortcuts are registered once at startup; `commands.shortcuts.*` needs a
  restart.
- Chat lock and slow mode are not persisted across restarts (by design).
- Report cooldowns are in memory and reset on restart.
- Existing tables from the alpha (`punishments`, `player_profiles`, ...) keep
  their unprefixed names; only new tables use `ua_`.
- `/admin whitelist add` resolves unknown names through Mojang; offline-mode
  servers should whitelist players after their first join.
- Network mode: proxy requests need one player online on the sending
  server; IP bans need the player on the issuing server; maintenance,
  whitelist, vanish and staff mode stay per server - see
  [network.md](docs/user/network.md#limits).
- No import from other ban plugins yet ([roadmap](ROADMAP.md)).
