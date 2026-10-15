# UniversalAdmin

[![Build](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml/badge.svg)](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-universaladmin-00AF5C)](https://modrinth.com/plugin/universaladmin)

**The free, all-in-one admin plugin for Paper.** Moderation, reports, staff
chat, vanish, staff mode, player tools, inventories, worlds, whitelist,
maintenance, performance and a full audit log - in clean in-game menus *and*
commands, with no dependencies and no paywall. Open source, extensible, made
to be the one admin plugin your server needs.

```
/admin          → the admin menu
/ban Steve 7d griefing    /mute Bob 1h spam    /report Griefer123 broke my house
```

## Why UniversalAdmin

- **Everything in one place.** One menu, one permission system, one audit
  log - instead of five plugins that don't know about each other.
- **Menus and commands.** Every feature works from the GUI and from chat or
  the console - same permissions, same messages, same audit trail.
- **Every action is audited.** Who banned whom, who changed which gamerule,
  who opened whose inventory - searchable in-game, with player names.
- **Fine-grained permissions.** Give helpers "kick and mute" without "ban".
  Role templates for LuckPerms are included.
- **Safe by design.** Dangerous actions ask for confirmation, double clicks
  are ignored, player input can't inject formatting or click events, and an
  invalid config value never crashes your server.
- **No dependencies.** No Vault, ProtocolLib, PlaceholderAPI or Essentials
  required. SQLite works out of the box, MySQL/MariaDB optional.
- **English and German** out of the box - every text is editable.
- **Free forever.** Apache-2.0, no premium version, no locked features.

## Features

| | |
|---|---|
| **Moderation** | Kick, ban, temp-ban, IP-ban, mute, temp-mute, warn, freeze - with reason and duration presets, a guided GUI wizard, full history and `/checkban`. Team notifications for every punishment. |
| **Reports** | `/report` for players, clickable instant alerts for staff, a reports GUI with claim/resolve/reject and one-click teleports. Spam-protected. |
| **Staff tools** | Vanish (with fake join/leave), godmode, no-collision, and a crash-safe **staff mode** with a toolbar: player inspector, freeze stick, live inventory/ender chest inspectors, teleport picker. |
| **Staff notes** | Private notes about players, with a heads-up when such a player joins. |
| **Chat** | Staff chat (`/sc` or `#message`), chat lock, slow mode, clear chat. |
| **Players** | Online/offline browser with search, profile pages, heal/feed/fly/gamemode/speed/effects, teleports, and a **live** inventory and ender chest editor. |
| **Server** | Dashboard, broadcasts (chat, title, action bar), maintenance mode with allow-list, shutdown/restart with countdown. |
| **Worlds** | Time, weather, difficulty, spawn, world border and a full gamerule editor - per world. |
| **Whitelist** | With reasons, notes and **temporary** access that expires on its own. |
| **Performance** | TPS/MSPT/memory dashboard, per-world stats, entity overview, staff alerts, and a careful entity clear that never touches players or protected mobs. |
| **Audit log** | Every admin action, filterable by player, module and time, with details. |
| **Settings** | Edit every setting in-game - validated, saved with comments intact. |
| **Extensions** | A public API for other plugins - see below. Official: **Discord logging**. |

## Requirements

- **Paper 26.2** (Spigot/CraftBukkit are not supported)
- **Java 25**
- Optional: MySQL or MariaDB to share data between servers

## Quick start

1. Download the jar from [Modrinth](https://modrinth.com/plugin/universaladmin)
   or [GitHub](https://github.com/nicki41/UniversalAdmin/releases), put it in
   `plugins/`, restart.
2. Join as an operator and type **`/admin`**.
3. Give your team permissions - copy a role template from
   [docs/user/permissions.md](docs/user/permissions.md#role-templates).
4. Optional: tweak settings in-game (`/admin` → Settings) or in
   `plugins/UniversalAdmin/config.yml`.

That's it - the database (SQLite) is created automatically.

## Most used commands

| Command | |
|---|---|
| `/admin` | Admin menu |
| `/kick`, `/ban`, `/tempban`, `/mute`, `/warn`, `/freeze` `<player> [duration] [reason]` | Punish |
| `/unban`, `/unmute`, `/unfreeze` `<player>` · `/unwarn <id>` | Pardon |
| `/history <player>` · `/checkban <player>` | Punishment history / current status |
| `/report <player> <reason>` · `/admin reports` | Reports |
| `/vanish` · `/staff` · `/sc <message>` | Staff tools |
| `/invsee` · `/endersee` · `/tphere` · `/tpto` `<player>` | Player tools |
| `/heal` · `/feed` · `/fly` · `/gm <mode>` `[player]` | Quick actions |
| `/admin maintenance on\|off` · `/broadcast <message>` | Server |
| `/lag` · `/admin entities` | Performance |
| `/admin audit [player]` | What happened? |
| `/admin help` | Everything you may use |

Shortcuts like `/ban` can be switched off if another plugin should keep
them; `/admin ban` always works. Full reference:
[docs/user/commands.md](docs/user/commands.md).

## FAQ

**Can I use it together with EssentialsX / CMI?** Yes. If both register the
same command (e.g. `/ban`), add the labels you want to keep elsewhere to
`commands.shortcuts.disabled` - or switch UniversalAdmin's shortcuts off and
use `/admin ban`.

**LuckPerms?** Fully supported - every node shows up in LuckPerms' editor.
Templates: [permissions.md](docs/user/permissions.md#role-templates).

**MySQL?** Set `database.type: mysql` and the connection details in
`config.yml`. See [docs/user/database.md](docs/user/database.md).

**Folia / Spigot / older Minecraft versions?** Not supported - UniversalAdmin
targets current Paper.

**Can I import bans from another plugin?** Not yet - it is on the
[roadmap](ROADMAP.md).

**How do I change the messages?** Edit `plugins/UniversalAdmin/lang/<language>.yml`,
then `/admin reload`. Only keep the keys you changed - everything else
updates with the plugin.

## Extensions

Other plugins can add their own modules to UniversalAdmin: `/admin`
commands, GUI pages with a menu button, buttons on player profiles, audited
actions, settings, database tables. An extension is a normal Paper plugin:

```java
UniversalAdminProvider.get().extensions().register(this, new MyModule());
```

- Developer guide: [docs/extensions/getting-started.md](docs/extensions/getting-started.md)
- Template: [examples/example-extension](examples/example-extension)
- API jars (binary, sources, javadoc) are attached to every
  [release](https://github.com/nicki41/UniversalAdmin/releases).

**Official extension - UniversalAdmin Discord:** posts bans, mutes, reports,
restarts and more to a Discord channel. Setup takes two minutes:
[docs/extensions/discord.md](docs/extensions/discord.md).

## Anonymous statistics

[![Servers](https://telemetry.0nicki.de/v1/badge/servers.svg?plugin=universaladmin)](docs/user/telemetry.md)
[![Players Online](https://telemetry.0nicki.de/v1/badge/players.svg?plugin=universaladmin)](docs/user/telemetry.md)

UniversalAdmin sends a small anonymous heartbeat (random installation id,
plugin/Minecraft/Java version, player count) so we know how many servers use
it. **Never** any IP, server name, player name, UUID, chat or world data.
Switch it off with `telemetry.enabled: false` - then no request is made at
all. Exactly what is sent: [docs/user/telemetry.md](docs/user/telemetry.md).

## Documentation

- **Server owners:** [installation](docs/user/installation.md) ·
  [commands](docs/user/commands.md) · [permissions](docs/user/permissions.md) ·
  [configuration](docs/user/configuration.md) · [database](docs/user/database.md) ·
  [audit log](docs/user/audit-log.md) · [telemetry](docs/user/telemetry.md)
- **Modules:** [players](docs/user/modules/players.md) ·
  [moderation](docs/user/modules/moderation.md) ·
  [staff tools](docs/user/modules/staff-tools.md) ·
  [reports](docs/user/modules/reports.md) · [chat](docs/user/modules/chat.md) ·
  [server](docs/user/modules/server.md) · [worlds](docs/user/modules/worlds.md) ·
  [whitelist](docs/user/modules/whitelist.md) ·
  [performance](docs/user/modules/performance.md) ·
  [settings](docs/user/modules/settings.md)
- **Developers:** [architecture](ARCHITECTURE.md) ·
  [extensions](docs/extensions/getting-started.md) ·
  [development rules](docs/development/architecture-rules.md) ·
  [ADRs](docs/architecture/decisions/) · [contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md) · [Roadmap](ROADMAP.md)

## Support and contributing

Found a bug or have an idea? Open an
[issue](https://github.com/nicki41/UniversalAdmin/issues) - please include
your Paper and UniversalAdmin version (`/admin status`). Pull requests are
welcome: [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: please report
privately, see [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE) - free for every server, commercial ones
included.
