# Installation

> UniversalAdmin is currently in alpha (see [ROADMAP.md](../../ROADMAP.md))
> - there's no official release artifact on Modrinth/Hangar yet, this guide
> describes the process with a self-built jar.

## Requirements

- A Paper server (version matching the Paper API version referenced in
  `build.gradle.kts`).
- Java 25 on the server.

## Steps

1. Build the plugin (see [docs/development/setup.md](../development/setup.md)):
   ```bash
   ./gradlew build
   ```
2. Copy `build/libs/universaladmin-core-<version>.jar` into the server's
   `plugins/` folder.
3. Start the server. On first start, `config.yml` and
   `lang/en_US.yml`/`lang/de_DE.yml` are created in
   `plugins/UniversalAdmin/` (see [configuration.md](configuration.md)).
4. By default, UniversalAdmin runs immediately on SQLite - no further
   setup needed. For MySQL/MariaDB, see [configuration.md](configuration.md).

## Uninstallation

Remove the plugin jar from `plugins/`, restart the server. The database
(`plugins/UniversalAdmin/data.db` for SQLite, or the configured
MySQL/MariaDB database) is preserved and not automatically deleted.
