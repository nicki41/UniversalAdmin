# Changelog

Format angelehnt an [Keep a Changelog](https://keepachangelog.com/).
Solange es keinen veröffentlichten Release gibt, wird alles unter
`[Unreleased]` gesammelt.

## [Unreleased]

### Added

- **Anonyme Nutzungsstatistik** (`dev.universaladmin.telemetry`):
  `InstallationIdentity`/`InstallationIdentityStore` (128 Bit aus
  `SecureRandom`, aus nichts abgeleitet, persistiert in
  `installation-id.yml`), `TelemetryPayload` (genau sechs Felder),
  `TelemetryClient` mit `HttpTelemetryClient` (JDK-HTTP-Client, kurze
  Timeouts, keine Redirects, Antwort wird verworfen) und
  `NoOpTelemetryClient`, `TelemetryService` (Enabled-Check bei jedem
  Heartbeat, Spielerzahlen auf dem Main-Thread, Request im Hintergrund,
  Fehler werden geschluckt und nur einmal pro Lauf gewarnt),
  `TelemetryScheduler` (verzögerter Start, Intervall mit Jitter, eigener
  Daemon-Thread) und `TelemetryBootstrap` (Verdrahtung mit drei Ausgängen:
  aus / kein Endpunkt / aktiv). Neue Einstellungen `telemetry.enabled`
  (live umschaltbar), `telemetry.endpoint` (leer voreingestellt - es wird
  standardmäßig **nichts** gesendet) und `telemetry.interval`. Vollständige
  Dokumentation inklusive aller nicht erhobenen Daten:
  [docs/user/telemetry.md](docs/user/telemetry.md).
- Tests für die Telemetrie ohne jeden echten Netzwerk-Request:
  ID-Erzeugung/-Persistenz, "deaktiviert heißt null Requests", Payload ohne
  Spieleridentitäten, Serialisierung, Fehlschlag des Endpunkts ohne
  Auswirkung, Jitter-Grenzen, Scheduler-Cleanup.
- `LICENSE` (Apache-2.0, unveränderter offizieller Lizenztext) und eine
  Lizenzentscheidung samt Begründung in
  [docs/release/licensing.md](docs/release/licensing.md).
- `docs/development/architecture-rules.md` - die verbindlichen Architektur-,
  Package-, Threading-, Settings-, Modul-, Sicherheits-, Dependency- und
  Testregeln als reguläre Entwicklerdokumentation.
- `docs/release/releasing.md` - reproduzierbarer Release-Prozess
  (Version → CHANGELOG → Build → Commit → Tag → Push).
- `.github/workflows/release.yml` - ein Tag `v*` erzeugt automatisch einen
  GitHub-Release: Tag-gegen-Projektversion-Prüfung, Build inklusive Tests,
  shaded jar plus SHA-256-Datei als Assets, Prerelease-Erkennung bei
  `-alpha`/`-beta`/`-rc`. `contents: write`, keine zusätzlichen Secrets.
- Gradle-Task `printVersion` als einzige Quelle der Projektversion für die
  CI (Tag-Prüfung, Artifact-Benennung).
- Issue-Formulare (`bug_report.yml`, `feature_request.yml`) und
  `ISSUE_TEMPLATE/config.yml` mit einem Verweis auf Private Vulnerability
  Reporting statt öffentlicher Security-Issues.

- Projektarchitektur festgelegt und dokumentiert (siehe ARCHITECTURE.md,
  docs/architecture/, ADRs unter docs/architecture/decisions/).
- Gradle-Setup (Java-25-Toolchain, Shadow-Plugin, SQLite/MySQL-Treiber,
  JUnit 5).
- Plugin-Bootstrap (`UniversalAdminPlugin`) mit manuellem Dependency-Wiring.
- Kernabstraktionen: `Module`, `Action`/`ActionResult`, `GuiPage`,
  `Repository`/`Migration`, `PermissionRegistry`, `ServiceRegistry`,
  `AuditService`, `MessageService`, `NotificationService`, `TaskScheduler`.
- Acht Modul-Skelette: Players, Moderation, Server, Worlds, Whitelist,
  Performance, Audit Log, Settings. `Players` als vollständige
  Referenzimplementierung (Repository → Service → Action, inkl. Migration).
- Test-Infrastruktur mit Beispieltests (Registry, Permission-Validierung,
  Service mit In-Memory-Fake, Migrationen gegen echte SQLite-Datenbank).

- Core-Bootstrap-Lifecycle (`onLoad`/`onEnable`/`onDisable`) mit klarer
  Trennung zwischen kritischen Core-Komponenten (Config, Scheduler,
  Storage/Migrationen, geteilte Registries - Fehler brechen den gesamten
  Plugin-Start ab) und einzelnen Modulen (Fehler werden isoliert, siehe
  `ModuleManager`).
- Internes Modul-Lifecycle-System: `ModuleDescriptor` (Metadaten inkl.
  Abhängigkeiten, Settings-/Permission-Namespace, optionales GUI-Icon-
  Placeholder), `ModuleState` (`DISCOVERED`/`LOADED`/`ENABLED`/`DISABLED`/
  `FAILED`), `ModuleRegistry` (Zustands-Tracking), `ModuleManager`
  (Abhängigkeitssortierung, Fehlerisolierung), `ModuleResources`
  (automatische Freigabe von Listenern/Tasks/Registry-Einträgen beim
  Disable). Siehe docs/architecture/modules.md.
- `PluginStatus`/`ComponentStatus` - Live-Snapshot aus Version, Uptime,
  aktiven/fehlgeschlagenen Modulen, grobem Datenbank-/Web-Status.
- `/admin`-Command (Aliase `/ua`, `/uadmin`) als Statusplatzhalter, rendert
  `PluginStatus`.
- `unregister(...)` auf `ActionRegistry`/`GuiRegistry`/`PermissionRegistry`
  für Modul-Disable-Cleanup.
- Tests für das Modul-Lifecycle-System (`ModuleManagerTest`): Registrierung,
  doppelte Modul-ID, Lifecycle-Reihenfolge, Fehlerisolierung, Disable-
  Cleanup, Abhängigkeitsreihenfolge, Zyklus-Erkennung, fehlende
  Abhängigkeit.

- Typisiertes Settings-System (`dev.universaladmin.settings`):
  `SettingKey<T>`/`SettingType<T>`/`SettingValidator<T>`/`SettingDefinition<T>`/
  `SettingValue<T>`/`SettingRegistry`/`SettingsService`, mit eingebauten
  Typen für String/boolean/int/long/double/Duration/Enum/String-Liste,
  Validatoren für Min/Max/Regex/Vielfaches, und Namespacing zwischen
  Core-Settings und künftigen Modul-/Extension-Settings. Siehe
  docs/development/settings.md und ADR 0007.
- `CoreSettings` - registriert den vollständigen `config.yml`-Baum
  (`general`, `database`, `gui`, `audit`, `modules`, `performance`,
  `maintenance`, `web`); `modules.*` steuert jetzt tatsächlich, ob ein
  Built-in-Modul überhaupt registriert wird.
- `config.yml`-Versionierung: `config-version`, `ConfigMigration`/
  `ConfigMigrationRunner` (`dev.universaladmin.config`) - eine bestehende
  Nutzer-Config wird bei künftigen Schema-Änderungen migriert statt
  überschrieben.
- Sicherer `/admin reload` (`ReloadConfigAction`, Permission
  `universaladmin.reload`) - liest ausschließlich UniversalAdmins eigene
  `config.yml` neu, niemals Bukkits globales `/reload`. Restart-required-
  Settings werden bei Änderung als "pending restart" gemeldet statt live
  angewendet.
- Mehrsprachiges Message-System: `lang/en_US.yml` (Default/Fallback) und
  `lang/de_DE.yml`, `YamlLocaleMessageService` mit Fallback-Kette (aktive
  Locale → `en_US` → sichtbarer `[missing: ...]`-Marker mit einmaligem
  Debug-Log), `ComponentMessages` als MiniMessage-Renderer für die
  Ingame-Ausgabe getrennt von der reinen String-Auflösung (Web-Wiederverwendbarkeit).
- Tests für das Settings-/Localization-System: Defaults, ungültige Werte
  (Parse-/Validierungsfehler → Fallback statt Crash), Enum-Parsing,
  Duration-Parsing, doppelte Setting-Keys, Locale-Fallback, Parameter-
  Interpolation.
- `DatabaseHealth` (`DISCONNECTED`/`CONNECTING`/`READY`/`FAILED`), getrackt
  von `StorageService` und in `UniversalAdmin#status()` auf das bestehende
  `ComponentStatus` abgebildet - siehe docs/architecture/storage.md#health.
- SQLite-Pragmas in `DataSourceFactory` (`journal_mode=WAL`,
  `synchronous=NORMAL`, `foreign_keys=on`, `busy_timeout=5000`).
- `Transactions` (`dev.universaladmin.storage`) - Helper für Repository-
  Methoden, die mehrere Statements atomar (Commit/Rollback) über eine
  Connection ausführen müssen.
- `StorageException` - generischer Unchecked-Wrapper für `SQLException` in
  der Storage-Foundation selbst (`StorageService`, `Transactions`).
- `docs/user/database.md` - Nutzerdokumentation SQLite vs. MySQL/MariaDB
  (wann welches, MySQL-User anlegen, Verhalten bei Startfehler, Backups).
- Tests: `StorageServiceTest` (SQLite-Init, Health-Zustände, Fehlschlag bei
  ungültigem Pfad), `TransactionsTest` (Commit, Rollback bei Fehler),
  `JdbcPlayerProfileRepositoryTest` (Repository-Foundation Ende-zu-Ende
  gegen echte SQLite-Datenbank), Datenbanktyp-Parsing in
  `SettingTypesTest`, explizite "kein doppelter Migrationslauf"-Assertion
  in `MigrationRunnerTest`.
- Wiederverwendbares Ingame-GUI-Framework (`dev.universaladmin.gui`):
  `AbstractGuiPage`/`AbstractListGuiPage` (Navigation, Permission-
  gesteuerte Sichtbarkeit, Pagination, async Laden mit Loading-/Empty-/
  Error-Zustand), `GuiSession`/`GuiSessionManager` (Pro-Spieler-Zustand
  ohne gehaltene `Player`-Referenzen, Cleanup bei Disconnect/echtem
  Inventory-Close), `GuiListener` als einziger, zentral registrierter
  Klick-/Drag-Handler, `GuiLayout`/`Pagination` als reine, getestete
  Slot-/Paginierungslogik, `IconProvider`/`MaterialIconProvider` als
  zentrale Material-Auflösung, `ConfirmationDialog` (Danger-Level NORMAL/
  WARNING/DANGEROUS), `SelectionDialog`, `GuiTextInput` (Freitext-/
  Such-Flow über die Paper-Dialog-API statt Packet-Hacks/Anvil/Sign) -
  siehe docs/development/gui-framework.md.
- `MainMenuPage`: `/admin`-Hauptmenü mit einem Button pro eingebautem
  Modul, gefiltert nach tatsächlichem `ModuleState.ENABLED` und
  Berechtigung; `PlaceholderGuiPage` als "noch nicht gebaut"-Zielseite
  pro Modul. Neue Permission `universaladmin.menu.open`.
- `ModuleDescriptor.icon()` für alle acht eingebauten Module gesetzt
  (vorher überall `null`, siehe `GuiIcon`).
- Tests für das GUI-Framework: `PaginationTest`, `GuiLayoutTest`,
  `GuiSessionTest`, `GuiSessionManagerTest`, `GuiButtonVisibilityTest`,
  `GuiClickContextTest` (Navigationsstack), `GuiListenerTest`
  (Click-Cancel/Dispatch, Close-/Quit-Cleanup).

### Changed

- Die verbindlichen Entwicklungsregeln liegen jetzt unter
  `docs/development/architecture-rules.md`; sämtliche Verweise in Quellcode
  und Dokumentation zeigen dorthin.
- `README.md` auf den öffentlichen Stand gebracht (Status, Features,
  Requirements, Quick Start, Datenbank, Permissions, anonyme Statistik,
  Extensions/Web-App ausdrücklich als geplant markiert, Lizenz).
- `CONTRIBUTING.md` und `SECURITY.md` überarbeitet: Setup, Architektur,
  Tests, Stil, PR-Ablauf bzw. Private Vulnerability Reporting,
  Telemetrie-/Datenschutzabschnitt und unterstützte Versionen.
- `.github/workflows/build.yml` läuft auf `push`/`pull_request` gegen `main`,
  installiert JDK 25 (Build-Toolchain) und 21 (führt Gradle aus), lädt die
  jar unter einem versionierten Artifact-Namen hoch.
- PR-Vorlage um Checklistenpunkte für Secrets, Lokalisierung, Threading und
  Telemetrie-Dokumentation erweitert.
- `.gitignore` um Build-/IDE-/Runtime-/Datenbank-/Secret-Muster sowie rein
  lokale Notizdateien ergänzt.
- Ziel-Paper-Version auf 26.2 (`paper-api:26.2.build.115-stable`) gesetzt.
  Paper 26.2 ist ausschließlich für JVM 25+ veröffentlicht, daher wurde die
  Java-Toolchain von der ursprünglich geplanten Version 21 auf 25
  angehoben - siehe `build.gradle.kts` und `plugin.yml`
  (`api-version: '26.2'`).
- `Module` ist jetzt zweiphasig (`onLoad` dann `onEnable`) statt nur
  `onEnable`; `id()`/`displayName()` sind durch `descriptor()`
  (`ModuleDescriptor`) ersetzt. Alle acht Built-in-Module angepasst.
- `UniversalAdminCommand` zeigt jetzt einen `PluginStatus`-Report statt
  nur der Liste geladener Module, und wird unter `/admin` statt `/ua`
  registriert.
- `ConfigService`/`YamlConfigService` entfernt, ersetzt durch
  `SettingsService`/`YamlSettingsService` (siehe oben).
  `UniversalAdmin.config()` ist jetzt `UniversalAdmin.settings()`
  (`SettingsService`) plus `UniversalAdmin.settingRegistry()`
  (`SettingRegistry`).
- `messages_en_US.yml` (Datei im Plugin-Root) ersetzt durch
  `lang/en_US.yml`/`lang/de_DE.yml`; `YamlMessageService` (eine feste
  Locale) ersetzt durch `YamlLocaleMessageService` (mehrere Locales,
  Fallback-Kette, liest die aktive Locale live aus `general.language`).
- `/admin` (ohne Argument) öffnet für einen Spieler jetzt `MainMenuPage`
  statt des Text-Statusberichts; für Konsole/Command-Block bleibt der
  Statusbericht unverändert. `/admin reload` unverändert.
  `UniversalAdmin`s Konstruktor hat ein neues Pflichtfeld `GuiFramework`
  (`UniversalAdmin#guiFramework()`).

- **Players** vollständig: Player-Browser-GUI (online/offline/zuletzt
  gesehen/Suche), Profilseite, ~20 Actions (Teleport, Heilen, Effekte,
  Gamemode, Inventar-/Enderchest-Editor), feingranulare Permissions, Audit.
  Siehe [docs/user/modules/players.md](docs/user/modules/players.md).
- **Moderation** vollständig: Punishment-Repository/-Service (kick/ban/
  tempban/ipban/mute/tempmute/warn/freeze/unban/unmute/removewarn/
  unfreeze), Join-/Chat-Enforcement, GUI-Wizard, feingranulare Permissions,
  Audit, plus Vanish/Godmode/No-Collision/Staff-Mode (crash-sicheres
  Snapshot/Recovery, `/admin staff recover`). Siehe
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md) und
  [docs/user/modules/staff-tools.md](docs/user/modules/staff-tools.md).
- **Server** vollständig: Live-Dashboard (Version/Uptime/Spieler/Memory/
  CPU/DB-Status/Module), Broadcast (Message/Title/Actionbar), eigenes
  Maintenance-Mode-System (Repository/Service, Join-Enforcement,
  Allow-List), Shutdown/Restart mit Dangerous-Confirmation und
  konfigurierbarem Countdown + Cancel, `/admin server ...`, feingranulare
  Permissions, Audit. Siehe [docs/user/modules/server.md](docs/user/modules/server.md).
- **Worlds** vollständig: World-Browser/-Profil (Environment/Seed/Spawn/
  Border/Players/Chunks/Entities/Time/Weather/Difficulty),
  Teleport/Spawn/Time/Weather/Difficulty-Actions, World-Border-Verwaltung,
  dynamisches Gamerule-GUI (liest `World#getGameRules()` zur Laufzeit),
  feingranulare Permissions (Seed separat), Audit. Siehe
  [docs/user/modules/worlds.md](docs/user/modules/worlds.md).
- **Whitelist** vollständig: natives Whitelist-Wrapping (enable/disable/
  list/add/remove) plus eigene Metadaten (added-by/at, Grund, Notizen,
  Ablauf), befristete Einträge mit Join-Check und stündlichem Sweep,
  striktes Ownership-Modell, feingranulare Permissions, Audit. Siehe
  [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md).
- **Audit Log** vollständig (GUI-Seite): volles `AuditEvent` (Actor/Action/
  Modul/Target/Source/Erfolg/Grund/Alt-Neuwert/Welt-Position/Metadata/
  Correlation-ID), automatisch von `ActionExecutor` befüllt, gefiltertes,
  paginiertes `AuditService#query`, funktionierende GUI (Liste mit
  Erfolg/Fehlschlag-Filter, Detailseite), stündliche konfigurierbare
  Retention. `/admin audit`-Commands noch offen. Siehe
  [docs/user/audit-log.md](docs/user/audit-log.md).
- **Performance** vollständig: gecachtes TPS/MSPT/Memory/World/Entity-
  Sampling auf konfigurierbarem Intervall (nie pro GUI-Render neu
  berechnet), Dashboard, World-Performance-Ansicht, Entity-Overview (nach
  Typ/Welt gruppiert), kurze In-Memory-Historie, Staff-Alerts bei TPS/MSPT/
  Memory-Schwellenwerten, eng gefasstes Entity Clear (nie Spieler,
  konfigurierbare geschützte Typen, Preview, Confirmation, Audit),
  feingranulare Permissions. Siehe
  [docs/user/modules/performance.md](docs/user/modules/performance.md).

### Fixed

- **Kritisch: sämtliche Modul-eigenen Tabellen wurden nie angelegt**
  (`player_profiles`, `punishments`, `server_maintenance_state`,
  `whitelist_entries`, `vanish_state`, ...) - `UniversalAdminPlugin` rief
  `storage.migrations().runPending()` nur einmal auf, bevor überhaupt ein
  Modul enabled wurde. Jedes Modul registriert seine eigene(n) Migration(en)
  aber erst *während* seines eigenen `onEnable` - ohne einen zweiten
  `runPending()`-Aufruf danach liefen diese Migrationen nie. Betraf jedes
  Modul außer Audit Log (dessen zwei Migrationen vor dem ersten, einzigen
  Aufruf registriert wurden) - u. a. Ursache für "Storage Error" beim
  Moderation-/Punishment-Modul, "unexpected error" im Maintenance-Mode, und
  fehlerhafte Whitelist-/Vanish-Zustände beim Join. Siehe
  [docs/architecture/threading.md](docs/architecture/threading.md).
- `worlds.gui.action` existierte zweimal als Geschwister-Key in
  `lang/en_US.yml`/`de_DE.yml` (einmal Erfolg/Fehler-Meldungen, einmal
  Button-Labels) - YAML erlaubt das nicht wirklich, Bukkits Loader nimmt
  stillschweigend den letzten Eintrag ("duplicate keys found"-Warnung beim
  Start). Button-Labels nach `worlds.gui.buttons` umbenannt.
- Spielerlisten (Players, Whitelist, Audit-Log-Detail) zeigten überall den
  generischen Steve-Kopf statt des echten Spieler-Skins - `GuiItem`
  bekommt eine neue `playerHead(OfflinePlayer, ...)`-Fabrikmethode
  (`SkullMeta#setOwningPlayer`), von allen Spieler-repräsentierenden Kacheln
  jetzt genutzt.
- **Kritisch: Plugin startete auf einem echten Paper-Server überhaupt
  nicht** (`HikariConfig: Failed to load driver class dev.universaladmin.libs.sqlite.JDBC`).
  Zwei zusammenwirkende Shadow-Plugin-Probleme, keines davon durch die
  Testsuite (läuft gegen ungeshadete, unminimierte Abhängigkeiten)
  erkennbar - erst durch echten Serverstart aufgefallen:
  - `shadowJar { minimize() }` entfernte beide JDBC-Treiber komplett aus
    der jar, weil sie nur reflektiv über `HikariConfig#setDriverClassName`
    geladen werden - für `minimize()`s statische Erreichbarkeitsanalyse
    unsichtbar. Beide Treiber jetzt explizit von der Minimierung
    ausgeschlossen.
  - `org.sqlite` wurde relociert wie jede andere Abhängigkeit - bricht
    aber das JNI-Linking der gebündelten nativen SQLite-Bibliothek
    (`ClassNotFoundException: org/sqlite/core/NativeDB`), da deren
    kompiliertes Binary den Original-Klassennamen fest verdrahtet hat.
    `org.sqlite` wird jetzt bewusst nicht mehr relociert (bleibt aber
    weiterhin gebündelt) - siehe
    [docs/development/architecture-rules.md](docs/development/architecture-rules.md)
    und `build.gradle.kts`.
  - Neuer Gradle-Task `verifyShadedJarDrivers` (läuft als Teil von
    `check`/`build`): öffnet eine echte SQLite-Verbindung ausschließlich
    über die fertige, gebaute jar (kein anderer Classpath-Eintrag), damit
    genau diese Fehlerklasse nie wieder unbemerkt zurückkommt. Siehe
    `ShadedJarDriverSmokeTestMain`.
- `PlayerProfileIndexMigration`/`ModerationPunishmentIndexMigration`
  verwendeten `CREATE INDEX IF NOT EXISTS`, das echtes MySQL (im Gegensatz
  zu SQLite/MariaDB) nicht unterstützt - entfernt, da `MigrationRunner`
  ohnehin garantiert, dass jede Migration höchstens einmal läuft. Siehe
  [docs/architecture/storage.md#dialekt-unterschiede](docs/architecture/storage.md#dialekt-unterschiede).
