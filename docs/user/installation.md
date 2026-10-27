# Installation

## Supported versions

| UniversalAdmin | Server | Java |
|---|---|---|
| 1.1.x | **Paper 1.21.7 - 1.21.11, 26.1.1 - 26.3** (and Purpur on those) | 21+ (26.x needs 25, like Paper itself) |

One jar for all of them. It is Java 21 bytecode with `api-version: '1.21.7'`
and only uses API that exists on every listed version (the build compiles
against the oldest and the newest). Every version is boot-tested with a
smoke test before it is listed. The exact list is
[`supported-versions.txt`](../../supported-versions.txt) - Modrinth shows the
same.

**New Paper versions** are picked up automatically: a weekly workflow tests
every new Paper version (no pre-releases or release candidates) and, if UniversalAdmin runs on it, adds it to
the current release on Modrinth right away (see
[docs/release/releasing.md](../release/releasing.md#compatibility)).

Below 1.21.7 the dialog API the menus' text input uses does not exist, so
older servers refuse to load the plugin. Spigot, CraftBukkit and Folia are
not supported.

Networks (Velocity, BungeeCord): install UniversalAdmin on every server -
see [network.md](network.md).

## Install

1. Download `universaladmin-core-<version>.jar` from
   [Modrinth](https://modrinth.com/plugin/universaladmin) or the
   [GitHub releases](https://github.com/universaladmin-mc/UniversalAdmin/releases)
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
- A proxy network: [network.md](network.md).

## Updating

- **Automatically:** every 6 hours UniversalAdmin checks GitHub for new
  releases - of itself and of the official extensions - and downloads a new
  one into `plugins/update/`; the server applies it at the next restart.
  Only within the same major version (`2.0` waits for you), and only with
  `update.auto-download: true` (the default). Staff with
  `universaladmin.update.notify` are told - right away and when they join -
  and so is the console.
- `/admin update` downloads every newer release right now, a new major
  version included.
- Or replace the jar by hand and restart.
- The Velocity and BungeeCord plugins only tell their console about a new
  version - replace their jar yourself and restart the proxy.

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
