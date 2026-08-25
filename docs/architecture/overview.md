# Architecture Overview

Ausführliche Version von [ARCHITECTURE.md](../../ARCHITECTURE.md). Dieses
Dokument geht Package für Package durch, was existiert und warum.

## Die Schichten im Code

| Schicht | Package(s) | Beispiel |
|---|---|---|
| Frontend | `gui`, `command` (später: Web) | `UniversalAdminCommand` |
| Application Services | modulintern, z. B. `modules.players.PlayerService` | `PlayerService` |
| Actions / Domain Logic | `action`, modulintern `modules.*.action` | `GetPlayerProfileAction` |
| Repositories | `storage` (Interface), `storage.jdbc`/`modules.*.jdbc` (Adapter) | `JdbcPlayerProfileRepository` |
| Paper / Datenbank | Bukkit-API, `javax.sql.DataSource` | - |

Jede Zeile kennt nur die Zeile unter sich über ein Interface, nie eine
konkrete Implementierung zwei Zeilen tiefer. Ein `GuiPage` kennt einen
`Service`, nie ein `Repository`. Ein `Service` kennt ein `Repository`-
Interface, nie `Connection`.

## Der Composition Root: `dev.universaladmin.core`

- `UniversalAdmin` - hält jede geteilte Registry/jeden geteilten Service
  (Plugin-Instanz, Version, Startzeit, `SettingRegistry`+`SettingsService`,
  Scheduler, Storage, Actions, GUI-Pages, `GuiFramework` (Sessions/Icons -
  siehe [docs/development/gui-framework.md](../development/gui-framework.md)),
  Permissions, `ModuleRegistry`, Audit, Messages, Notifications, ein
  generisches `ServiceRegistry` für modulübergreifend geteilte Services).
  Wird einmal in `UniversalAdminPlugin#bootstrapCore` gebaut und an jedes
  Modul über `ModuleContext` weitergereicht.
- `PluginStatus`/`ComponentStatus` - `UniversalAdmin.status()` baut bei
  jedem Aufruf einen frischen Snapshot (Version, Uptime, aktive/fehlgeschlagene
  Module aus `ModuleRegistry`, grober Datenbank-/Web-Status). Nichts wird
  gecacht - zwei Aufrufe im Abstand einer Minute können unterschiedliche
  Modullisten zeigen. Genutzt vom `/admin`-Command.
- `ServiceRegistry` - Typ-basierte Registry (`Class<T> → T`) für Services,
  die mehr als ein Modul braucht (Beispiel: `PlayerService`, das
  `PlayersModule` registriert und das z. B. `ModerationModule` später
  nachschlagen kann, ohne eine eigene Kopie zu bauen).
- `registry.Registry` / `registry.SimpleRegistry` - die generische,
  Thread-sichere Registry-Implementierung, auf der `ActionRegistry`,
  `GuiRegistry`, `PermissionRegistry` aufbauen.
- `id.Key` - `namespace:name`-Identifier, Basis für `ModuleId`, `ActionId`,
  `GuiPageId`, `AuditEventType`. Der Namespace ist der Kollisionsschutz für
  künftige Extensions (`core:players` vs. `myext:players`).

## `module` - Lifecycle-System

`Module` ist das Interface, das jedes eingebaute Modul (und später jede
Extension) implementiert: ein `ModuleDescriptor` (ID, Name, Beschreibung,
Abhängigkeiten, Settings-/Permission-Namespace, optionales GUI-Icon), plus
`onLoad`/`onEnable`/`onDisable`. `ModuleRegistry` trackt den Zustand
(`DISCOVERED`/`LOADED`/`ENABLED`/`DISABLED`/`FAILED`) jedes Moduls;
`ModuleManager` treibt die Übergänge in Abhängigkeitsreihenfolge
(topologische Sortierung über `ModuleDescriptor.dependencies()`) und
isoliert ein fehlschlagendes Modul (`FAILED`, voll geloggt), statt den
ganzen Server-Start abzubrechen. `ModuleResources` (Teil von
`ModuleContext`) gibt Listener/Tasks/Registry-Einträge, die ein Modul in
`onEnable` registriert, beim Disable automatisch wieder frei. Volles
Detail: [modules.md](modules.md).

## `action` - wo Business-Logik lebt

`Action<I, R>` ist eine einzelne, von jedem Frontend aufrufbare
Operation. `ActionResult<R>` ist ein sealed Success/Failure-Typ statt
Exceptions oder `null`. `Actor`/`ActorType` beschreiben, *wer* handelt
(Spieler, Konsole, System, künftig: Web), ohne dass `action` von Bukkit-
Typen abhängt. Details: [actions.md](actions.md).

## `gui` und `command` - die Frontends

`GuiPage` ist die einzige Stelle, die Bukkit-Inventory-APIs anfassen darf.
`UniversalAdminCommand` ist der Root-Command (`/admin`, Aliase `/ua`,
`/uadmin`) - aktuell ein Statuscommand, der `UniversalAdmin.status()`
rendert, kein Feature-Command. Er ist die eine dokumentierte Ausnahme von
"Frontend bekommt nur die eine Abhängigkeit, die es braucht": ein
Root-Statuscommand braucht legitim Lesezugriff auf die ganze Plattform.
Jede spätere `GuiPage` bekommt dagegen genau die Services/Actions, die sie
braucht, über den Konstruktor, nicht durch Zugriff auf ein globales
Objekt zur Laufzeit. Details: [gui.md](gui.md).

## `storage` - Persistenz

`Repository<T, ID>` ist die Standardform für Datenzugriff, immer async
(`CompletableFuture`). `Migration`/`MigrationRunner` versionieren das
Schema. `storage.jdbc.DataSourceFactory` ist die einzige Stelle, die
`HikariConfig` und JDBC-URLs baut. Details: [storage.md](storage.md).

## `permission`, `audit`, `settings`, `config`, `localization`, `notification`, `scheduler`

Je ein fokussierter Querschnittsservice:

- `permission` - `PermissionNode`/`PermissionDefinition`/`PermissionRegistry`,
  Paper-unabhängig testbar; der Sync zu Bukkits `PluginManager` passiert erst
  im Bootstrap.
- `audit` - `AuditService`, JDBC-Implementierung in `audit.jdbc`. Jede Action
  mit sichtbarer Wirkung sollte nach Erfolg hier einen Eintrag schreiben.
- `settings` - das typisierte Settings-System: `SettingKey`/`SettingType`/
  `SettingDefinition`/`SettingValidator`/`SettingRegistry`/`SettingsService`,
  `CoreSettings` (alle Core-Settings), `YamlSettingsService` als Paper-
  Adapter, `ReloadConfigAction` für `/admin reload`. Ersetzt vollständig
  verstreute `config.getString(...)`-Aufrufe. Details:
  [../development/settings.md](../development/settings.md).
- `config` - bewusst klein: nur noch `ConfigMigration`/`ConfigMigrationRunner`
  für die `config.yml`-Versionierung (`config-version`), analog zu
  `storage.Migration` für die Datenbank.
- `localization` - `MessageService`/`MessageKey` (Paper-unabhängig, liefert
  reine Strings), `YamlLocaleMessageService` als Mehrsprachen-Adapter über
  `lang/*.yml` mit Fallback-Kette (aktive Locale → `en_US` → sichtbarer
  Marker), `ComponentMessages` als dünner MiniMessage-Renderer für die
  Ingame-Ausgabe.
- `notification` - `NotificationService`, aktuell nur Ingame-Chat
  (`InGameNotificationService`); der Interface-Schnitt ist bereits so
  gewählt, dass ein Discord-/Web-Kanal ihn nur implementieren muss.
- `scheduler` - `TaskScheduler`/`PaperTaskScheduler`, siehe
  [threading.md](threading.md).

## `modules.*` - die eingebauten Module

Siehe [modules.md](modules.md) für die vollständige Liste und
[adding-module.md](../development/adding-module.md) für die Anleitung, ein
neues zu bauen. `modules.players` ist die Referenzimplementierung.

## Warum ein Gradle-Projekt statt vier

`universaladmin-api`, `-sdk`, `-web` würden heute nur aus leeren Ordnern
bestehen - es gibt noch keine externen Extensions und keine Web-App, die
gegen sie kompilieren. Der Trennungspunkt ist stattdessen im Code markiert
(siehe [extensions-future.md](extensions-future.md) und
[web-future.md](web-future.md)) und wird zu einem echten Modul-Split, sobald
es etwas gibt, das ihn braucht. Details:
[decisions/0006-optional-web-architecture.md](decisions/0006-optional-web-architecture.md).
