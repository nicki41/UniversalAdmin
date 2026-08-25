# Server

The Server module is a live dashboard plus server-wide control: broadcasts,
maintenance mode, and confirmed shutdown/restart - built entirely on the
existing Module/GUI/Action/Audit/Settings infrastructure, no shortcuts
specific to a built-in module (see docs/development/architecture-rules.md's "Built-in Modules bleiben
Extension-freundlich"). Every mutation is an `Action` run through
`ActionExecutor`, so it is permission-checked and audited the same way
whether it's clicked in the GUI or run from `/admin server ...`.

Open via `/admin` → **Server** (needs `universaladmin.server.view`).

## Dashboard

Read-only, refreshed on every open/refresh click - never cached, never an
`Action` (nothing here is a mutation worth auditing):

- UniversalAdmin version, Paper build, Minecraft version, Java version
- Uptime
- Online / max players
- World count
- Used / max memory
- CPU: available processor count always shown; process CPU load shown only
  when the JVM exposes it reliably (`com.sun.management.OperatingSystemMXBean`),
  otherwise "n/a" - not every JVM guarantees this, hence "soweit zuverlässig"
- Database status (`ONLINE`/`DEGRADED`/`OFFLINE`, from `PluginStatus`)
- Enabled modules

## Broadcasts

Three independent actions, all gated on `universaladmin.server.broadcast`,
all MiniMessage-formatted admin-authored text (rendered via
`ComponentMessages`, same as every other in-game message):

- **Broadcast Message** - a chat message to every online player.
- **Broadcast Title** - a title/subtitle shown to every online player for a
  fixed 0.5s fade-in / 4s stay / 1s fade-out.
- **Broadcast Actionbar** - an action-bar message to every online player.

Implemented as `NotificationService#broadcast`/`broadcastTitle`/
`broadcastActionBar` - core, platform-wide methods any future module can
reuse, not something this module hand-rolled for itself.

## Maintenance Mode

Its own persisted system (`MaintenanceState`/`MaintenanceService`), **not**
a `SettingDefinition` - unlike ordinary settings, maintenance mode must flip
instantly from a GUI click or command, and `SettingsService` is read-only at
runtime (a setting only ever changes by editing `config.yml` and reloading).
`core.maintenance.enabled`/`core.maintenance.kick-message` (`CoreSettings`)
still exist and are used as the boot-time seed/default before the persisted
state has loaded, and as the default kick message.

State: enabled, reason (admin-facing only, shown on the dashboard/audit
log - never sent to a blocked player), message (MiniMessage kick-message
override; falls back to `core.maintenance.kick-message` if blank), and an
allow-list of player names (case-insensitive) who may join anyway.

- **Enable** (`universaladmin.server.maintenance`) - prompts for a reason,
  then whether to kick every currently-online player without bypass. New
  joins are denied (`PlayerLoginEvent`) with the effective kick message
  unless the joining player holds `universaladmin.bypass.maintenance` or
  their name is on the allow-list.
- **Disable** (`universaladmin.server.maintenance`) - reason/message are kept
  (so re-enabling later doesn't lose them), only `enabled` flips.
- **Allowed Players** (`universaladmin.server.maintenance`) - a
  comma-separated name list, replaced wholesale.

Maintenance mode has no dedicated `/admin server maintenance` console
command in this version - its richest action (enable) takes a multi-field
input, and `UniversalAdminCommand` deliberately never imports a specific
built-in module's types (the same rule `staff recover` already follows, see
that command's javadoc). It's fully manageable in-game via the GUI.

## Shutdown & Restart

Both gated on their own permission (`universaladmin.server.shutdown` /
`universaladmin.server.restart`), both require a dangerous confirmation
(`gui.confirmations`, skippable like any other GUI confirmation), both take
an optional reason, and both support a **Cancel** action while pending - a
countdown with no way to abort would be a footgun.

**Countdown** (`server.countdown.enabled`, default `true`): when enabled,
broadcasts a warning at each of `server.countdown.broadcast-steps`'
remaining-second marks (default `60, 30, 10, 5, 4, 3, 2, 1`), then executes.
When disabled, executes immediately after confirmation (still broadcasting
one final "shutting down/restarting now" message). Only one shutdown/restart
countdown can be active at a time.

**Shutdown** calls `Bukkit.shutdown()` - always works, no platform caveats.

**Restart limitations.** "Restart" calls `Server#restart()`, Paper/Spigot's
own built-in restart mechanism - not a shell command. This performs a clean
shutdown, but **whether the OS process actually relaunches afterward depends
entirely on how the server was started**: a looping start script, or a
process manager/hosting panel that restarts the process on exit (many do).
On a bare `java -jar paper.jar` with no such wrapper, "Restart" behaves
exactly like Shutdown - the process exits and nothing brings it back. There
is no universal, guaranteed-real restart target across every possible Paper
deployment, so this module uses the platform's own mechanism and documents
the limitation rather than hardcoding a shell command that would only work
for some setups and silently fail (or do the wrong thing) for others.

## Commands

`/admin server broadcast <message...>`, `/admin server shutdown [reason...]`,
`/admin server restart [reason...]`, `/admin server cancel` - console's only
path to these permissions (it has no GUI). `cancel` tries to cancel a
pending shutdown first, then a pending restart, whichever (if either) is
actually active.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.server.view` | op | Open the server dashboard |
| `universaladmin.server.broadcast` | op | Broadcast messages/titles/actionbars |
| `universaladmin.server.maintenance` | op | Enable/disable maintenance mode and manage its allow-list |
| `universaladmin.server.restart` | op | Restart the server (confirmation/countdown) |
| `universaladmin.server.shutdown` | op | Shut down the server (confirmation/countdown) |
| `universaladmin.bypass.maintenance` | op | Join the server while maintenance mode is enabled |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list, and [docs/architecture/actions.md](../architecture/actions.md)
for how a node on `ActionDefinition.Builder#permission(...)` gets enforced.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `server.countdown.enabled` | `true` | Broadcast a staged countdown before shutdown/restart, instead of running immediately after confirmation |
| `server.countdown.broadcast-steps` | `60, 30, 10, 5, 4, 3, 2, 1` | Remaining-seconds marks the countdown broadcasts a warning at |

Registered under the module's own `server` settings namespace (see
[docs/development/settings.md](../development/settings.md)). Neither
requires a restart to take effect.

## Database

`server_maintenance_state` (`ServerMaintenanceMigration`, version 1006) is a
single-row (`id = 1`) table holding the persisted `MaintenanceState` -
`enabled`, `reason`, `message`, `allowed_players` (comma-separated, no JSON
library dependency - same reasoning as the audit log's `MetadataJson`),
`updated_at`, `updated_by`. Deliberately not a
`dev.universaladmin.storage.Repository<T, ID>` - a singleton row has no
natural `ID`, see `MaintenanceStateRepository`'s javadoc.
