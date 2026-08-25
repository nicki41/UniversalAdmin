# 0003 - Repository Pattern over JDBC Instead of an ORM

## Status

Accepted

## Context

Storage has to support SQLite (default, zero setup) and optionally
MySQL/MariaDB, be fully async (no blocking on the Paper main thread), and
must not let SQL queries leak into services/GUI/commands. The data volume
and schema complexity of an admin plugin is modest (a handful of tables per
module), not that of an enterprise system.

## Decision

- A `Repository<T, ID>` interface (async, `CompletableFuture`-based) as the
  only persistence abstraction services/actions know about.
- Concrete implementations write raw JDBC with `PreparedStatement` (no
  ORM/Hibernate/JPA), encapsulated in `*Repository` implementation classes
  in a `jdbc` subpackage.
- `HikariCP` as the connection pool, `sqlite-jdbc` and `mariadb-java-client`
  as the only drivers (the latter also for MySQL, see
  [../storage.md](../storage.md)).
- `Migration`/`MigrationRunner` for versioned, forward-only schema
  management with a `schema_version` table, instead of an external
  migration tool (Flyway/Liquibase).
- Dialect differences (auto-increment, upsert syntax) are handled per
  affected migration/repository method via
  `connection.getMetaData().getDatabaseProductName()`, not through a SQL
  abstraction layer.

## Consequences

- More manual work per query than with an ORM, but full control over the
  generated queries and no ORM learning curve/"magic" for contributors.
- Dialect differences have to be handled manually wherever they occur - see
  `AuditSchemaMigration`/`JdbcPlayerProfileRepository` as the pattern.
  Deliberately kept local instead of in a generic abstraction, as long as
  the number of affected spots stays small.
- Flyway/Liquibase would have brought more features (checksums, rollback
  scripts), but also an additional dependency and its own configuration
  file for a use case `MigrationRunner` covers in about 100 lines of code.

## Alternatives

- **JPA/Hibernate:** would save boilerplate for complex object graphs, but
  the domain models are deliberately simple, immutable records - ORM entity
  mapping (mutable proxies, lazy loading, session semantics) doesn't fit
  this model and would push it toward becoming mutable.
- **jOOQ or another query builder:** reduces string-SQL errors, but a
  codegen step/additional dependency isn't justified yet for a modest
  number of queries. Can be reconsidered if query complexity grows.
