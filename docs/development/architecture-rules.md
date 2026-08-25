# Verbindliche Entwicklungsregeln

Diese Datei ist die verbindliche Kurzfassung der Regeln, nach denen in
diesem Repository entwickelt wird. Sie beschreibt Entscheidungen, die
**bereits getroffen wurden** - nicht bei jeder Änderung neu verhandeln,
sondern darauf aufbauen. Wenn eine Aufgabe diesen Regeln widerspricht,
gehört das explizit benannt (siehe "Bestehende Architektur respektieren"
am Ende), bevor davon abgewichen wird.

Ausführliche Begründungen stehen in [ARCHITECTURE.md](../../ARCHITECTURE.md), den
[ADRs](../architecture/decisions/) und im übrigen `docs/`-Baum. Diese Datei
ist die Kurzfassung zum Nachschlagen während der Arbeit; sie ersetzt keine
davon.

## Projektziel

UniversalAdmin ist eine universelle Admin-Plattform für Paper-Server -
**keine** Sammlung von GUI-Commands. Langfristig geplant: Core-Plugin,
eingebaute Module, öffentliche Extension-API, Community-Extensions, optionale
Web-App, REST-API, WebSockets. Aktuell (siehe [ROADMAP.md](../../ROADMAP.md))
existiert der Core mit acht eingebauten Modulen und noch keine öffentliche
API, kein Webserver.

Jede Architekturentscheidung muss so getroffen werden, dass sie diesen
späteren Ausbau nicht durch einen Rewrite erzwingt. Siehe
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md).

## Die eine Architekturregel, die alles andere erklärt

```
Frontend (GUI / Command / später Web)
    ↓
Application Services
    ↓
Actions / Domain Logic
    ↓
Repositories / Server Adapters
    ↓
Paper / Datenbank
```

Business-Logik lebt **ausschließlich** in Services und Actions. Frontends
(GUI-Click-Handler, Commands, später Web-Endpunkte) rufen Services/Actions
auf - sie enthalten selbst keine Logik. Das ist der Grund, warum die gleiche
"Spieler kicken"-Logik später von einem GUI-Button, einem `/admin kick` und
einem REST-Endpunkt genutzt werden kann, ohne Code zu duplizieren.

Konkret:

- **Kein Business-Logic-Code** in `dev.universaladmin.gui.*` Click-Handlern
  oder `dev.universaladmin.command.*` Executors - nur Aufrufe von Services
  oder `Action`s.
- **Kein SQL** außerhalb von `*Repository`-Implementierungen (typischerweise
  in einem `jdbc`-Subpackage). Services und Actions kennen nur das
  `Repository<T, ID>`-Interface, nie `Connection`/`Statement`/`DataSource`.
- **Keine Bukkit-Event-Listener mit Logik.** Ein Listener übersetzt ein
  Bukkit-Event in einen Aufruf an einen Service/eine Action und sonst nichts.

Referenzimplementierung für dieses Muster:
[`dev.universaladmin.modules.players`](../../src/main/java/dev/universaladmin/modules/players)
(Model → Repository → Service → Action → Module). Neue Module orientieren
sich daran, siehe [docs/development/adding-module.md](../development/adding-module.md).

## Action-Ausführung und Autorisierung

- **Frontends rufen nie `Action.execute(...)` direkt auf.** Der einzige Weg,
  eine Action laufen zu lassen, ist `ActionExecutor.execute(ActionRequest)`
  (bzw. die `(ActionId, ActionContext, I)`-Überladung) - siehe
  [docs/architecture/actions.md](../architecture/actions.md). Der Executor
  übernimmt Permission-/Feature-enabled-/Self-Target-/Input-Validierung und
  den Audit-Hook; ein direkter `Action.execute(...)`-Aufruf umgeht das
  komplett.
- **Keine verstreuten `player.hasPermission("...")`-Aufrufe mit rohem
  String-Literal** für alles, was eine Action ist. Permission-Nodes werden
  bei der Action-Registrierung über `ActionDefinition.Builder#permission(...)`
  deklariert; geprüft wird über den `PermissionEvaluator`, den jeder `Actor`
  trägt (`Actor.hasPermission(node)`), nie direkt gegen ein Bukkit-`Permissible`.
- **Kein Modul baut eigenes Audit-Logging.** `ActionExecutor` erzeugt den
  `AuditEvent` für jede Action automatisch aus `ActionDefinition`/
  `ActionContext`/`ActionResult`; ein Modul liefert höchstens optionale
  `AuditDetails` (Grund, Alt-/Neuwert, Metadata, ...) über
  `ActionDefinition.Builder#auditDetails(...)` - nie einen eigenen Aufruf an
  `AuditService`/`AuditEventRepository` außerhalb dieses Hooks. Siehe
  [docs/user/audit-log.md](../user/audit-log.md).

## Package-Regeln

- Root-Package: `dev.universaladmin`.
- Architektur-Pakete (`core`, `module`, `action`, `gui`, `command`,
  `permission`, `storage`, `audit`, `config`, `settings`, `localization`,
  `notification`, `scheduler`) enthalten die plattformweiten Abstraktionen.
  Ein Modul implementiert diese Interfaces, es erweitert sie nicht.
  `settings` ist das typisierte Settings-System (`SettingKey`/`SettingDefinition`/
  `SettingRegistry`/`SettingsService`); `config` ist bewusst kleiner
  gehalten und nur noch für die `config.yml`-Versionierung
  (`ConfigMigration`/`ConfigMigrationRunner`) zuständig - siehe
  [docs/development/settings.md](../development/settings.md).
- Eingebaute Module leben unter `dev.universaladmin.modules.<name>` (Plural
  `modules`, damit klar ist: das ist eine Sammlung von Modulen, keine
  Kernklasse). Jedes Modul ist in sich geschlossen; Cross-Module-Zugriff läuft
  über `ServiceRegistry`, nie über einen direkten Import einer
  Modul-internen Klasse eines anderen Moduls.
- Konkrete Adapter (JDBC-Implementierungen, Bukkit-spezifischer Code) liegen
  in einem `jdbc`- bzw. dem jeweiligen Adapter-Subpackage, nicht im
  Interface-Package selbst. Siehe z. B. `storage/` (Interfaces) vs.
  `storage/jdbc/` (Hikari/JDBC).
- Es gibt aktuell **ein** Gradle-Projekt (`universaladmin-core`). Ein Split in
  `universaladmin-api`/`-sdk`/`-web` ist geplant, aber nicht jetzt -
  Begründung in
  [docs/architecture/decisions/0006-optional-web-architecture.md](../architecture/decisions/0006-optional-web-architecture.md).
  Baue keine leeren Multi-Module-Gerüste dafür vor.

## Naming Conventions

- Typed IDs statt roher Strings: `ModuleId`, `ActionId`, `GuiPageId`,
  `AuditEventType` sind `record`s um `dev.universaladmin.core.id.Key`
  (`namespace:name`, z. B. `core:players`). `PermissionNode` und `MessageKey`
  sind eigene, einfache dotted-String-Records (siehe deren Javadoc, warum sie
  keinen `Key` verwenden - sie folgen externen Konventionen wie LuckPerms).
- Interfaces ohne Präfix/Suffix (`Repository`, `Module`, `Action`), konkrete
  Implementierungen mit sprechendem Präfix (`JdbcPlayerProfileRepository`,
  `YamlSettingsService`, `InGameNotificationService`).
- Domain-Modelle sind `record`s, nicht Klassen mit Settern. Wo ein Zustand
  sich ändert, entsteht ein neuer Record (siehe `PlayerProfile.withLastSeen`).
- Ergebnisse von `Action`s sind `ActionResult<R>` (sealed: `Success`/
  `Failure`), keine `null`-Rückgaben, keine geschluckten Exceptions.

## Threading-Regeln

Ausführlich: [docs/architecture/threading.md](../architecture/threading.md).

- **Niemals blockierende DB-Calls auf dem Paper-Main-Thread.** Jede
  `Repository`-Methode läuft über `TaskScheduler.supplyAsync`/`runAsync`
  (virtuelle Threads, siehe `PaperTaskScheduler`).
- Alles, was Bukkit-API anfasst (Inventories, Entities, World), läuft über
  `TaskScheduler.runOnMainThread`.
- Die dokumentierte Ausnahme: `MigrationRunner.runPending()` beim
  Plugin-Start in `UniversalAdminPlugin#onEnable`, bevor Spieler joinen
  können - läuft bewusst **zweimal** (einmal vor, einmal nach
  `ModuleManager.enableAll()`, da jedes Modul seine eigene Migration erst
  in seinem eigenen `onEnable` registriert; siehe
  [docs/architecture/threading.md](../architecture/threading.md)), nie
  öfter und kein Vorbild für sonstigen Code.
- **Niemals globale Bukkit-Reloads** (`Bukkit.reload()` o. Ä.) auslösen oder
  dazu anleiten - das umgeht Plugin-Lifecycle und Server-State auf Wegen, die
  UniversalAdmin nicht kontrollieren kann. Der einzige sanktionierte Reload
  ist `/admin reload` (siehe [ReloadConfigAction](../../src/main/java/dev/universaladmin/settings/ReloadConfigAction.java)) -
  liest ausschließlich UniversalAdmins eigene `config.yml` neu.

## Configuration & Localization

Ausführlich: [docs/development/settings.md](../development/settings.md).

- **Kein `config.getString(...)`/`getInt(...)`/... verstreut im Code.**
  Jeder Config-Wert ist ein registriertes, typisiertes
  `SettingDefinition<T>` (`SettingKey<T>`, `SettingType<T>`, Default,
  Validator, `requiresRestart`-Flag), gelesen über `SettingsService.get(key)`.
  Core-Settings stehen in `dev.universaladmin.settings.CoreSettings`; ein
  Modul registriert eigene Settings unter seinem eigenen
  `ModuleDescriptor.settingsNamespace()`, nie unter `core`.
- **Ein ungültiger Config-Wert crasht nie den Server.** `YamlSettingsService`
  fällt bei einem Parse- oder Validierungsfehler auf den Default zurück und
  loggt eine klare Warnung - das gilt für den initialen Start genauso wie
  für `/admin reload`.
- **Restart-required-Settings ändern sich nie live.** Ändert sich beim
  Reload ein Wert, dessen `SettingDefinition.requiresRestart()` `true` ist,
  bleibt der alte Wert aktiv und die Änderung wird als "pending restart"
  gemeldet - nicht versuchen, das live nachzuziehen (z. B. Datenbank-
  Verbindungsparameter).
- **Keine sichtbaren Texte im Code.** Jeder Nutzertext ist ein
  `MessageKey`, aufgelöst über `MessageService.get(key, args...)` aus
  `lang/<locale>.yml`. Fallback-Kette: aktive Locale → `en_US` → sichtbarer
  `[missing: ...]`-Marker (mit einmaligem Debug-Log pro Key, kein Spam).
  Rendering als Adventure-`Component` (MiniMessage) passiert erst in der
  GUI-/Command-Schicht (`ComponentMessages.render(...)`) - `MessageService`
  selbst liefert nur den aufgelösten String, damit dieselbe Auflösung später
  von einer Web-Ansicht wiederverwendet werden kann.
- **`config-version` nicht von Hand ändern.** Schema-Änderungen an
  `config.yml` bekommen eine neue `ConfigMigration`
  (`dev.universaladmin.config`), analog zu `storage.Migration` für die
  Datenbank - eine bestehende Nutzer-Config wird nie stillschweigend
  überschrieben.

## Module-Lifecycle

Ausführlich: [docs/architecture/modules.md](../architecture/modules.md).

- Jedes Modul durchläuft `DISCOVERED → LOADED → ENABLED` (via
  `ModuleManager.loadAll()`/`enableAll()`) und zurück zu `DISABLED` (via
  `disableAll()`). `ModuleRegistry` speichert nur den Zustand;
  `ModuleManager` ist die einzige Stelle, die Übergänge auslöst.
- **Ein Modul ist nie kritisch, Core-Bootstrap-Komponenten sind es immer.**
  Wirft `Module#onLoad`/`onEnable`, wird **nur dieses Modul** `FAILED`
  markiert (mit vollständig geloggtem Stacktrace) - der Rest startet normal
  weiter. Wirft dagegen etwas während der kritischen Bootstrap-Phase in
  `UniversalAdminPlugin#bootstrapCore` (Config, Scheduler, Storage +
  Migrationen, die geteilten Registries), bricht der gesamte Plugin-Start
  ab und das Plugin deaktiviert sich selbst - dafür gibt es keine
  Modul-Ebene, auf der man das isolieren könnte.
- Modul-Abhängigkeiten werden über `ModuleDescriptor.dependencies()`
  deklariert, nicht über direkte Imports. `ModuleManager` bringt Module
  per topologischer Sortierung in eine Reihenfolge, in der Abhängigkeiten
  zuerst laden/enablen. Eine Abhängigkeitszyklus ist ein Programmierfehler
  im deklarierten Graphen, kein isolierbarer Laufzeitfehler - `loadAll()`
  wirft in diesem Fall statt weiterzumachen.
- Ressourcen, die ein Modul in `onEnable` registriert (Listener,
  Scheduler-Tasks, Registry-Einträge, die beim Disable wieder verschwinden
  sollen), gehören über `context.resources().listener(...)`/`task(...)`/
  `closeable(...)` - nicht manuell in `onDisable` nachgebaut. Sie werden
  automatisch freigegeben, auch wenn `onEnable` selbst wirft oder
  `onDisable` eine Exception wirft.
- Keine Fehler still verschlucken: jeder `FAILED`-Übergang wird mit
  Modul-ID und vollem Stacktrace geloggt (`Level.SEVERE`), nie nur als
  Zustand vermerkt.

## Sicherheit

- **Keine Secrets loggen** (DB-Passwörter, Tokens, später API-Keys) - auch
  nicht auf `FINE`/Debug-Level.
- **Keine unsicheren Packet-Hacks im Core** (kein ProtocolLib, kein rohes
  Packet-Injection). Wenn low-level Netzwerkzugriff für eine Extension
  nötig wird, ist das explizit außerhalb des Cores zu lösen, nicht im Core
  nachzurüsten.
- Business-Logik-Fehler werden über `ActionResult.Failure` mit
  `FailureReason` transportiert, nicht über verschluckte Exceptions oder
  generische `RuntimeException`s ohne Kontext.

## Dependencies

- **Keine neuen Dependencies ohne klaren Grund**, der in einem Kommentar
  oder Commit/PR nachvollziehbar ist. Aktuell bewusst **keine** Pflicht-
  Dependencies auf Vault, LuckPerms, PlaceholderAPI oder ProtocolLib im Core.
- Datenbank-Treiber (`sqlite-jdbc`, `mariadb-java-client`) und `HikariCP` sind
  die einzigen Runtime-Libraries; sie werden per Shadow-Plugin gebündelt,
  damit sie nicht mit anderen Plugins auf demselben Server kollidieren.
  `mariadb-java-client` und `HikariCP` werden dabei zusätzlich unter
  `dev.universaladmin.libs.*` relociert (siehe `build.gradle.kts`).
  `sqlite-jdbc` bewusst **nicht** relociert - es bündelt eine native
  (JNI-)Bibliothek, deren kompiliertes Binary den Klassennamen
  `org/sqlite/core/NativeDB` fest verdrahtet für das native
  Methoden-Linking; eine Relocation dieser Java-Klasse bricht das JNI-
  Linking beim ersten echten Connection-Aufbau (`ClassNotFoundException:
  org/sqlite/core/NativeDB`), obwohl Build und jeder andere Check dabei
  grün bleiben. Bleibt trotzdem gebündelt (geshadet), nur unter seinem
  Original-Package-Namen - ein Kollisionsrisiko mit einer fremden
  sqlite-jdbc-Kopie auf demselben Server ist der akzeptierte Kompromiss,
  wie bei jedem anderen Bukkit-Plugin, das diesen Treiber bündelt.

## Built-in Modules bleiben Extension-freundlich

Eingebaute Module verwenden exakt dieselben Abstraktionen
(`Module`/`Action`/`GuiPage`/`Repository`/`Migration`/`PermissionRegistry`),
die später auch externen Extensions offenstehen sollen. Baue kein
"Schnellweg"-Verhalten für Built-ins, das eine Extension nicht auch könnte -
siehe [docs/architecture/extensions-future.md](../architecture/extensions-future.md).
Es gibt noch keine öffentliche `api`/`sdk`-Modulgrenze; diese Regel ist der
Ersatz dafür, bis es sie gibt.

## Tests

- Jede neue Business-Logik (Service, Action, Migration mit nicht-trivialer
  Logik) braucht einen Unit-Test, der **ohne** laufenden Paper-Server läuft.
  Repositories werden über ein In-Memory-Fake des jeweiligen Interfaces
  getestet, nicht gemockt, wo ein Fake einfacher ist (siehe
  `PlayerServiceTest`).
- Migrationen werden gegen eine echte (temporäre) SQLite-Datenbank getestet,
  nicht gegen Mocks (siehe `MigrationRunnerTest`).
- Details und Konventionen: [docs/development/testing.md](../development/testing.md).

## Dokumentation aktuell halten

Code-Änderungen, die eine Architekturentscheidung, ein Modul-Verhalten oder
eine Konfigurationsoption betreffen, aktualisieren die passende Datei in
`docs/` bzw. diese Datei im selben Change - nicht "später". Eine neue,
bewusste Architekturentscheidung bekommt eine neue ADR-Datei unter
`docs/architecture/decisions/`, keine Diskussion nur im Commit-Message.

## Bestehende Architektur respektieren

Diese Struktur wurde bewusst geplant (siehe ADRs). Ein Task, der ein neues
Feature will, baut **auf** dieser Architektur auf - er baut sie nicht bei
jeder Gelegenheit um. Wenn die Architektur für einen konkreten Fall
nachweislich nicht passt, wird das als eigener Vorschlag (neue ADR) benannt,
nicht stillschweigend im Feature-PR mitgeändert.

## Build & Test

```bash
./gradlew build   # kompiliert, testet, baut die shaded jar (build/libs/universaladmin-core-*.jar)
./gradlew test    # nur Tests
```

Java-Toolchain ist 25 (per `foojay-resolver-convention` automatisch
beschafft, falls lokal nicht vorhanden). Ziel-Server: aktuelle stabile Paper
API (siehe `build.gradle.kts` für die exakte Version).
