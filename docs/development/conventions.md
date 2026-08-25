# Conventions

Ausführliche Begründungen stehen in [Entwicklungsregeln](architecture-rules.md) und den
[ADRs](../architecture/decisions/). Hier die Kurzfassung als Nachschlagwerk.

## Packages

- Root: `dev.universaladmin`.
- Architektur-Pakete (`core`, `module`, `action`, `gui`, `command`,
  `permission`, `storage`, `audit`, `config`, `localization`,
  `notification`, `scheduler`) - plattformweite Abstraktionen.
- Eingebaute Module: `dev.universaladmin.modules.<name>` (Plural
  `modules`). Adapter-/Bukkit-spezifischer Code in einem Subpackage wie
  `jdbc`, nicht im Interface-Package.

## Naming

- Interfaces ohne Präfix/Suffix: `Repository`, `Module`, `Action`,
  `SettingsService`.
- Implementierungen mit sprechendem Präfix, das die Technologie/den
  Kontext nennt: `JdbcPlayerProfileRepository`, `YamlSettingsService`,
  `InGameNotificationService`, `PaperTaskScheduler`.
- Typed IDs statt roher Strings für alles, was in einer Registry landet:
  `ModuleId`, `ActionId`, `GuiPageId`, `AuditEventType` (alle über
  `dev.universaladmin.core.id.Key`, Format `namespace:name`).
  `PermissionNode`/`MessageKey` sind eigene, einfache dotted-String-
  Records (externe Konventionen, siehe deren Javadoc).
- `ModuleId.core(...)`/`ActionId.core(...)`/... als Shorthand für den
  `core`-Namespace, den alle eingebauten Module nutzen.

## Domain-Modelle

- `record`, nicht Klassen mit Settern. Zustandsänderung erzeugt einen
  neuen Record (`PlayerProfile.withLastSeen(...)`), keine Mutation.
- Keine `null`-Rückgaben für "nicht gefunden" - `Optional<T>` bei
  Repository-Lookups, `ActionResult.Failure` bei Actions.

## Fehlerbehandlung

- Actions: `ActionResult<R>` (sealed `Success`/`Failure` mit
  `FailureReason`), keine Exceptions für erwartbare Fehlerfälle
  (nicht gefunden, keine Berechtigung, Validierung).
- Repository-/Storage-Fehler: eigene, modulspezifische unchecked
  Exception (`PlayerStorageException`, `AuditStorageException`), die eine
  `SQLException` wrapped - kein rohes `SQLException`-Durchreichen an
  Aufrufer außerhalb der `jdbc`-Schicht.

## Formatierung

- UTF-8, `-parameters`-Compiler-Flag aktiv (siehe `build.gradle.kts`).
- Kein festes Auto-Formatter-Tool aktuell eingerichtet; an bestehendem
  Stil im jeweiligen Package orientieren (4 Leerzeichen Einrückung,
  Zeilenlänge grob ~110 Zeichen, ein Import pro Zeile, keine Wildcard-
  Imports).
- Javadoc auf öffentlichen Interfaces/Klassen erklärt *warum*, nicht
  *was* (siehe bestehende Klassen als Beispiel) - keine Javadoc-Pflicht
  für private/offensichtliche Methoden.
