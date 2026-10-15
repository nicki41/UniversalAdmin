# Configuration

Two files, generated on first start:

- `plugins/UniversalAdmin/config.yml` - all settings
- `plugins/UniversalAdmin/lang/en_US.yml`, `plugins/UniversalAdmin/lang/de_DE.yml` - translations (see [Localization](#localization))

Every value below is a registered, typed, validated setting (see
[docs/development/settings.md](../development/settings.md) for how that
works internally) - an invalid value never crashes the server, it logs a
warning and falls back to its default.

**Easiest way to change anything:** in-game via `/admin` → Settings, or
`/admin settings set <path> <value>` - see
[modules/settings.md](modules/settings.md). The shipped `config.yml` lists
every setting of every module with a comment; the module docs describe
their own settings in detail.

## `general`

| Key | Type | Default | Notes |
|---|---|---|---|
| `general.language` | locale code | `en_US` | Must look like `xx_XX`. Reloadable live. |

## `database`

| Key | Type | Default | Restart required |
|---|---|---|---|
| `database.type` | `sqlite` \| `mysql` | `sqlite` | yes |
| `database.file` | string | `data.db` | yes |
| `database.host` | string | `localhost` | yes |
| `database.port` | integer, 1-65535 | `3306` | yes |
| `database.database` | string | `universaladmin` | yes |
| `database.username` | string | `universaladmin` | yes |
| `database.password` | string | `""` | yes |
| `database.ssl` | boolean | `true` | yes |
| `database.pool-size` | integer, 1-100 | `10` | yes |

`type: mysql` is also used for MariaDB - the bundled driver speaks
MariaDB's wire protocol, which MySQL servers accept too, so there's no
separate `mariadb` value. All database settings require a restart: a live
connection-pool swap mid-session is exactly the kind of "unsafe to change
live" setting `/admin reload` deliberately refuses to attempt (see
[Reload](#reload)).

See [Database](database.md) for SQLite vs. MySQL/MariaDB in more depth -
when to use which, setting up a MySQL user, what happens if the database
is unreachable at startup, and backups.

## `gui`

| Key | Type | Default | Notes |
|---|---|---|---|
| `gui.page-size` | integer, multiple of 9, 9-54 | `45` | Reloadable live. |
| `gui.confirmations` | boolean | `true` | Ask before destructive GUI actions. Reloadable live. |

`gui.confirmations` applies to every confirmation dialog of every module.

## `audit`

| Key | Type | Default | Notes |
|---|---|---|---|
| `audit.enabled` | boolean | `true` | Whether `AuditService` records events. Reloadable live. |
| `audit.retention-days` | integer | `0` | How many days of audit history to keep; `0` = unlimited (never deleted). Cleanup runs hourly via `AuditLogModule`, not on every tick - see [docs/user/audit-log.md](audit-log.md#retention). |

## `modules`

| Key | Type | Default | Restart required |
|---|---|---|---|
| `modules.players` | boolean | `true` | yes |
| `modules.moderation` | boolean | `true` | yes |
| `modules.reports` | boolean | `true` | yes |
| `modules.chat` | boolean | `true` | yes |
| `modules.server` | boolean | `true` | yes |
| `modules.worlds` | boolean | `true` | yes |
| `modules.whitelist` | boolean | `true` | yes |
| `modules.performance` | boolean | `true` | yes |
| `modules.audit` | boolean | `true` | Controls the **Audit Log** module (its GUI/commands over `AuditService`) - not the same as `audit.enabled` above, which controls whether `AuditService` records anything at all. |
| `modules.settings` | boolean | `true` | yes |

Setting one to `false` means that module is never even registered with
`ModuleManager` - not disabled-after-the-fact, simply never brought up.
Requires a restart: modules are only registered once, at plugin startup.

## `commands`

| Key | Type | Default | Notes |
|---|---|---|---|
| `commands.shortcuts.enabled` | boolean | `true` | Top-level shortcuts such as `/kick` next to `/admin kick`. Restart required. |
| `commands.shortcuts.disabled` | list | `[]` | Shortcut labels to skip, e.g. `[ban, mute]`. Restart required. |

`/report` and `/staffchat` are always registered; `/universaladmin:<label>`
always works. See [commands.md](commands.md).

## Module sections

Each module keeps its settings in its own section - `players.*`,
`moderation.*`, `reports.*`, `chat.*`, `server.*`, `whitelist.*`,
`performance.*` - documented in the module pages:
[players](modules/players.md#settings),
[moderation](modules/moderation.md#settings),
[staff tools](modules/staff-tools.md),
[reports](modules/reports.md#settings), [chat](modules/chat.md#settings),
[server](modules/server.md), [whitelist](modules/whitelist.md),
[performance](modules/performance.md#settings).

## `performance`

| Key | Type | Default | Notes |
|---|---|---|---|
| `performance.refresh-interval` | duration, 1s-10m | `5s` | How often the Performance module refreshes its TPS/MSPT/memory/world/entity snapshot. Restart required. |

Alert thresholds and entity-clear protection: see
[docs/user/modules/performance.md](modules/performance.md#settings).

Duration values accept a bare number of seconds, or a suffix: `30s`, `5m`,
`1h`, `2d`, `250ms`.

## `maintenance`

| Key | Type | Default | Notes |
|---|---|---|---|
| `maintenance.enabled` | boolean | `false` | Initial maintenance state on a fresh install; afterwards `/admin maintenance on\|off` and its stored state win. |
| `maintenance.kick-message` | string (MiniMessage) | `<red>Server is currently under maintenance.` | Default message for players who cannot join. Reloadable live. |

## `update`

| Key | Type | Default | Notes |
|---|---|---|---|
| `update.check-for-updates` | boolean | `true` | Check GitHub for a newer stable release and tell staff. `/admin update` works either way. |
| `update.check-interval` | duration, 1h-7d | `6h` | Restart required. |

## `telemetry`

Anonymous usage statistics. The full payload, the opt-out, and everything that
is deliberately never collected are documented in
[docs/user/telemetry.md](telemetry.md) - that document is the complete list,
and nothing is collected that isn't in it.

| Key | Type | Default | Notes |
|---|---|---|---|
| `telemetry.enabled` | boolean | `true` | `false` means no request of any kind, no installation id, no timer. Re-read on **every** heartbeat, so `/admin reload` applies a change immediately - no restart needed. The endpoint itself is not configurable - see [telemetry.md](telemetry.md). |
| `telemetry.interval` | duration, 5m-24h | `30m` | Time between heartbeats; a random extra of up to half this value is added to each wait. The first heartbeat additionally waits ~5-7 minutes after startup. Restart required. |

## `web`

| Key | Type | Default | Notes |
|---|---|---|---|
| `web.enabled` | boolean | `false` | Reserved for the future web app (see [docs/architecture/web-future.md](../architecture/web-future.md)). No web server exists yet - this flag currently does nothing. |

## Reload

```
/admin reload
```

Requires `universaladmin.reload` (default: op). Re-reads `config.yml`,
migrates it if its `config-version` is behind, and re-resolves every
setting:

- Settings that don't need a restart apply immediately.
- Settings that do (see the tables above) keep their previous value; the
  command reports which ones are "pending restart".
- A value that fails to parse or validate falls back to its default and is
  reported, same as at startup - reload never leaves the plugin in a
  broken state.

It also re-reads the language files in `lang/`.

This is **not** Bukkit's `/reload` - it never touches any other plugin or
the server's own state, only UniversalAdmin's own files.

## Config versioning

`config-version` at the top of the file is managed automatically - don't
edit it by hand. Upgrading UniversalAdmin never blindly overwrites your
existing `config.yml`; when the config's shape changes, a migration adjusts
your file in place. Version 2 (UniversalAdmin 1.0) moved module settings
under their module (`vanish.*` → `moderation.vanish.*`, `countdown.*` →
`server.countdown.*`, `gui.max-results` → `players.gui.max-results`, ...)
and added the `commands` section. See
[docs/development/settings.md](../development/settings.md#config-file-versioning).

## Localization

`lang/en_US.yml` is the default and the fallback for every other locale;
`lang/de_DE.yml` ships alongside it. `general.language` selects the active
one.

**Your files override, the plugin fills the gaps.** The files in
`plugins/UniversalAdmin/lang/` are yours - change any text there. Every key
you don't have (or deleted) comes from the copy built into the plugin, so
texts added by an update show up automatically. You can delete every key
you did not change.

**Fallback chain** for any single message key: your file for the active
locale → the built-in copy of that locale → your `en_US.yml` → the built-in
`en_US` → a visible `[missing: the.key]` marker (logged once).

**Adding a locale:** drop a new `lang/xx_XX.yml` file (anything you skip
falls back to `en_US`), set `general.language` to match, `/admin reload`.

A broken language file (YAML error) is skipped with an error in the
console - the plugin keeps running with the built-in texts.

**Placeholders and apostrophes:** `{0}`, `{1}`, ... are replaced by values.
In a text that has placeholders, write an apostrophe as `''` (two single
quotes). Values typed by players (reasons, report texts, chat) are always
shown as plain text and can never add colours or click events.

Message values may use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
markup (e.g. `<green>...</green>`) - see the shipped `lang/en_US.yml` for
examples. This does **not** apply to a future web client, which would
render the same underlying message key differently (plain text/HTML
instead of an Adventure `Component`) - see
[docs/architecture/web-future.md](../architecture/web-future.md).
