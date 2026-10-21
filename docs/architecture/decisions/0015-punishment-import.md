# 0015 - Importing Punishments from Other Plugins

## Status

Accepted (1.2.0)

## Context

A server that already uses LiteBans, AdvancedBan, the vanilla ban lists or
EssentialsX has years of bans, mutes and warnings there. Without a way to
bring them along, switching to UniversalAdmin means losing that history or
running two plugins side by side. The sources differ a lot: LiteBans and
AdvancedBan keep their data in MySQL or in an embedded database file (H2
for LiteBans, HSQLDB for AdvancedBan), the vanilla lists are JSON, and
EssentialsX keeps one YAML file per player.

## Decision

- **One importer per source, one service for all.** A
  `PunishmentImporter` only reads its source and translates each entry into
  an `ImportedPunishment` or `ImportedNote`, streamed to an `ImportSink`.
  `ImportService` does everything else the same way for every source:
  preview, de-duplication, batched writing, adding unknown players, the
  audit entry.
- **Preview first.** `/admin import <source>` reads the whole source and
  shows what would be imported (per type, how many are in force, what is
  skipped and why) without writing anything; only
  `/admin import <source> confirm` writes. The import itself is an action
  (`moderation.import`, permission `universaladmin.moderation.import`), so
  it is audited with its counts.
- **Repeatable without duplicates.** Every imported entry keeps its source
  and an id from the source (`ban:123`, `history:45`, `player:<uuid>`) in
  `ua_import_records`, and in the punishment's metadata. A batch of up to
  500 entries and its records are written in one transaction; running an
  import again - after an interruption, or after the old plugin recorded
  more punishments - imports only what is new.
- **Embedded databases are read with the source plugin's own driver.** An H2
  or HSQLDB file can only be read by a driver version compatible with the
  one that wrote it, and shipping both would add several megabytes to every
  installation for a one-time task. The importer looks for the driver class
  (by class file name, so relocated copies are found too) in the source
  plugin's jar, its folder and the server's `libraries` folder, loads it in
  its own class loader, and opens the file read-only without taking the
  file lock - the source plugin may still be running in the same server.
  The class loader is closed with the connection. MySQL/MariaDB and SQLite
  use the drivers UniversalAdmin already ships.
- **No equivalent, no guessing.** Entries UniversalAdmin cannot represent
  are skipped and counted (wildcard IP bans, IP-only mutes) - except jails
  (EssentialsX) and notes (AdvancedBan), which become staff notes so the
  information is not lost.
- **Vanilla entries are moved.** After a vanilla import the imported entries
  are removed from `banned-players.json`/`banned-ips.json`; otherwise the
  server would keep enforcing its own copy and an unban in UniversalAdmin
  would not work. Other sources are left untouched - their plugin is
  removed by the server owner.
- **Players become known.** Players of imported punishments who never
  joined a server using this database are added to the player list
  (`PlayerDirectory#addIfAbsent`, new in API 1.2), so `/admin unban <name>`
  finds them. Existing profiles are never changed.
- **No enforcement on import.** Importing does not kick anyone. Imported
  bans and mutes apply from the next join or chat message, like any other
  punishment in the database.

## Consequences

- Importing from an H2 or HSQLDB file needs the old plugin's jar (or its
  downloaded libraries) on the server until the import is done; the error
  message says so.
- A source format change in a future LiteBans or AdvancedBan version can
  break its importer; columns are read only if present, so additions do
  not.
- IP bans without a known player get a UUID derived from the address (see
  `ImportTargets`). It is never a real player's id; enforcement only looks
  at the address.

## Alternatives

- **Shipping the H2 and HSQLDB drivers.** Simplest to use, but several
  megabytes in every jar, and still the wrong version for some files.
- **Asking the server owner to export to MySQL first.** Works for LiteBans
  (it has an export), not for AdvancedBan, and is one more step in the way
  of switching.
- **Importing through the source plugin's API.** Neither plugin offers an
  API that returns the full history, and it would need the plugin enabled.
