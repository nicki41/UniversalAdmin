# Modrinth-Seite (Entwurf)

Entwurf für die künftige Modrinth-Projektseite. **Noch nichts hochgeladen** -
dieses Dokument ist die Vorbereitung dafür, kein veröffentlichter Inhalt und
kein automatisierter Upload. Texte unten sind auf Englisch, da
Modrinth-Projektseiten üblicherweise englischsprachig sind (internationale
Zielgruppe); die interne Doku bleibt überwiegend Deutsch.

## Project Title

**UniversalAdmin**

## Projekt-Metadaten

- **Name:** UniversalAdmin
- **Slug:** `universaladmin` (Verfügbarkeit auf Modrinth prüfen, bevor angelegt
  wird)
- **Kategorie:** Admin Tools / Utility / Management
- **Client/Server-Seite:** Server-only (kein Client-Mod nötig)
- **Lizenz:** Apache-2.0 (entschieden, siehe [licensing.md](licensing.md);
  `LICENSE` liegt im Repository-Root)
- **Source:** https://github.com/nicki41/UniversalAdmin

## Summary

> A dependency-free administration platform for Paper servers: full in-game
> GUIs for players, moderation, worlds, whitelist and performance, every action
> permission-checked and audited.

(Modrinth-Summary ist längenbegrenzt - notfalls kürzen auf: "A dependency-free
admin platform for Paper servers with full in-game GUIs and a complete audit
trail.")

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

Muss auf der Projektseite stehen, sofern die zum Veröffentlichungszeitpunkt
geltenden Modrinth-Regeln eine Offenlegung von Telemetrie verlangen (vor dem
Upload gegen die dann aktuellen Regeln prüfen - hier wird keine
Plattform-Compliance behauptet). Vorgeschlagener Text:

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
| Discord | keiner - nicht angeben, solange keiner existiert |

## Screenshots

Noch keine erstellt - vor dem ersten öffentlichen Listing nötig:

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

- Version number, Release-Typ und Tag-Schema kommen aus dem GitHub-Release -
  siehe [releasing.md](releasing.md). Ein Modrinth-Upload verwendet exakt
  dieselbe Versionsnummer wie der zugehörige GitHub-Release.
- Versionen mit `-alpha`/`-beta`/`-rc` werden auf Modrinth als **Alpha** bzw.
  **Beta** hochgeladen, nie als Release.
- Hochgeladen wird ausschließlich die installierbare, shaded jar
  (`universaladmin-core-<version>.jar`) - keine sources- oder javadoc-jar. Die
  SHA-256-Datei aus dem GitHub-Release kann zur Prüfung verlinkt werden.

## Release Checklist

Vor dem ersten Modrinth-Upload:

- [x] Lizenz entschieden und `LICENSE` im Repository (Apache-2.0, siehe
      [licensing.md](licensing.md)).
- [x] Öffentliches GitHub-Repository mit CI und automatisierten Releases.
- [x] Telemetrie dokumentiert
      ([docs/user/telemetry.md](../user/telemetry.md)) und
      Offenlegungstext oben vorbereitet.
- [ ] Screenshots/Galerie erstellt (Liste oben).
- [ ] Unterstützter Minecraft-/Paper-Versionsbereich bestätigt und angegeben
      (nicht nur "was zuletzt kompiliert wurde").
- [ ] Ein echter Paper-Server-Durchlauf mit der Release-jar (GUI-Navigation,
      Moderation-Edge-Cases) - siehe
      [RELEASE_READINESS.md](../../RELEASE_READINESS.md).
- [ ] `./gradlew clean build` grün, inklusive Tests.
- [ ] `CHANGELOG.md` hat einen datierten Versionsabschnitt (nicht nur
      `[Unreleased]`).
- [ ] Version in `build.gradle.kts` entspricht dem zu veröffentlichenden Tag.
- [ ] GitHub-Release existiert und enthält jar + SHA-256.
- [ ] Modrinth-Projekt angelegt, Slug/Kategorie/Beschreibung oben geprüft und
      eingesetzt.
- [ ] Telemetrie-Offenlegung gegen die dann geltenden Modrinth-Regeln geprüft
      und eingesetzt.
- [ ] Ein Maintainer löst den Upload bewusst aus - es gibt keinen CI-Job, der
      auf Modrinth veröffentlicht (und keinen Modrinth-Token im Repository).
