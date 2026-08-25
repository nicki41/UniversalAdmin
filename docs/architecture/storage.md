# Storage

## Repository-Pattern

Jede Persistenz läuft über ein `Repository<T, ID>`
([`src/main/java/dev/universaladmin/storage/Repository.java`](../../src/main/java/dev/universaladmin/storage/Repository.java)):

```java
public interface Repository<T, ID> {
    CompletableFuture<Optional<T>> findById(ID id);
    CompletableFuture<List<T>> findAll();
    CompletableFuture<T> save(T entity);
    CompletableFuture<Void> deleteById(ID id);
}
```

Services und Actions kennen nur dieses Interface (oder eine modulspezifische
Erweiterung davon, z. B. `AuditEventRepository.recent(int)`). SQL,
`Connection`, `PreparedStatement` existieren ausschließlich in
`*Repository`-Implementierungen, üblicherweise in einem `jdbc`-Subpackage
neben dem Interface (`storage.jdbc`, `audit.jdbc`, `modules.players.jdbc`).

Alle Methoden sind async (`CompletableFuture`) - siehe
[threading.md](threading.md) für die Regel, warum.

## Datenbank-Konfiguration

Standard ist SQLite (`plugins/UniversalAdmin/data.db`), MySQL/MariaDB ist
über `config.yml` aktivierbar:

```yaml
database:
  type: sqlite   # oder: mysql
  file: data.db
  host: localhost
  port: 3306
  database: universaladmin
  username: universaladmin
  password: ""
  ssl: true
  pool-size: 10
```

`CoreSettings.readDatabaseConfig(settingsService)` liest die einzelnen
typisierten `database.*`-Settings (siehe
[docs/development/settings.md](../development/settings.md)) und baut daraus
ein `DatabaseConfig`-Record (siehe
[`src/main/java/dev/universaladmin/storage/DatabaseConfig.java`](../../src/main/java/dev/universaladmin/storage/DatabaseConfig.java)).
`storage.jdbc.DataSourceFactory` ist die einzige Stelle, die daraus eine
gepoolte `javax.sql.DataSource` (HikariCP) baut - SQLite bekommt einen
Pool mit Größe 1 (SQLite kennt keine echte nebenläufige Schreib-
Nebenläufigkeit), MySQL/MariaDB die konfigurierte Poolgröße. Der
MariaDB-JDBC-Treiber wird auch für "echtes" MySQL verwendet - er spricht
das MySQL-Protokoll, ein zweiter Treiber nur für MySQL wäre eine
zusätzliche Abhängigkeit ohne Mehrwert. Nutzerdokumentation (SQLite vs.
MySQL, wann welches sinnvoll ist) steht in
[docs/user/database.md](../user/database.md).

`StorageService` ist der "Database Manager" dieses Systems - ein Name, den
der Code bewusst nicht trägt, um keine zweite Abstraktion neben
`DataSourceFactory`/`MigrationRunner` einzuführen, die dasselbe täte.

### HikariCP als Connection-Pool

HikariCP ist die einzige Connection-Pool-Bibliothek im Projekt (siehe
[decisions/0003-repository-storage.md](decisions/0003-repository-storage.md))
- keine eigene Pool-Implementierung. Für SQLite ist der Pool bewusst auf
Größe 1 begrenzt (siehe oben); für MySQL/MariaDB nutzt er die konfigurierte
`database.pool-size`. HikariCP setzt außerdem den Zustand jeder Connection
(Autocommit, Read-Only, Catalog) zurück, bevor sie an den Pool
zurückgegeben wird - `Transactions` (siehe unten) verlässt sich bewusst
darauf, statt `setAutoCommit(true)` selbst wiederherzustellen.

### SQLite-Pragmas

`DataSourceFactory` setzt für SQLite vier Pragmas über die JDBC-URL
(`?journal_mode=WAL&synchronous=NORMAL&foreign_keys=on&busy_timeout=5000`):

- `journal_mode=WAL` - lässt künftige Leser laufen, ohne auf den einzigen
  Schreiber zu warten, statt des Standard-Rollback-Journals. Legt zwei
  zusätzliche Dateien neben der `.db`-Datei an (`-wal`, `-shm`).
- `synchronous=NORMAL` - die für WAL dokumentierte sichere Kombination
  (fsync bei Checkpoints, nicht bei jedem Commit).
- `foreign_keys=on` - SQLite erzwingt Fremdschlüssel nicht standardmäßig
  pro Connection; explizit eingeschaltet, damit `REFERENCES`-Constraints in
  Migrationen tatsächlich wirken.
- `busy_timeout=5000` - vermeidet ein sofortiges "database is locked",
  falls etwas außerhalb des Pools (z. B. ein Backup-Tool) die Datei kurz
  sperrt.

Kein automatisches Backup: siehe [docs/user/database.md](../user/database.md)
für die bewusste Entscheidung, dafür kein eigenes Feature zu bauen.

## Dialekt-Unterschiede

SQLite und MySQL/MariaDB sind nicht syntaktisch kompatibel, insbesondere:

- Auto-increment: SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` vs. MySQL
  `BIGINT AUTO_INCREMENT PRIMARY KEY`.
- Upsert: SQLite `INSERT ... ON CONFLICT (id) DO UPDATE SET ...` vs. MySQL
  `INSERT ... ON DUPLICATE KEY UPDATE ...`.
- `CREATE INDEX IF NOT EXISTS`: SQLite und MariaDB akzeptieren das, echtes
  MySQL (kein MariaDB) nicht - `CREATE INDEX` kennt dort kein
  `IF NOT EXISTS` (im Gegensatz zu `CREATE TABLE IF NOT EXISTS`, das überall
  funktioniert). Braucht ohnehin keine eigene Prüfung: `MigrationRunner`
  garantiert bereits, dass jede Migration höchstens einmal läuft - ein
  Index-erstellender `Statement#execute(...)` lässt das `IF NOT EXISTS`
  deshalb einfach weg, siehe `PlayerProfileIndexMigration`/
  `ModerationPunishmentIndexMigration`.

Jede Migration/Repository, die davon betroffen ist, prüft
`connection.getMetaData().getDatabaseProductName()` und wählt die passende
SQL-Variante - siehe `AuditSchemaMigration` und
`JdbcPlayerProfileRepository` als Beispiele. Es gibt bewusst keine
SQL-Abstraktionsschicht (kein JPA/Hibernate) darüber; bei der aktuellen
Anzahl Tabellen ist das mehr Overhead als Nutzen, siehe
[decisions/0003-repository-storage.md](decisions/0003-repository-storage.md).

## Migrationen

`Migration` ist ein Forward-only-Schema-Change:

```java
public interface Migration {
    int version();
    String description();
    void migrate(Connection connection) throws SQLException;
}
```

`MigrationRunner` führt alle registrierten Migrationen in Versionsreihen-
folge aus und trackt den Stand in einer `schema_version`-Tabelle. Versionen
sind global eindeutig über die ganze Datenbank, nicht pro Modul:

- **1-999**: Core-Migrationen (z. B. `AuditSchemaMigration`, Version 1),
  direkt in `UniversalAdminPlugin` registriert.
- **1000+**: Modul-Migrationen, vom jeweiligen Modul in `onEnable`
  registriert (z. B. `PlayerProfileMigration`, Version 1000).

Migrationen laufen einmal beim Plugin-Start (`storage.migrations().runPending()`
in `UniversalAdminPlugin#onEnable`), bevor Module enabled werden - siehe
[threading.md](threading.md) für die Ausnahme vom "kein Blocking auf dem
Main-Thread"-Prinzip, die das darstellt.

## Neue Migration hinzufügen

1. `Migration`-Implementierung im Modul-Package schreiben, Version nach dem
   letzten vergebenen Wert für dieses Modul (siehe existierende Migrationen
   für die aktuell höchste Version).
2. In `Module#onEnable` registrieren: `context.platform().storage().migrations().register(...)`.
3. Migration ist forward-only - keine nachträgliche Änderung einer bereits
   released Migration, stattdessen eine neue mit höherer Version.

## Health

`StorageService` trackt einen `DatabaseHealth` (`DISCONNECTED`,
`CONNECTING`, `READY`, `FAILED`):

```java
public enum DatabaseHealth {
    DISCONNECTED, CONNECTING, READY, FAILED
}
```

Der Konstruktor setzt `CONNECTING`, baut den Pool über `DataSourceFactory`
und validiert eine Connection (`Connection#isValid`); Erfolg setzt `READY`,
jeder Fehler setzt `FAILED` und wirft die Exception weiter. `close()` setzt
`DISCONNECTED`. `UniversalAdmin#status()` bildet das auf das gröbere
`ComponentStatus` ab (`READY→ONLINE`, `CONNECTING→DEGRADED`,
`FAILED`/`DISCONNECTED→OFFLINE`), das `/admin` anzeigt.

**Das ist ein Start-/Stop-Snapshot, keine laufende Live-Prüfung** - ein
Ausfall der Remote-Datenbank mitten im Betrieb (z. B. MySQL-Server stürzt
ab) ändert diesen Wert nicht rückwirkend. Ein periodischer Health-Check
wäre möglich, ist aber bewusst zurückgestellt: er bräuchte einen
wiederkehrenden Task (den `TaskScheduler` heute nicht anbietet, siehe
[threading.md](threading.md)) und der praktische Nutzen ohne eine
Auto-Reconnect- oder Auto-Restart-Strategie dahinter ist begrenzt.

**Entscheidung: kompletter DB-Ausfall beim Start deaktiviert das ganze
Plugin, kein eingeschränkter Betrieb.** `StorageService` ist eine
*kritische* Bootstrap-Komponente (siehe
[modules.md](modules.md#failure-isolation-whats-critical-whats-isnt)) - wirft der Konstruktor, bricht
`UniversalAdminPlugin#bootstrapCore` ab und das Plugin deaktiviert sich
selbst, bevor irgendein Modul lädt. Es gibt bewusst keinen
"Storage-loses"-Modus: jedes eingebaute Modul geht von einer
funktionierenden Datenbank aus, und ein Plugin, das scheinbar normal
startet, aber nichts persistieren kann, ist ein schlechteres
Fehlerverhalten als ein Plugin, das gar nicht erst startet.

## Transactions

Für Repository-Methoden, die mehrere Statements atomar ausführen müssen
(z. B. zwei Tabellen für eine logische Änderung), gibt es
`dev.universaladmin.storage.Transactions`:

```java
Transactions.run(dataSource, scheduler, connection -> {
    // mehrere PreparedStatements über dieselbe connection
    return result;
});
```

Läuft wie jeder andere JDBC-Aufruf über den übergebenen `TaskScheduler`
(nie den Main-Thread, siehe [threading.md](threading.md)), setzt
`autoCommit=false`, committet bei Erfolg, rollt bei jeder Exception
vollständig zurück und wirft sie weiter (unchecked-Exceptions unverändert,
`SQLException` gewrappt in `StorageException`). Eine Repository-Methode mit
nur einem Statement braucht das nicht - Statements sind für sich schon
atomar.
