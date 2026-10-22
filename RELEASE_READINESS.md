# Release Readiness - 1.2.1

Status of the 1.2.1 release: maintenance per server or for the whole
network, on top of 1.2.0's punishment import, PlaceholderAPI extension and
Discord bot (details in the [changelog](CHANGELOG.md)). What a manual pass
covers is in the [test plan](docs/release/test-plan.md); what was verified
for 1.1.0 and 1.2.0 below still holds.

## Overview

| Item | Status |
|---|---|
| **Version** | `1.2.1` (`build.gradle.kts`; `./gradlew -q printVersion`) |
| **Build** | `./gradlew clean build` green - all projects, including `verifyShadedJarDrivers` (real SQLite connection through the finished jar) |
| **Tests** | unit and integration tests for services, actions, migrations (real SQLite), settings, localization, commands' building blocks, live inventory sync, the network repository, the Discord extension and the Velocity plugin |
| **Supported server** | Paper/Purpur 1.21.7 - 1.21.11 and 26.1.1 - 26.3, Java 21+ - see [installation.md](docs/user/installation.md#supported-versions) and [supported-versions.txt](supported-versions.txt) |
| **Modules** | Players, Moderation (+ staff tools, notes), Reports, Chat, Server, Worlds, Whitelist, Performance, Audit Log, Settings; Network (with network mode) |
| **Commands** | every module, `/admin help`, shortcuts - [commands.md](docs/user/commands.md) |
| **Extension API** | `universaladmin-api` 1.2, semantically versioned - [ADR 0010](docs/architecture/decisions/0010-extension-api.md) |
| **Official extensions** | UniversalAdmin Discord (webhook or bot), UniversalAdmin Placeholders (PlaceholderAPI); proxy plugin UniversalAdmin Velocity |
| **Release pipeline** | tag → GitHub release (plugin, Discord extension, Velocity plugin, API jars, checksums) + Modrinth versions for every supported game version; new Paper versions tested weekly - [releasing.md](docs/release/releasing.md) |
| **Telemetry** | on by default, fully documented, single switch `telemetry.enabled` - [telemetry.md](docs/user/telemetry.md) |
| **Upgrade path** | 1.2.0 → 1.2.1 and 1.1.x → 1.2.1: replace the jars (new tables, maintenance state copied). 0.1.0-alpha → 1.2.1 automatic: config migration v3, new tables, layered language files - see the changelog |

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
  players, messages), the Velocity ban lookup, ban screen and maintenance
  lookup; maintenance per scope (own row per server, network checked first,
  the row from before 1.2.1, a server learning its proxy name).

On real servers (boot test: every module enabled, about 20 console
commands, no errors, no missing texts):

- Paper 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.1, 26.1.2, 26.2,
  26.3. Paper 1.21.4 refuses the plugin cleanly (`api-version`).
- A network of two Paper 26.2 servers behind Velocity 4.3 on one MariaDB:
  network list with TPS, `/admin find`, staff chat and `/admin alert`
  across servers, a ban issued on one server for a player known only on the
  other, audit entries tagged with the server, servers starting together on
  an empty database, the Velocity plugin starting before the database.

For 1.2.0 additionally: 365 automated tests, including fixture databases
for every import source; live on Paper 1.21.11 with the real AdvancedBan
2.3.0, EssentialsX 2.22.0 and PlaceholderAPI 2.12.3 (imports, re-runs,
placeholders); the Discord extension's library loading and error handling
in bot mode. **Not yet verified against a real Discord server: the bot's
slash commands, staff chat bridge and report buttons** - test plan section
23.

For 1.2.1 additionally: 380 automated tests; live on Paper 26.3 (SQLite:
maintenance on/off, allow-list, `network` refused without network mode) and
on the network of two Paper 26.3 servers behind Velocity 4.3 on MariaDB:
survival's own maintenance leaves the lobby open (one row per server in
`ua_maintenance`), network maintenance switched on the lobby reaches
survival within a second and back off again, the proxy's server list shows
"Maintenance" during network maintenance only, `/uavelocity` shows both
checks, the proxy starting before the table exists, the LuckPerms hint.
Refused logins at the proxy need a real client (test plan section 24).

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
  server; IP bans need the player on the issuing server; whitelist, vanish
  and staff mode stay per server - see
  [network.md](docs/user/network.md#limits). A network server without
  `network.server-name` learns its own maintenance only once the first
  player joined.
