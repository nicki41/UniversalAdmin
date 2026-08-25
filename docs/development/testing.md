# Testing

## Grundsatz

Business-Logik (Services, Actions, nicht-triviale Migrationen) muss ohne
laufenden Paper-Server testbar sein. Das ist der eigentliche Test dafür,
ob die Schichtentrennung aus [ARCHITECTURE.md](../../ARCHITECTURE.md)
eingehalten wurde - lässt sich ein Service nicht ohne Bukkit-Mocking
testen, hängt vermutlich Business-Logik an einer Stelle, die eigentlich
nur Frontend/Adapter sein sollte.

## Werkzeuge

- JUnit 5 (Jupiter) - Testframework.
- Mockito - verfügbar (`testImplementation`), aber nicht die erste Wahl:
  siehe unten.
- Kein Paper-Server-Mocking-Framework (MockBukkit o. Ä.) aktuell
  eingerichtet. Wird relevant, sobald `GuiPage`-Implementierungen oder
  Bukkit-Event-Listener mit nennenswerter eigener Logik entstehen (siehe
  [ROADMAP.md](../../ROADMAP.md) Phase 1) - bis dahin nicht vorzeitig
  hinzufügen.

## Repositories: Fakes statt Mocks

Ein Service hängt von einem `Repository`-*Interface* ab - das lässt sich
mit einer einfachen In-Memory-Implementierung faken, statt jede Methode
mit Mockito zu mocken. Ein Fake verhält sich wie eine echte
Implementierung (konsistenter Zustand über mehrere Aufrufe), ein Mock
antwortet nur auf das, was explizit programmiert wurde - für
Repository-Tests ist ein Fake meist der Test, der tatsächlich das
Service-Verhalten prüft statt nur die Aufrufreihenfolge.

Beispiel:
[`PlayerServiceTest`](../../src/test/java/dev/universaladmin/modules/players/PlayerServiceTest.java)
- ein `record`, das `PlayerProfileRepository` gegen eine
`ConcurrentHashMap` implementiert, direkt in der Testklasse.

Mockito ist die richtige Wahl, wenn eine Abhängigkeit *Verhalten*
simulieren muss, das ein einfaches Fake nicht sinnvoll abbildet (z. B. ein
Fehlerfall, der nur schwer über einen echten Zustand erzwingbar ist).

## Migrationen: echte SQLite-Datenbank, kein Mock

`Migration`/`MigrationRunner` gegen eine echte temporäre SQLite-Datei
testen (`@TempDir` + `DataSourceFactory`), nicht gegen eine gemockte
`Connection` - SQL-Syntaxfehler fallen nur gegen eine echte Datenbank auf.
Beispiel:
[`MigrationRunnerTest`](../../src/test/java/dev/universaladmin/storage/MigrationRunnerTest.java).

**Wichtig unter Windows:** die von `DataSourceFactory.create(...)`
erzeugte `DataSource` (HikariCP) am Ende des Tests schließen
(`((AutoCloseable) dataSource).close()`), sonst kann JUnits `@TempDir`
die Datei nach dem Test nicht löschen, weil SQLite die Datei noch offen
hält.

## Settings/Config: echte `YamlConfiguration`, kein Mock

Wie bei Migrationen: `YamlSettingsService` gegen eine echte (in-memory)
`org.bukkit.configuration.file.YamlConfiguration` testen
(`config.loadFromString("gui:\n  page-size: 27\n")`), nicht gegen eine
gemockte `FileConfiguration` - `YamlConfiguration` ist eine reine
Datenstruktur-Klasse in `paper-api`, kein Server nötig. Für einen Reload
zwischen zwei Werten reicht ein `AtomicReference<YamlConfiguration>`, den
der `Supplier<FileConfiguration>` liest. Beispiel:
[`YamlSettingsServiceTest`](../../src/test/java/dev/universaladmin/settings/YamlSettingsServiceTest.java).

`YamlLocaleMessageService` entsprechend gegen echte, in einem `@TempDir`
geschriebene `lang/*.yml`-Dateien testen, nicht gegen eine gemockte
Message-Map - siehe
[`YamlLocaleMessageServiceTest`](../../src/test/java/dev/universaladmin/localization/YamlLocaleMessageServiceTest.java).
`SettingsService` selbst (nur für `general.language`) lässt sich hier
mocken, weil dieser Test nicht das Settings-System prüft, sondern nur die
Locale-Fallback-Logik.

## Was noch fehlt

- Tests für die acht Modul-Skelette folgen, sobald sie über
  `PlayersModule`/`PlayerService` hinaus echte Logik bekommen (siehe
  [adding-module.md](adding-module.md)).
- Permission-Tests bisher nur auf Validierungsebene
  (`PermissionNodeTest`) - Tests für tatsächliche Berechtigungs-
  *entscheidungen* folgen mit der ersten Action, die eine solche
  Entscheidung trifft.
- Kein Integrationstest, der `UniversalAdminPlugin#onEnable` end-to-end
  durchläuft (bräuchte einen Paper-Testserver) - bewusst zurückgestellt,
  bis dafür ein konkretes Bedürfnis besteht.
