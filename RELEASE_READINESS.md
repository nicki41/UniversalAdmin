# Release Readiness - 1.4.0

Status of the 1.4.0 release: opt-in alt detection and the BungeeCord proxy
plugin, on top of 1.3.0's punishment templates, network whitelist and vanish
across the network (details in the [changelog](CHANGELOG.md)). What a manual
pass covers is in the [test plan](docs/release/test-plan.md); what was
verified for earlier versions below still holds.

## Overview

| Item | Status |
|---|---|
| **Version** | `1.4.0` (`build.gradle.kts`; `./gradlew -q printVersion`) |
| **Build** | `./gradlew clean build` green - all projects, including `verifyShadedJarDrivers` (real SQLite connection through the finished jar) |
| **Tests** | unit and integration tests for services, actions, migrations (real SQLite), settings, localization, commands' building blocks, live inventory sync, the network repository, the Discord extension and the Velocity plugin |
| **Supported server** | Paper/Purpur 1.21.7 - 1.21.11 and 26.1.1 - 26.3, Java 21+ - see [installation.md](docs/user/installation.md#supported-versions) and [supported-versions.txt](supported-versions.txt) |
| **Modules** | Players, Moderation (+ staff tools, notes), Reports, Chat, Server, Worlds, Whitelist, Performance, Audit Log, Settings; Network (with network mode) |
| **Commands** | every module, `/admin help`, shortcuts - [commands.md](docs/user/commands.md) |
| **Extension API** | `universaladmin-api` 1.3, semantically versioned - [ADR 0010](docs/architecture/decisions/0010-extension-api.md) |
| **Official extensions** | UniversalAdmin Discord (webhook or bot), UniversalAdmin Placeholders (PlaceholderAPI); proxy plugins UniversalAdmin Velocity and UniversalAdmin BungeeCord |
| **Release pipeline** | tag → GitHub release (plugin, Discord and Placeholders extensions, Velocity and BungeeCord plugins, API jars, checksums) + Modrinth versions for every supported game version; new Paper versions tested weekly - [releasing.md](docs/release/releasing.md) |
| **Telemetry** | on by default, fully documented, single switch `telemetry.enabled` - [telemetry.md](docs/user/telemetry.md) |
| **Upgrade path** | 1.1.x - 1.3.x → 1.4.0: replace the jars (new tables and columns created, maintenance and whitelist metadata taken over). 0.1.0-alpha → 1.4.0 automatic: config migration v3, new tables, layered language files - see the changelog |

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

For 1.3.0 additionally: 414 automated tests (template loading including
both bundled files, escalation counting, the template action through a
real action executor, label clashes of the moderation commands, scoped
whitelist storage and its take-over, the network whitelist, the proxy's
whitelist lookup, vanish in the network tables); live on Paper 26.3
(SQLite) and on the network of two Paper 26.3 servers behind Velocity 4.3
on MariaDB: migrations 4, 1013 and 1014 on an existing 1.2.1 database,
`/punish` escalating warn → 1h mute with the note in the reason, a removed
warning no longer counting, unknown templates refused; survival taking over
its legacy whitelist entry while the lobby does not; the network whitelist
switched on the lobby reaching survival and the proxy within seconds. Not
yet with real clients: a login refused by the network whitelist, vanish
hidden in another server's lists (test plan sections 12 and 24). A first
live run caught `/punish` clashing with the old alias of `/admin moderate`,
which kept the Moderation module from starting - fixed, and a test now
checks the labels.

For 1.4.0 additionally: 426 automated tests (address hashing for IPv4,
IPv6 /64 and mapped addresses; alt detection against SQLite - recording,
matching, ban status, retention, forgetting, nothing stored while off; the
shared proxy checks - which refusal a bypass node lifts); live on the test
network: migration 1015, `/admin alts`, the reworked Velocity plugin
(all three checks, "Maintenance" in the server list during network
maintenance), and a BungeeCord proxy (latest build) with
UniversalAdmin BungeeCord on the same database: start without a database
configured, all three checks active once it was, "Maintenance" in the server
list during network maintenance and back after, `/uabungee`. Not yet with
real clients: alt hints and refusals (they need two accounts logging in),
logins refused by the BungeeCord plugin (test plan sections 4 and 24).

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
  server; IP bans need the player on the issuing server; staff mode stays
  per server; another server learns about a vanish with the next heartbeat
  (a few seconds) - see
  [network.md](docs/user/network.md#limits). A network server without
  `network.server-name` learns its own maintenance only once the first
  player joined.
