# Roadmap

Grober Ausbauplan, kein Sprint-Plan mit Datum. Reihenfolge innerhalb einer
Phase ist eine Empfehlung, keine harte Vorgabe. Jede Phase baut auf der in
[ARCHITECTURE.md](ARCHITECTURE.md) festgelegten Struktur auf - nicht um sie
herum.

## Phase 0 - Architektur & Scaffolding

- [x] Package-Struktur, Kernabstraktionen (`Module`, `Action`, `GuiPage`,
      `Repository`, `Migration`, Registries)
- [x] Gradle-Setup (Java-25-Toolchain, Shadow-Plugin, SQLite/MySQL-Treiber)
- [x] Plugin-Bootstrap, das kompiliert und startet
- [x] Acht Modul-Skelette, `Players` als vollständige Referenzimplementierung
- [x] Test-Infrastruktur (JUnit 5, In-Memory-Fakes, echte SQLite-Migrationstests)
- [x] Grunddokumentation (dieses Dokument, ARCHITECTURE.md,
      docs/development/architecture-rules.md, ADRs)
- [x] Core-Bootstrap-Lifecycle (`onLoad`/`onEnable`/`onDisable`, kritische
      vs. isolierte Fehlerbehandlung) und internes Modul-System
      (`ModuleRegistry`/`ModuleManager`, Lifecycle-States, Abhängigkeits-
      auflösung, `ModuleResources`-Cleanup) - siehe
      [docs/architecture/modules.md](docs/architecture/modules.md)
- [x] `/admin`-Statuscommand (Aliase `/ua`, `/uadmin`), `PluginStatus`-Snapshot
- [x] Zentrale Action-Autorisierung (`ActionDefinition`/`ActionExecutor`):
      Permission-/Feature-enabled-/Self-Target-/Input-Validierung vor jeder
      Action, `PermissionEvaluator` als zentraler Permission-Resolver
      (`Actor`-getragen statt verstreutem `hasPermission(...)`),
      `ActionEvent`s (`Executing`/`Executed`/`Failed`), Undo-Vertrag
      (`ReversibleAction`), Audit-Hook - siehe
      [docs/architecture/actions.md](docs/architecture/actions.md)
- [x] Typisiertes Settings-System (`SettingKey`/`SettingDefinition`/
      `SettingRegistry`/`SettingsService`, Namespacing für Core/Module/
      künftige Extensions, Validierung mit Fallback statt Crash), voller
      `config.yml` (general/database/gui/audit/modules/performance/
      maintenance/web), Config-Versionierung (`config-version` +
      `ConfigMigrationRunner`), sicherer `/admin reload` - siehe
      [docs/development/settings.md](docs/development/settings.md)
- [x] Mehrsprachiges Message-System (`en_US`/`de_DE`, Fallback-Kette,
      Parameter-Substitution, MiniMessage-Rendering für Ingame-Ausgabe) -
      siehe [docs/user/configuration.md](docs/user/configuration.md#localization)

- [x] Wiederverwendbares Ingame-GUI-Framework (`AbstractGuiPage`/
      `AbstractListGuiPage`, Sessions, Pagination, Permission-gesteuerte
      Sichtbarkeit, async Laden, Confirmation-/Selection-Dialoge,
      Texteingabe über die Paper-Dialog-API) plus Hauptmenü-Skelett mit
      einer Platzhalterseite pro eingebautem Modul - siehe
      [docs/development/gui-framework.md](docs/development/gui-framework.md)
- [x] Zentrales Audit-System: volles `AuditEvent` (Actor/Action/Modul/
      Target/Source/Erfolg/Grund/Alt-Neuwert/Welt-Position/Metadata/
      Correlation-ID), automatisch von `ActionExecutor` befüllt statt
      Feature-spezifischem Logging; `AuditSchemaMigrationV2` + gefiltertes,
      paginiertes `AuditService#query`; funktionierende Audit-Log-GUI
      (Liste mit Erfolg/Fehlschlag-Filter, Detailseite); stündliche,
      konfigurierbare Retention (`audit.retention-days`) - siehe
      [docs/user/audit-log.md](docs/user/audit-log.md). Damit ist der
      "Audit-Log-GUI/-Commands"-Punkt aus Phase 2 unten für die GUI-Seite
      bereits erledigt; Commands folgen bei Bedarf später.

## Phase 1 - Players & Moderation nutzbar machen

- [x] `PlayerJoinEvent`/`PlayerQuitEvent`-Listener, der nur
      `PlayerService.getOrCreateProfile` aufruft (kein Logic-Leck in den
      Listener) - siehe `PlayerActivityListener`
- [x] Punishment-Repository + -Service für Moderation
      (kick/ban/tempban/ipban/mute/tempmute/warn/unban/unmute/removewarn),
      inklusive Migration, Join-/Chat-Enforcement und GUI-Wizard, siehe
      [docs/user/modules/moderation.md](docs/user/modules/moderation.md)
- [x] Erste echte GUI-Page (Player-Liste), die die
      `core:players.home`-Platzhalterseite ersetzt - ausgebaut zum vollen
      Player-Browser/Profil/Actions/Inventar-Editor, siehe
      [docs/user/modules/players.md](docs/user/modules/players.md)
- [ ] Erste echte Subcommands unter `/admin players`, `/admin moderation`
      (die Players- und Moderation-Actions aus diesem Release sind bereits
      command-fähig, da sie über `ActionExecutor` laufen - nur der
      Command-Frontend-Teil fehlt noch)
- [x] `/admin server broadcast|shutdown|restart|cancel` (Console-Pfad zu den
      Server-Permissions, da Console kein GUI hat - siehe
      [docs/user/modules/server.md](docs/user/modules/server.md))
- [x] Audit-Log-Einträge für jede Moderationsaktion über `AuditService`
      (wie bei Players: jede mutierende Action auditiert automatisch über
      `ActionExecutor`)

## Phase 2 - Restliche Built-in-Module

- [x] Server (Dashboard, Broadcasts, Maintenance-Mode, Shutdown/Restart mit
      Confirmation/Countdown - siehe
      [docs/user/modules/server.md](docs/user/modules/server.md); TPS/MSPT
      bleibt Aufgabe des Performance-Moduls weiter unten)
- [x] Worlds (Browser/Profil, Spawn/Time/Weather/Difficulty, Border,
      dynamisches Gamerule-GUI - siehe
      [docs/user/modules/worlds.md](docs/user/modules/worlds.md)). Welt
      laden/entladen war nicht Teil des aktuellen Auftrags und ist noch
      offen; delete/clone/reset bleiben bewusst außerhalb des Cores, siehe
      dessen "Dangerous Features"-Abschnitt.
- [x] Whitelist (natives Whitelist-Wrapping plus eigene Tabelle mit
      Grund/Notizen/Ablauf, Ownership-Modell, Join-Check + stündlicher
      Sweep - siehe [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md))
- [x] Performance (gecachtes TPS/MSPT/Memory/World/Entity-Sampling, Dashboard,
      World-Performance, Entity-Overview nach Typ/Welt, kurze In-Memory-
      Historie, Staff-Alerts bei Schwellenwerten, eng gefasstes Entity Clear
      mit Preview/Confirmation/Audit - siehe
      [docs/user/modules/performance.md](docs/user/modules/performance.md))
- [ ] Settings-GUI/-Commands über `SettingsService` (siehe
      [docs/development/settings.md](docs/development/settings.md) - das
      typisierte System existiert bereits, nur die GUI/Command-Oberfläche
      dafür fehlt noch)
- [x] Audit-Log-GUI über `AuditService` (siehe Phase 0) - `/admin audit`-Commands noch offen

## Phase 2.5 - Öffentliche Veröffentlichung

- [x] Öffentliches GitHub-Repository, Apache-2.0-Lizenz (`LICENSE`), siehe
      [docs/release/licensing.md](docs/release/licensing.md)
- [x] CI: Build und Tests auf jedem Push/PR gegen `main`
- [x] Automatisierte Releases: ein `v*`-Tag erzeugt Build, Tests,
      GitHub-Release, jar und SHA-256 - siehe
      [docs/release/releasing.md](docs/release/releasing.md)
- [x] Anonyme Nutzungsstatistik mit vollständiger Dokumentation und Opt-out
      (siehe [docs/user/telemetry.md](docs/user/telemetry.md)); es gibt noch
      keinen offiziellen Endpunkt, standardmäßig wird nichts gesendet
- [ ] Offizieller Telemetrie-Endpunkt (Backend) inklusive
      Datenschutzerklärung und Retention-Entscheidung
- [ ] Screenshots und erster Modrinth-Upload (Checkliste in
      [docs/release/modrinth.md](docs/release/modrinth.md))
- [ ] Erster getaggter Alpha-Release

## Phase 3 - Proxy-Support

- [ ] BungeeCord-/Velocity-Messaging-Kanal für serverübergreifende Aktionen
      (z. B. globaler Kick, geteilter Whitelist-Status)
- [ ] Klarziehen, was proxy-weit vs. pro Server konfiguriert wird

## Phase 4 - Öffentliche Extension-API (nächster Meilenstein)

- [ ] `universaladmin-api`-Gradle-Modul extrahieren (siehe
      [docs/architecture/decisions/0006-optional-web-architecture.md](docs/architecture/decisions/0006-optional-web-architecture.md))
- [ ] Stabiles, versioniertes Interface für alles, was in
      [docs/architecture/extensions-future.md](docs/architecture/extensions-future.md)
      aufgelistet ist (Module, GUI-Pages, Actions, Permissions, Migrationen, ...)
- [ ] Extension-Loader (eigene jars in `plugins/UniversalAdmin/extensions/`
      oder als Bukkit-Plugins mit `depend: [UniversalAdmin]` - Entscheidung
      offen, siehe extensions-future.md)
- [ ] `universaladmin-sdk` mit Beispiel-Extension und Doku

## Phase 5 - Community-/offizielle Extensions und Marketplace

- [ ] Erste offizielle Extensions als Machbarkeitsnachweis der API (z. B.
      Vault-Integration, Discord-Integration - siehe Aufgabenstellung für
      die volle Liste möglicher Extensions)
- [ ] Extension-Registry/-Verzeichnis (Format offen: einfache Liste vs.
      eigener Service)

## Phase 6 - Optionale Web-App

- [ ] `universaladmin-web`-Modul (siehe
      [docs/architecture/web-future.md](docs/architecture/web-future.md))
- [ ] REST-API über dieselben Services/Actions wie GUI und Commands
- [ ] WebSockets/Live-Updates für Dashboard-Widgets
- [ ] Web-seitige Authentifizierung (getrennt vom Minecraft-Account-System,
      Details offen)

## Bewusst zurückgestellt

Diese Punkte sind nicht vergessen, sondern bewusst nicht Teil der aktuellen
Phasen, weil sie erst mit realem Nutzungsdruck sinnvoll entschieden werden
können:

- Konkretes Extension-Verteilungsformat (Marktplatz? Reines GitHub-Listing?)
- Web-App-Framework-Wahl
- Ob/wie Folia-Unterstützung nötig wird
