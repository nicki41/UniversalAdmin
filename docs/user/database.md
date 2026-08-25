# Database

UniversalAdmin needs a database to persist anything (player profiles, audit
log entries, and whatever future modules add). It works out of the box with
no setup, and optionally supports MySQL/MariaDB for servers that already run
one. This page is the user-facing companion to
[docs/architecture/storage.md](../architecture/storage.md), which covers the
internal design.

## SQLite (default)

```yaml
database:
  type: sqlite
  file: data.db
```

No installation, no server process, no credentials. The file is created
inside the plugin's own data folder: `plugins/UniversalAdmin/data.db`.

Two extra files appear next to it once the plugin has started -
`data.db-wal` and `data.db-shm`. These are SQLite's write-ahead log, not
temporary junk: don't delete them while the server is running, and if you
copy the database while the server is stopped, copy all three files
together.

**When SQLite is the right choice:** almost always, for a single Paper
server. It's the default for a reason. Reach for MySQL/MariaDB only for the
cases below.

## MySQL / MariaDB (optional)

```yaml
database:
  type: mysql
  host: localhost
  port: 3306
  database: universaladmin
  username: universaladmin
  password: ""
  ssl: true
  pool-size: 10
```

`type: mysql` also works against a MariaDB server - the bundled driver
speaks MariaDB's wire protocol, which MySQL servers accept too, so there's
no separate `mariadb` value to set.

**When to use this instead of SQLite:**

- Several Paper servers (e.g. a proxy network) need to share the same
  UniversalAdmin data (player profiles, audit log) instead of each server
  having its own isolated SQLite file.
- You already operate a MySQL/MariaDB server for other plugins and prefer
  one centrally-managed database over a growing set of per-plugin SQLite
  files.

Create the database and a user with access to it yourself (UniversalAdmin
does not create the database itself, only its own tables inside it):

```sql
CREATE DATABASE universaladmin CHARACTER SET utf8mb4;
CREATE USER 'universaladmin'@'%' IDENTIFIED BY 'a-real-password';
GRANT ALL PRIVILEGES ON universaladmin.* TO 'universaladmin'@'%';
```

Set a real `password` in `config.yml` before switching `type` to `mysql` -
the shipped default is an empty string, which won't authenticate against
most MySQL setups (and shouldn't be used even if it did).

## Changing the type

Every `database.*` key requires a restart to take effect - see
[Configuration](configuration.md#database). Switching `type` does **not**
migrate existing data between SQLite and MySQL; UniversalAdmin starts the
new database empty (its own migrations create the tables, but not the rows)
and you'd move data over by hand if you need to keep it.

## Startup failure

If the configured database can't be reached at all - a bad SQLite path
that can't be created, an unreachable MySQL host, a wrong password - the
whole plugin fails to start and disables itself; it does not start in a
reduced, storage-less mode. Check the server log for the error, fix the
`database.*` settings, and restart. See
[docs/architecture/storage.md#health](../architecture/storage.md#health)
for why UniversalAdmin is deliberately strict about this instead of
limping along.

## Backups

UniversalAdmin does not implement its own backup feature. Back up the
database the same way you already back up the rest of your server:

- **SQLite:** stop the server (or use a tool that understands SQLite's
  WAL, so it doesn't copy the `.db` file mid-write) and copy `data.db`
  together with `data.db-wal`/`data.db-shm` if present.
- **MySQL/MariaDB:** use your existing database backup process
  (`mysqldump`, a managed database's snapshot feature, etc.) - the same one
  you'd use for any other plugin's tables in that database.

## Security

- Credentials in `config.yml` are protected the same way as any other
  Paper plugin's config: filesystem permissions on the server. There is no
  encrypted secret store today - see [SECURITY.md](../../SECURITY.md).
- The database password is never written to the server log, including at
  debug level, even when the connection fails.
- Every query UniversalAdmin runs uses parameterized `PreparedStatement`s -
  no user input is ever concatenated into SQL text.
