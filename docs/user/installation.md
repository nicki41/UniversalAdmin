# Installation

> UniversalAdmin ist aktuell im Alpha-Stadium (siehe [ROADMAP.md](../../ROADMAP.md))
> - noch kein offizielles Release-Artefakt auf Modrinth/Hangar, diese
> Anleitung beschreibt den Ablauf mit einer selbst gebauten jar.

## Voraussetzungen

- Ein Paper-Server (Version passend zur in `build.gradle.kts` referenzierten
  Paper-API-Version).
- Java 25 auf dem Server.

## Schritte

1. Plugin bauen (siehe [docs/development/setup.md](../development/setup.md)):
   ```bash
   ./gradlew build
   ```
2. `build/libs/universaladmin-core-<version>.jar` in den `plugins/`-Ordner
   des Servers kopieren.
3. Server starten. Beim ersten Start werden `config.yml` und
   `lang/en_US.yml`/`lang/de_DE.yml` in `plugins/UniversalAdmin/` angelegt
   (siehe [configuration.md](configuration.md)).
4. Standardmäßig läuft UniversalAdmin sofort mit SQLite - keine weitere
   Einrichtung nötig. Für MySQL/MariaDB siehe [configuration.md](configuration.md).

## Deinstallation

Plugin-Jar aus `plugins/` entfernen, Server neu starten. Die Datenbank
(`plugins/UniversalAdmin/data.db` bei SQLite, bzw. die konfigurierte
MySQL/MariaDB-Datenbank) bleibt erhalten und wird nicht automatisch
gelöscht.
