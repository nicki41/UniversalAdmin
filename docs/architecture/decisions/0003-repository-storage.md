# 0003 - Repository-Pattern über JDBC statt ORM

## Status

Angenommen

## Kontext

Storage muss SQLite (Standard, Zero-Setup) und optional MySQL/MariaDB
unterstützen, vollständig async sein (kein Blocking auf dem Paper-Main-
Thread) und darf keine SQL-Queries in Services/GUI/Commands durchsickern
lassen. Die Datenmenge und Schemakomplexität eines Admin-Plugins ist
überschaubar (eine Handvoll Tabellen pro Modul), nicht die eines
Enterprise-Systems.

## Entscheidung

- `Repository<T, ID>`-Interface (async, `CompletableFuture`-basiert) als
  einzige Persistenz-Abstraktion, die Services/Actions kennen.
- Konkrete Implementierungen schreiben rohes JDBC mit
  `PreparedStatement` (kein ORM/Hibernate/JPA), gekapselt in
  `*Repository`-Implementierungsklassen in einem `jdbc`-Subpackage.
- `HikariCP` als Connection-Pool, `sqlite-jdbc` und `mariadb-java-client`
  als einzige Treiber (letzterer auch für MySQL, siehe
  [../storage.md](../storage.md)).
- `Migration`/`MigrationRunner` für versioniertes, forward-only Schema-
  Management mit einer `schema_version`-Tabelle, statt eines externen
  Migrationswerkzeugs (Flyway/Liquibase).
- Dialektunterschiede (Auto-Increment, Upsert-Syntax) werden pro
  betroffener Migration/Repository-Methode über
  `connection.getMetaData().getDatabaseProductName()` behandelt, nicht
  über eine SQL-Abstraktionsschicht.

## Konsequenzen

- Mehr Handarbeit pro Query als mit einem ORM, aber volle Kontrolle über
  die erzeugten Queries und keine ORM-Lernkurve/-"Magie" für Contributor.
- Dialektunterschiede müssen manuell behandelt werden, wo sie auftreten -
  siehe `AuditSchemaMigration`/`JdbcPlayerProfileRepository` als Muster.
  Das ist bewusst lokal gehalten statt in einer generischen Abstraktion,
  solange die Anzahl betroffener Stellen klein bleibt.
- Flyway/Liquibase hätten mehr Features (Checksums, Rollback-Skripte)
  mitgebracht, aber auch eine zusätzliche Abhängigkeit und eigene
  Konfigurationsdatei für einen Anwendungsfall, den `MigrationRunner` mit
  ~100 Zeilen Code abdeckt.

## Alternativen

- **JPA/Hibernate:** Würde Boilerplate bei komplexen Objektgraphen sparen,
  aber die Domain-Modelle sind bewusst einfache, unveränderliche Records -
  ORM-Entity-Mapping (mutable Proxies, Lazy Loading, Session-Semantik)
  passt nicht zu diesem Modell und würde es unter Druck setzen, mutable zu
  werden.
- **jOOQ oder ein anderer Query-Builder:** Reduziert String-SQL-Fehler,
  aber Codegen-Schritt/zusätzliche Abhängigkeit für eine überschaubare
  Anzahl Queries noch nicht gerechtfertigt. Kann bei wachsender
  Query-Komplexität neu bewertet werden.
