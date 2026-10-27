# Importing Punishments

Switching from another plugin? `/admin import` brings its bans, mutes,
warnings and kicks along - with reasons, dates, who issued them, and what is
still in force.

| Source | What is imported | Where it reads from |
|---|---|---|
| **LiteBans** | bans (also IP bans), mutes, warnings, kicks - lifted ones with who lifted them | the database in `plugins/LiteBans/config.yml`: MySQL/MariaDB, or the H2 file `litebans.mv.db` |
| **AdvancedBan** | bans, IP bans, mutes, warnings, kicks; its notes become staff notes | MySQL (`UseMySQL: true`, `MySQL.yml`) or the HSQLDB files in `plugins/AdvancedBan/data/` |
| **Vanilla** | `banned-players.json` and `banned-ips.json` - also where EssentialsX keeps its bans | the server folder |
| **EssentialsX** | mutes; jails become staff notes (UniversalAdmin has no jails) | `plugins/Essentials/userdata/` |

## How to import

1. Keep the old plugin installed for now (it may even stay enabled) - an
   H2 or HSQLDB file can only be read with the old plugin's own driver.
2. `/admin import` - lists the sources found on this server, with a
   clickable preview for each.
3. `/admin import litebans` - the **preview**. Nothing is written yet:

   ```
   [UA] Import from LiteBans (MySQL litebans@db:3306) - nothing written yet:
   Bans: 1204 (312 in force)
   IP bans: 18 (5 in force)
   Mutes: 433 (12 in force)
   Warnings: 201 (40 in force)
   Kicks: 2010
   Players: 2876
   Skipped: 3 (no player: 1, no equivalent: 2, unreadable: 0)
   ```

4. `/admin import litebans confirm` - imports, with progress in chat, and
   ends with a summary. The import is one entry in the audit log, with the
   numbers.
5. Remove the old plugin (for vanilla, see below).

Works from the console and in game (permission
`universaladmin.moderation.import`, operators by default). Large imports
run in the background in batches of 500; the server keeps running normally.

## Good to know

- **Run it again any time.** Every imported entry is remembered. A second
  run - after an interruption, or because the old plugin was still in use
  for a few days - only imports what is new.
- **Nobody is kicked by the import.** Imported bans apply from the next
  join, imported mutes from the next chat message.
- **Players who never joined** this server (or network) are added to the
  player list, so `/admin unban <name>` and `/admin history <name>` find
  them.
- **Vanilla entries are moved**, not copied: after the import they are
  removed from `banned-players.json` and `banned-ips.json`. Otherwise the
  server would keep enforcing its own copy, and an unban in UniversalAdmin
  would not let the player back in. The preview says so before you confirm.
- **Networks:** run the import once, on any server - all servers share the
  database.
- **Not imported:** wildcard IP bans and IP-only mutes (UniversalAdmin has
  no equivalent - they are counted as "no equivalent"), entries without a
  player or address. AdvancedBan does not record who lifted a punishment,
  so lifted and expired entries both import as no longer in force.
- **Offline-mode servers:** AdvancedBan with its UUID fetcher switched off
  stores names instead of UUIDs; those entries get the offline-mode UUID.

## When something goes wrong

| Message | What to do |
|---|---|
| "No LiteBans data found on this server." | The plugin's folder or config is missing - copy `plugins/LiteBans/` (or `AdvancedBan/`, `Essentials/userdata/`) from the old server. |
| "... needs the H2 driver that comes with LiteBans." | Put the LiteBans jar back into `plugins/` until the import is done, restart, and run the import again. Same for AdvancedBan and HSQLDB. |
| "Could not open the database of LiteBans: ..." | Check the MySQL details in the old plugin's config - the import uses exactly those. |
| "... uses PostgreSQL, which UniversalAdmin cannot read." | Not supported yet - please [open an issue](https://github.com/universaladmin-mc/UniversalAdmin/issues) if you need it. |

How it works inside: [ADR 0015](../architecture/decisions/0015-punishment-import.md).
