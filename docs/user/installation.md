# Installation

## Supported versions

| UniversalAdmin | Server | Java |
|---|---|---|
| 1.0.x | **Paper 26.2** | 25 |

UniversalAdmin is built and tested against Paper 26.2. Its `plugin.yml`
declares `api-version: '26.2'`, so older servers refuse to load it. Newer
Paper versions may work, but are only listed here (and on Modrinth) once
they have been tested. Spigot, CraftBukkit and Folia are not supported.

## Install

1. Download `universaladmin-core-<version>.jar` from
   [Modrinth](https://modrinth.com/plugin/universaladmin) or the
   [GitHub releases](https://github.com/nicki41/UniversalAdmin/releases)
   (each release lists a SHA-256 checksum).
2. Put it into the server's `plugins/` folder and start (or restart) the
   server - never use `/reload`.
3. On first start UniversalAdmin creates `plugins/UniversalAdmin/` with
   `config.yml`, `lang/en_US.yml`, `lang/de_DE.yml` and the SQLite database.
4. Join as an operator and run `/admin`. Hand out permissions to your team -
   templates: [permissions.md](permissions.md#role-templates).

Optional:

- MySQL/MariaDB instead of SQLite: [database.md](database.md).
- German texts: `general.language: de_DE` (in-game: `/admin` → Settings →
  general), then `/admin reload`.
- Discord logging: install the official extension, see
  [docs/extensions/discord.md](../extensions/discord.md).

## Updating

- `/admin update` downloads the newest stable release into
  `plugins/update/`; the server applies it on the next restart. Staff with
  `universaladmin.update.notify` are told when a new version is out.
- Or replace the jar by hand and restart.

Configuration, translations and the database are migrated automatically -
nothing you configured is overwritten. Read the "Upgrading" notes in the
[changelog](../../CHANGELOG.md) before a major version.

## Building it yourself

```bash
./gradlew build
```

The plugin is `build/libs/universaladmin-core-<version>.jar` (database
drivers included); the Discord extension is in `extensions/discord/build/libs/`.
See [docs/development/setup.md](../development/setup.md).

## Uninstalling

Remove the jar from `plugins/` and restart. Your data
(`plugins/UniversalAdmin/data.db` for SQLite, or the MySQL/MariaDB database)
is kept and not deleted automatically.
