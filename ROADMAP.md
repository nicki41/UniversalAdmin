# Roadmap

What exists, and what comes next. Not a dated plan - order within a block is
a recommendation. Every item builds on the structure in
[ARCHITECTURE.md](ARCHITECTURE.md), not around it. Ideas and votes are
welcome as [GitHub issues](https://github.com/nicki41/UniversalAdmin/issues).

## Done - 1.1.0

- [x] Modular core: modules with lifecycle and dependencies, actions with
      central authorization/validation/audit, typed settings, layered
      translations (`en_US`, `de_DE`), async storage (SQLite, MySQL/MariaDB)
      with versioned migrations, GUI framework
- [x] Players, Moderation (incl. staff tools and staff notes), Reports, Chat,
      Server, Worlds, Whitelist, Performance, Audit Log, Settings
- [x] Commands for every module, `/admin help`, tab completion, optional
      shortcuts ([ADR 0011](docs/architecture/decisions/0011-command-framework.md))
- [x] In-game settings editor
- [x] Team notifications for punishments, report alerts, join hints
- [x] Escaped message arguments and upgrade-safe language files
      ([ADR 0012](docs/architecture/decisions/0012-safe-messages.md))
- [x] Public extension API `universaladmin-api`, extension loading,
      developer guide and example
      ([ADR 0010](docs/architecture/decisions/0010-extension-api.md))
- [x] Official extension: Discord logging
- [x] **Network mode** (Velocity/BungeeCord): network overview, find,
      cross-server teleports, network-wide punishments, staff chat, reports,
      alerts and audit log over one shared MySQL database, plus a Velocity
      plugin that refuses banned players at the proxy
      ([ADR 0013](docs/architecture/decisions/0013-network-mode.md))
- [x] One jar for Paper 1.21.7 - 26.3, new Paper versions tested and
      published automatically
- [x] Releases: GitHub (plugin, Discord extension, Velocity plugin, API
      jars), Modrinth, update checker, anonymous statistics with opt-out

## Done - 1.2.0

- [x] **Import** bans/mutes/warnings/kicks from LiteBans, AdvancedBan, the
      vanilla ban lists and EssentialsX
      ([ADR 0015](docs/architecture/decisions/0015-punishment-import.md))
- [x] **PlaceholderAPI** placeholders as an official extension, backed by
      status services in the extension API

## Done - 1.2.1

- [x] **Maintenance per server or for the whole network**, enforced at the
      proxy by UniversalAdmin Velocity
      ([ADR 0017](docs/architecture/decisions/0017-maintenance-scopes.md))

## Done - 1.3.0

- [x] **Punishment templates and escalation** ("spam": 1st = warn, 2nd =
      1h mute, 3rd = 1d mute) in the wizard, via `/punish <player> <template>`
      and in the Discord bot
      ([ADR 0018](docs/architecture/decisions/0018-punishment-templates.md))
- [x] **Network whitelist** and **vanish across the network**
      ([ADR 0019](docs/architecture/decisions/0019-network-whitelist-and-vanish.md))

## Done - 1.4.0

- [x] **Alt detection** based on keyed address hashes (opt-in, raw addresses
      are not stored): possible alts on the profile and a staff hint when an
      alt of a banned account joins
      ([ADR 0020](docs/architecture/decisions/0020-alt-detection.md))
- [x] A **BungeeCord** proxy plugin next to the Velocity one, both on shared
      proxy code ([ADR 0021](docs/architecture/decisions/0021-bungeecord-proxy-plugin.md))

## Later - 1.x

Additive, no breaking changes.

- [ ] **Vault** bridge for economy/permission display on profiles (official
      extension)
- [ ] Ticket/support system as an official extension built on the API
- [ ] More languages (community translations - `lang/<locale>.yml` is all it
      takes)
- [ ] Audit log export (CSV) and per-module retention
- [ ] Loading/unloading worlds from the Worlds module
- [ ] A curated extension list in the docs

## Later - 2.0 candidates

- [ ] **Web panel**: REST API over the same actions, live dashboard via
      WebSockets, web logins separate from Minecraft accounts - see
      [docs/architecture/web-future.md](docs/architecture/web-future.md)
- [ ] **Extension builder in the browser**: build extensions by drag and
      drop - menu pages on the chest grid, commands, logic blocks
      (event → condition → action), settings, permissions, translations -
      for people who don't write Java. The result is a real extension
      project against `universaladmin-api` with code blocks and an "eject
      to IDE" path, so an extension can grow as complex as a hand-written
      one - see [docs/architecture/extension-builder.md](docs/architecture/extension-builder.md)
- [ ] **Sharing extensions on the same site** (the extension registry):
      a page per extension with versions and downloads, search, and "open
      in builder" to start from a shared project

## Deliberately not planned

- Folia support - only once there is real demand; the scheduler abstraction
  keeps the door open.
- Packet-level features (ProtocolLib, NMS) in the core - those belong in an
  extension, see [docs/development/architecture-rules.md](docs/development/architecture-rules.md).
- Spigot/CraftBukkit or Minecraft versions below 1.21.7.
