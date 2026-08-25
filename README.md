# UniversalAdmin

[![Build](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml/badge.svg)](https://github.com/nicki41/UniversalAdmin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Eine universelle Admin-Plattform für Paper-Server - mit vollständigen
Ingame-GUIs, ohne Pflicht-Abhängigkeiten, auf einer Architektur, die auf
Erweiterbarkeit ausgelegt ist.

> **Status: Alpha (`0.1.0-alpha`).** Sechs der acht eingebauten Module
> (Players, Moderation, Server, Worlds, Whitelist, Performance) sind
> vollständig nutzbar - eigene GUI, Actions, Permissions, Audit-Einträge. Das
> Audit-Log-Modul hat Service und GUI, Settings bisher nur den Kernservice.
> Es gibt noch **keine** öffentliche Extension-API und keine Web-App - beides
> ist geplant, siehe [Roadmap](#roadmap). Auf Modrinth ist noch nichts
> veröffentlicht.

## Overview

Die meisten Admin-Plugins wachsen zu einer Sammlung unabhängiger
GUI-Listener und Commands, die jeweils ihre eigene Logik, ihr eigenes
Berechtigungssystem und oft ihr eigenes bisschen SQL mitbringen. Das lässt
sich weder testen noch später über eine Web-App oder eine Extension-API
wiederverwenden.

UniversalAdmin trennt die Business-Logik von der Oberfläche, die sie aufruft:

```
Frontend (GUI / Commands / später Web)
    ↓
Application Services
    ↓
Actions / Domain Logic
    ↓
Repositories / Server Adapters
    ↓
Paper / Datenbank
```

Jede mutierende Operation - einen Spieler kicken, eine Gamerule ändern, eine
gefilterte Menge Entities entfernen - läuft durch dieselbe Pipeline mit
Permission-Prüfung und automatischem Audit-Eintrag, egal ob sie aus der GUI,
einem Command oder später aus einer REST-API kommt. Kein Feature bringt seine
eigene Permission-Behandlung, sein eigenes SQL oder sein eigenes Audit-Logging
mit. Details: [ARCHITECTURE.md](ARCHITECTURE.md).

## Features

Alles hier Genannte ist implementiert und nutzbar - Geplantes steht unter
[Roadmap](#roadmap).

- **Players** - Spieler-Browser (online/offline/zuletzt gesehen/Suche),
  Profilseite, ~20 Actions (Teleport, Heilen, Effekte, Gamemode,
  Inventar-/Enderchest-Editor).
- **Moderation** - Kick, Ban, Tempban, IP-Ban, Mute, Tempmute, Warn, Unban,
  Unmute, Removewarn; Join- und Chat-Enforcement, GUI-Wizard.
- **Staff-Tools** - Vanish, Freeze, Godmode, No-Collision und ein Staff-Mode
  mit crash-sicherem Snapshot/Recovery (`/admin staff recover`).
- **Server** - Live-Dashboard, Broadcast (Chat/Title/Actionbar),
  Maintenance-Mode mit Allow-List, Shutdown/Restart mit Bestätigung und
  konfigurierbarem Countdown.
- **Worlds** - World-Browser und -Profil, Teleport/Spawn/Zeit/Wetter/
  Schwierigkeit, World-Border-Verwaltung, dynamisches Gamerule-GUI (liest die
  Gamerules zur Laufzeit, keine feste Liste).
- **Whitelist** - natives Whitelist-Wrapping plus eigene Metadaten (Grund,
  Notizen, Ablaufdatum), Ownership-Modell, Join-Check mit stündlichem Sweep.
- **Performance** - TPS/MSPT/Memory-Dashboard, Performance pro Welt,
  Entity-Übersicht nach Typ und Welt, kurze In-Memory-Historie, Staff-Alerts
  bei konfigurierbaren Schwellenwerten und ein bewusst eng gefasstes Entity
  Clear (nie Spieler, geschützte Typen, Preview, Bestätigung, auditiert) -
  ausdrücklich **kein** aggressiver Lag-Cleaner.
- **Audit Log** - jede Action wird automatisch auditiert; filterbare,
  paginierte Ingame-Ansicht mit Detailseite und konfigurierbarer Retention.
- **Settings** - typisiertes, validiertes Settings-System für die gesamte
  Plattform; ein ungültiger Wert in der `config.yml` fällt auf den Default
  zurück, statt den Server zu crashen. Eigene GUI/Commands stehen noch aus.
- **Datenbank** - SQLite (Standard, keine Einrichtung) oder MySQL/MariaDB;
  beide Treiber sind mitgeliefert, alle Zugriffe laufen asynchron.
- **Keine Pflicht-Abhängigkeiten** - kein Vault, kein LuckPerms, kein
  PlaceholderAPI, kein ProtocolLib nötig.
- **Zweisprachig** - `en_US` und `de_DE` von Haus aus; jeder sichtbare Text
  kommt aus `lang/<locale>.yml`.

## Requirements

- **Paper** in der Version, gegen die gebaut wird - aktuell Paper-API `26.2`
  (exakte Version: `build.gradle.kts`). Spigot/CraftBukkit werden nicht
  unterstützt.
- **Java 25** auf dem Server (dieselbe Version wie die Build-Toolchain).
- Optional: ein MySQL-/MariaDB-Server, wenn mehrere Server sich Daten teilen
  sollen. Ohne das läuft SQLite ohne jede Einrichtung.

## Installation

Fertige jars gibt es unter
[Releases](https://github.com/nicki41/UniversalAdmin/releases) (sobald der
erste Release getaggt ist). Die jar in den `plugins/`-Ordner legen und den
Server neu starten - fertig.

Selbst bauen:

```bash
./gradlew build
```

Das Ergebnis liegt unter `build/libs/universaladmin-core-<version>.jar` und
enthält die Datenbanktreiber bereits. Vollständige Anleitung inklusive
Deinstallation: [docs/user/installation.md](docs/user/installation.md).

## Quick Start

1. jar nach `plugins/` legen, Server starten.
2. Ingame `/admin` eingeben (Aliase: `/ua`, `/uadmin`). Das Hauptmenü zeigt
   jede aktive Modulseite.
3. Läuft sofort mit SQLite. Konfiguration danach in
   `plugins/UniversalAdmin/config.yml`, Änderungen mit `/admin reload`
   übernehmen.

| Command | Zweck |
|---|---|
| `/admin` | Hauptmenü (Konsole/Command-Block sehen stattdessen einen Statusreport) |
| `/admin reload` | Lädt UniversalAdmins eigene `config.yml` neu (nie Bukkits globales `/reload`) |
| `/admin server broadcast\|shutdown\|restart\|cancel ...` | Server-Steuerung von der Konsole aus |
| `/admin staff recover` | Manuelle Wiederherstellung eines hängen gebliebenen Staff-Mode-Snapshots |

Der überwiegende Teil der Funktionalität wird aktuell über die GUI bedient.
Die zugehörigen Actions laufen bereits über `ActionExecutor` und sind damit
command-fähig - nur die Command-Frontends fehlen noch (siehe
[ROADMAP.md](ROADMAP.md)).

## Database

SQLite ist der Standard und braucht keine Einrichtung; die Datei liegt im
Plugin-Ordner. MySQL/MariaDB ist optional und in `config.yml` unter
`database:` konfigurierbar. Alle Zugriffe laufen über `PreparedStatement`s
und asynchron, nie blockierend auf dem Paper-Main-Thread. Schemaänderungen
laufen über versionierte Migrationen. Details, Backup-Hinweise und
Sicherheitsaspekte: [docs/user/database.md](docs/user/database.md).

## Permissions

Jede geschützte Aktion hat einen eigenen Node der Form
`universaladmin.<modul>.<node>`, zur Laufzeit registriert (nicht statisch in
`plugin.yml`), kompatibel mit jedem Standard-Permission-Plugin. Alle Nodes
stehen standardmäßig auf `op`. Vollständige Liste:
[docs/user/permissions.md](docs/user/permissions.md).

## Anonymous Statistics

UniversalAdmin kann eine anonyme Nutzungsstatistik senden, um drei Fragen
beantworten zu können: **wie viele Installationen aktiv sind**, **wie viele
Spieler insgesamt online sind**, und **wie sich die Versionen verteilen**.

**Aktuell wird nichts gesendet.** Es gibt noch keinen offiziellen Endpunkt,
und es ist keiner voreingestellt (`telemetry.endpoint` ist leer) - ohne
Endpunkt wird kein Request gemacht, keine Installation-ID erzeugt und kein
Timer gestartet.

Wenn ein Endpunkt konfiguriert ist, besteht ein Heartbeat aus genau sechs
Feldern:

```json
{
  "installationId": "0123456789abcdef0123456789abcdef",
  "universalAdminVersion": "0.1.0-alpha",
  "minecraftVersion": "1.21.4",
  "javaMajorVersion": 25,
  "onlinePlayers": 17,
  "maxPlayers": 100
}
```

Ausdrücklich **nie** übertragen werden: Server-IP, Hostname, Domain, Port,
Spielernamen, Spieler-UUIDs, Spieler-IPs, Chat, Commands, Weltnamen,
Koordinaten, andere installierte Plugins, Datei- oder Datenbankinhalte,
Hardware-Merkmale, MAC-Adressen, OS-Benutzername oder Dateipfade. Die
Installation-ID ist reiner Zufall (128 Bit) und aus nichts abgeleitet - kein
Hardware-Fingerprint. Die Spielerzahl ist ausschließlich eine Zahl.

Vollständig abschalten:

```yaml
telemetry:
  enabled: false
```

Dann wird gar kein Request ausgeführt - es gibt keinen versteckten Fallback
und keine "essenzielle" zweite Übertragung. Die vollständige Dokumentation
(jedes Feld, Intervall, Fehlerverhalten, offene Punkte) steht in
[docs/user/telemetry.md](docs/user/telemetry.md). Es wird nichts erhoben, was
dort nicht dokumentiert ist.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Architekturüberblick und -prinzipien
- [ROADMAP.md](ROADMAP.md) - was existiert, was als Nächstes kommt
- [CHANGELOG.md](CHANGELOG.md) - Änderungshistorie
- [docs/user/](docs/user/) - Installation, Konfiguration, Permissions,
  Datenbank, Audit-Log, [Telemetrie](docs/user/telemetry.md), Module
- [docs/development/](docs/development/) - Setup, Konventionen, Tests, neues
  Modul hinzufügen, GUI-Framework, Settings-System und die verbindlichen
  [Entwicklungsregeln](docs/development/architecture-rules.md)
- [docs/architecture/](docs/architecture/) - vertiefende Architekturdokus und
  [ADRs](docs/architecture/decisions/)
- [docs/release/](docs/release/) - [Release-Prozess](docs/release/releasing.md),
  [Lizenzierung](docs/release/licensing.md), Modrinth-Vorbereitung

Modul-Dokumentation:
[Players](docs/user/modules/players.md) ·
[Moderation](docs/user/modules/moderation.md) ·
[Staff-Tools](docs/user/modules/staff-tools.md) ·
[Server](docs/user/modules/server.md) ·
[Worlds](docs/user/modules/worlds.md) ·
[Whitelist](docs/user/modules/whitelist.md) ·
[Performance](docs/user/modules/performance.md) ·
[Audit Log](docs/user/audit-log.md)

## Extensions

**Geplant, noch nicht verfügbar.** Es gibt heute keine öffentliche,
versionierte Extension-API - Extensions von Dritten lassen sich also noch
nicht installieren.

Der Core ist aber darauf ausgelegt: Alle eingebauten Module nutzen exakt
dieselben Abstraktionen (`Module`, `Action`, `GuiPage`, `Repository`,
`Migration`, `PermissionRegistry`), die später auch externen Extensions
offenstehen sollen. Es gibt bewusst keinen "Schnellweg" für Built-ins, den
eine Extension nicht auch gehen könnte - siehe
[ADR 0005](docs/architecture/decisions/0005-extension-ready-design.md) und
[docs/architecture/extensions-future.md](docs/architecture/extensions-future.md).

Die öffentliche Extension-API ist der **nächste große Meilenstein**.

## Web App

**Geplant, noch nicht verfügbar.** Eine optionale Web-App mit REST-API über
denselben Services/Actions wie GUI und Commands ist vorgesehen, aber bewusst
zurückgestellt, bis der Core stabil ist - siehe
[docs/architecture/web-future.md](docs/architecture/web-future.md). Der
Schlüssel `web.enabled` existiert bereits als reservierte, aktuell
wirkungslose Einstellung.

## Roadmap

| Phase | Inhalt | Status |
|---|---|---|
| Core | Architektur, Module, GUI, Actions, Audit, Settings, Storage | weitgehend fertig |
| Command-Frontends | `/admin players`, `/admin moderation`, ... | offen |
| **Extension API** | `universaladmin-api`, Extension-Loader, SDK | **nächster Meilenstein** |
| Offizielle Extensions | z. B. Vault-, Discord-Integration | offen |
| Marketplace | Verteilung von Extensions | offen, Format noch nicht entschieden |
| Web-App | REST-API, WebSockets, Dashboard | offen |
| Multi-Server | Proxy-Support (BungeeCord/Velocity) | offen |

Ausführlich mit allen Einzelpunkten: [ROADMAP.md](ROADMAP.md).

## Contributing

Beiträge sind willkommen. Ablauf, Setup, Architekturregeln und
Test-Erwartungen: [CONTRIBUTING.md](CONTRIBUTING.md).

Bug-Reports und Feature-Requests bitte als
[GitHub Issue](https://github.com/nicki41/UniversalAdmin/issues).

## Security

Sicherheitslücken **nicht** als öffentliches Issue melden - siehe
[SECURITY.md](SECURITY.md) für den Meldeweg.

## License

Apache License 2.0 - siehe [LICENSE](LICENSE). Kommerzielle Nutzung ist
erlaubt. Was das für die geplante Extension-API, Community-Extensions und ein
mögliches Marketplace-Backend bedeutet, steht in
[docs/release/licensing.md](docs/release/licensing.md) (kein Rechtsrat).
