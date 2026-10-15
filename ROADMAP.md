# Roadmap

What exists, and what comes next. Not a dated plan - order within a block is
a recommendation. Every item builds on the structure in
[ARCHITECTURE.md](ARCHITECTURE.md), not around it. Ideas and votes are
welcome as [GitHub issues](https://github.com/nicki41/UniversalAdmin/issues).

## Done - 1.0.0

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
- [x] Releases: GitHub (plugin, Discord extension, API jars), Modrinth,
      update checker, anonymous statistics with opt-out

## Next - 1.x

Additive, no breaking changes.

- [ ] **Import** bans/mutes/warnings from LiteBans, AdvancedBan and vanilla
      `banned-players.json` - the most requested migration path
- [ ] **Punishment templates and escalation** ("spam": 1st = warn, 2nd =
      1h mute, 3rd = 1d mute) selectable in the wizard and via
      `/punish <player> <template>`
- [ ] **Alt detection** based on hashed IPs (opt-in, privacy-friendly - raw
      IPs are not stored for this)
- [ ] **PlaceholderAPI** expansion as an official extension (vanish state,
      open reports, punishment counts)
- [ ] **Vault** bridge for economy/permission display on profiles (official
      extension)
- [ ] Ticket/support system as an official extension built on the API
- [ ] More languages (community translations - `lang/<locale>.yml` is all it
      takes)
- [ ] Audit log export (CSV) and per-module retention
- [ ] Loading/unloading worlds from the Worlds module
- [ ] A curated extension list in the docs

## Later - 2.0 candidates

- [ ] **Proxy support** (Velocity/BungeeCord): network-wide bans, mutes,
      staff chat and reports across servers sharing one MySQL database
- [ ] **Web panel**: REST API over the same actions, live dashboard via
      WebSockets, web logins separate from Minecraft accounts - see
      [docs/architecture/web-future.md](docs/architecture/web-future.md)
- [ ] Extension marketplace/registry (format still open)

## Deliberately not planned

- Folia support - only once there is real demand; the scheduler abstraction
  keeps the door open.
- Packet-level features (ProtocolLib, NMS) in the core - those belong in an
  extension, see [docs/development/architecture-rules.md](docs/development/architecture-rules.md).
- Spigot/CraftBukkit or old Minecraft versions.
