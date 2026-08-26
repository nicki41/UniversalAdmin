# Modrinth Page (Draft)

Draft for the future Modrinth project page. **Nothing has been uploaded
yet** - this document is the preparation for that, not published content and
not an automated upload.

## Project Title

**UniversalAdmin**

## Project Metadata

- **Name:** UniversalAdmin
- **Slug:** `universaladmin` (check availability on Modrinth before creating
  it)
- **Category:** Admin Tools / Utility / Management
- **Client/Server side:** Server-only (no client mod needed)
- **License:** Apache-2.0 (decided, see [licensing.md](licensing.md);
  `LICENSE` is in the repository root)
- **Source:** https://github.com/nicki41/UniversalAdmin

## Summary

> A dependency-free administration platform for Paper servers: full in-game
> GUIs for players, moderation, worlds, whitelist and performance, every action
> permission-checked and audited.

(Modrinth's summary field is length-limited - shorten to this if needed: "A
dependency-free admin platform for Paper servers with full in-game GUIs and a
complete audit trail.")

## Description

> **UniversalAdmin** is a universal admin platform for Paper servers - not
> another pile of unrelated commands, but a core with a clean layered
> architecture (GUI/Commands → Services → Actions → Repositories →
> Paper/Database) built to grow into a full extension ecosystem and, later, an
> optional web dashboard.
>
> Everything - kicking a player, changing a gamerule, clearing a filtered set
> of lagging entities - runs through the same permission-checked, audited
> pipeline whether it's triggered from the in-game GUI or (soon) a command or a
> future REST API. No feature reimplements its own permission handling, its own
> SQL, or its own audit logging.
>
> **Status: alpha.** Six of the eight built-in modules are fully usable. There
> is no public extension API yet and no web app - both are planned.

## Features

- **Players** - browser (online/offline/last-seen/search), profile page,
  ~20 actions (teleport, heal, effects, gamemode, inventory/ender chest
  editor).
- **Moderation** - kick/ban/tempban/IP-ban/mute/tempmute/warn/unban/
  unmute/remove-warn, join/chat enforcement, GUI wizard, plus vanish/
  godmode/no-collision/staff mode with crash-safe snapshot/recovery.
- **Server** - live dashboard, broadcast (message/title/actionbar),
  maintenance mode with an allow-list, shutdown/restart with confirmation
  and a configurable countdown.
- **Worlds** - world browser/profile, teleport/spawn/time/weather/
  difficulty, world border management, a dynamic gamerule GUI that reads
  every gamerule at runtime (no hardcoded list).
- **Whitelist** - native whitelist wrapping plus its own metadata (reason,
  notes, expiration), ownership model, join check with an hourly sweep.
- **Performance** - TPS/MSPT/memory dashboard, per-world breakdown,
  entity overview grouped by type/world, short in-memory history,
  staff alerts on configurable thresholds, and a deliberately narrow
  Entity Clear (never players, protected types excluded by default,
  live preview, confirmation, fully audited) - **not** an aggressive lag
  cleaner.
- **Audit Log** - every mutating action is audited automatically; a
  filterable, paginated in-game log with a detail view.
- **Full audit trail, typed permissions, typed/validated settings,
  bilingual (English/German) out of the box.**

## Requirements

- Paper (Spigot/CraftBukkit are not supported)
- Java 25 on the server
- No required plugin dependencies
- Optional: MySQL/MariaDB for multi-server setups

## Supported Minecraft/Paper Versions

- Built and tested against Paper API `26.2.build.115-stable` (see
  `build.gradle.kts` for the exact pinned version at any given time).
- Requires Java 25 on the server.
- **TODO before release:** decide and state the actual supported Minecraft
  version range (currently only the single Paper API build above is verified in
  CI) - confirm whether older Paper builds also work, or whether the plugin is
  pinned to the exact API version it compiles against. Modrinth requires
  explicit game versions per upload, so this must be answered before the first
  listing.

## Dependencies

**None required.** No mandatory dependency on Vault, LuckPerms,
PlaceholderAPI, or ProtocolLib - see [README.md](../../README.md#requirements).
Works with any standard permission plugin (or plain server-operator status)
since permission nodes are registered through Bukkit's own `PluginManager`.

The jar bundles its own database drivers (SQLite, MariaDB/MySQL) and connection
pool, so nothing has to be installed alongside it.

## Installation

1. Download the jar (or build it yourself - see
   [README.md](../../README.md#installation)).
2. Drop it into the server's `plugins/` folder.
3. Start the server. `config.yml` and `lang/en_US.yml`/`lang/de_DE.yml` are
   created automatically in `plugins/UniversalAdmin/`.
4. Runs immediately on the bundled SQLite database - no setup required.

Full instructions: [docs/user/installation.md](../user/installation.md).

## Database

SQLite by default (zero setup, created inside the plugin's own data folder) -
optional MySQL/MariaDB for multi-server setups that want to share data. See
[docs/user/database.md](../user/database.md) for the full explanation,
including backup guidance and the security notes on credential handling.

## Commands

| Command | Purpose |
|---|---|
| `/admin` (aliases `/ua`, `/uadmin`) | Opens the main menu for a player; console/command blocks get a status report |
| `/admin reload` | Reloads UniversalAdmin's own `config.yml` (never Bukkit's global `/reload`) |
| `/admin server broadcast\|shutdown\|restart\|cancel …` | Server control from the console |
| `/admin staff recover` | Manually restores a stuck staff-mode snapshot |

Most functionality is GUI-driven today; the underlying actions already run
through the same executor and are command-ready, only the command frontends are
still missing.

## Permissions

Every protected action has its own `universaladmin.<module>.<node>` permission
node, registered at runtime and compatible with any standard permission plugin.
All nodes default to `op`. The complete, current list lives in
[docs/user/permissions.md](../user/permissions.md) - link to it from the
Modrinth page rather than duplicating it there (it would go stale).

## Telemetry Disclosure

Must appear on the project page if the Modrinth rules in effect at the time
of publishing require a telemetry disclosure (check against the then-current
rules before uploading - no platform compliance is claimed here). Suggested
text:

> **Anonymous statistics**
>
> UniversalAdmin can send an anonymous heartbeat so the project can see how
> many installations are active, how many players are online across them, and
> how versions are distributed.
>
> **In this version nothing is sent:** there is no official statistics endpoint
> yet and none is preconfigured, so no request is made and no installation id
> is even generated.
>
> When an endpoint is configured, a heartbeat contains exactly six values: a
> random installation id, the UniversalAdmin version, the Minecraft version,
> the Java major version, the number of online players, and the server's player
> slot count.
>
> Never collected: server IP, hostname, domain, port, player names, player
> UUIDs, player IPs, chat, commands, world names, coordinates, other installed
> plugins, file or database contents, hardware identifiers, MAC addresses, OS
> user names, or file paths. The installation id is 128 random bits and is not
> derived from anything about the machine.
>
> Switch it off completely with `telemetry.enabled: false` in `config.yml` -
> then no request of any kind is made. Full documentation:
> https://github.com/nicki41/UniversalAdmin/blob/main/docs/user/telemetry.md

## Links

| Link type | URL |
|---|---|
| Source | https://github.com/nicki41/UniversalAdmin |
| Issue tracker | https://github.com/nicki41/UniversalAdmin/issues |
| Wiki / Documentation | https://github.com/nicki41/UniversalAdmin/tree/main/docs |
| Discord | none - don't list one until one actually exists |

## Screenshots

None captured yet - needed before the first public listing:

- [ ] Server dashboard (TPS/MSPT/memory/players/modules tile view)
- [ ] Player browser + profile page
- [ ] Moderation punishment wizard (kick/ban/mute flow)
- [ ] Performance dashboard
- [ ] Performance entity overview (by type)
- [ ] Worlds browser + gamerule GUI
- [ ] Whitelist member list
- [ ] Audit log list + detail view
- [ ] A short (10-20s) clip of the main menu navigation flow

## Versioning

- Version number, release type, and tag scheme come from the GitHub release
  - see [releasing.md](releasing.md). A Modrinth upload uses exactly the
  same version number as the corresponding GitHub release.
- Versions with `-alpha`/`-beta`/`-rc` are uploaded to Modrinth as **Alpha**
  or **Beta**, never as a Release.
- Only the installable, shaded jar (`universaladmin-core-<version>.jar`) is
  uploaded - no sources or javadoc jar. The SHA-256 file from the GitHub
  release can be linked for verification.

## Release Checklist

Before the first Modrinth upload:

- [x] License decided and `LICENSE` in the repository (Apache-2.0, see
      [licensing.md](licensing.md)).
- [x] Public GitHub repository with CI and automated releases.
- [x] Telemetry documented ([docs/user/telemetry.md](../user/telemetry.md))
      and the disclosure text above prepared.
- [ ] Screenshots/gallery captured (list above).
- [ ] Supported Minecraft/Paper version range confirmed and stated (not just
      "whatever was last compiled against").
- [ ] A real Paper server run with the release jar (GUI navigation,
      moderation edge cases) - see
      [RELEASE_READINESS.md](../../RELEASE_READINESS.md).
- [ ] `./gradlew clean build` green, including tests.
- [ ] `CHANGELOG.md` has a dated version section (not just `[Unreleased]`).
- [ ] Version in `build.gradle.kts` matches the tag being published.
- [ ] GitHub release exists and contains the jar + SHA-256.
- [x] Modrinth project created (id `wGP5uSse`, still in "draft" status);
      slug/category/description above still need reviewing and pasting in.
- [ ] Telemetry disclosure checked against the then-current Modrinth rules
      and pasted in.
- [x] Every GitHub release is automatically mirrored to Modrinth as a new
      version - see [releasing.md](releasing.md#modrinth). The project page
      itself (description, categories, screenshots) is still only prepared
      above, not filled in - that part stays a manual, deliberate step.
