# Moderation

The Moderation module is a fully standalone punishment system - kick, ban,
temp-ban, IP ban, mute, temp-mute, and warn, plus unban/unmute/remove-warn -
built entirely on the existing Module/GUI/Action/Audit/Settings
infrastructure, the same way Players is. It has **no dependency on any
external moderation plugin** (Essentials, LiteBans, AdvancedBan, or
otherwise); every punishment is its own row in UniversalAdmin's own
database, every mutation is an `Action` run through `ActionExecutor` (so
it's permission-checked and audited the same way regardless of frontend),
and enforcement (join-time ban check, chat-time mute check) is UniversalAdmin's
own Bukkit/Paper listeners.

Open via `/admin` → **Moderation** (needs `universaladmin.moderation.use`),
or the **Moderate** button on a player's profile page (Players module, if
both modules are enabled - see "Cross-module link" below).

This module also owns Vanish, Freeze, Godmode, No-Collision, and Staff Mode
- see [docs/user/modules/staff-tools.md](staff-tools.md) for those. Freeze
is a punishment type here (see below); the rest are self-directed staff
toggles documented separately since they aren't "punish another player"
actions.

## Punishment model

Every punishment type is one row in the `punishments` table (see
"Database" below), including kicks and warns - "Recent Punishments" is a
single query over one table, not a union of several. A punishment record
carries: id, type, target UUID, target's last known name, target IP
(IP bans only), acting UUID/name (null actor UUID for console/system),
reason, created-at, expires-at (`null` = permanent), active, revoked-at,
revoked-by, and a small metadata map.

"Currently in force" is **never** decided by a stored flag alone - every
check (join, chat, the "Active Punishments" list) computes it live as
`active AND (expires_at IS NULL OR expires_at > now)`. The `active` column
itself only changes on an explicit revoke (Unban/Unmute/Remove Warning) or
the optional hourly housekeeping sweep that flips overdue rows for cleaner
history display - never on a per-punishment scheduled timer. This is
deliberate: a fleet of Bukkit tasks, one per active temp-ban, doesn't scale
and doesn't survive a restart cleanly; a query-time expiry check does both.

## Punishment types

| Type | Duration | Notes |
|---|---|---|
| Kick | instant | Also recorded for history, immediately inactive |
| Ban | permanent | |
| Temp Ban | temporary | Requires a duration |
| IP Ban | permanent or temporary | Captures the target's current IP - requires them to be online |
| Mute | permanent | Blocks chat, not commands |
| Temp Mute | temporary | Requires a duration |
| Warn | permanent | Removed one at a time via Remove Warning, never bulk-cleared |
| Freeze | permanent | Blocks movement/teleport/interaction/inventory/commands (configurable) - see [staff-tools.md](staff-tools.md#freeze) |

Issuing a Ban/IP Ban immediately kicks the target if they're online; a Mute
sends them a notice instead (they stay connected, just can't chat).

## Enforcement

- **Join** - `ModerationJoinListener` handles `AsyncPlayerPreLoginEvent`
  (before a `Player` object even exists, off the main thread): checks for
  an active ban by UUID, then by IP if none was found by UUID, and denies
  the login with a localized message (reason + expiry, if temporary) if
  either matches.
- **Chat** - `ModerationChatListener` handles the modern Paper
  `io.papermc.paper.event.player.AsyncChatEvent` (not the deprecated Bukkit
  `AsyncPlayerChatEvent`): cancels the message and notifies the player if
  they have an active mute.

Both listeners are pure event-to-service-call translation - the actual
"is this in force" decision lives in `PunishmentService`, not the listener.

## GUI flow

Player → **Moderate** → **Type** → **Reason** → **Duration** (only for
Temp Ban/Temp Mute/IP Ban) → **Confirmation** → the action runs.

- **Type** - one button per punishment type plus Unban/Unmute/Remove
  Warning, each hidden unless the viewer holds the matching permission.
  Unban/Unmute run immediately (no reason/duration needed to revoke); Remove
  Warning opens the target's warnings list so a specific one can be picked.
- **Reason** - a preset list (`moderation.reasons.presets`, see "Settings")
  plus a "Custom..." option that opens a free-text prompt.
- **Duration** - only for types that need one; a preset list
  (`moderation.durations.presets`) plus "Custom...", parsed by
  `DurationParser` (`10m`, `1h`, `3d`, compound forms like `1d12h`, or
  `permanent`/`perm`).
- **Confirmation** - a summary of target/reason/expiry, skipped if
  `core.gui.confirmations` is off (same setting every other confirmation in
  the plugin respects).

The Moderation home page (`/admin` → **Moderation**) lists **Active
Punishments**, **Recent Punishments**, **Warnings**, **Bans** (Ban/Temp
Ban/IP Ban), and **Mutes** (Mute/Temp Mute) - each an async, paginated,
newest-first list capped at 200 rows per query, loaded once and paginated
client-side (the same "load a bounded batch, slice in memory" shape every
other list page in this plugin uses). Clicking a row opens its detail page,
which shows the full record and, if it's still active, an Unban/Unmute/
Remove Warning button gated on the matching permission.

## Cross-module link

The Players module's profile page shows a **Moderate** button that opens
this module's wizard directly on that target - but only if the Moderation
module is actually enabled. Neither module imports the other's internal
classes: `ModerationModule` publishes a small `ModerationPlayerLink`
interface through `ServiceRegistry`, and `PlayerProfilePage` looks it up
optionally (`ServiceRegistry.get`, not `.require`) - if Moderation is
disabled, the lookup returns empty and the button simply doesn't render.
Players works completely standalone either way.

## Policy hook (staff hierarchy)

There is no rank/hierarchy system in UniversalAdmin today, and this module
does not build one - but every punishing action checks a `ModerationPolicy`
(`canPunish(actor, type, targetId)`) before persisting anything. The
default implementation allows everything; `ModerationModule` looks up
`ServiceRegistry` for an existing `ModerationPolicy` before falling back to
that default, so a future rank/hierarchy extension (declaring a dependency
on this module) can register its own policy and every action here picks it
up automatically, with zero hierarchy logic shipped in this module itself.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.moderation.use` | op | Open the Moderation GUI |
| `universaladmin.moderation.view` | op | View punishment history, warnings, bans, and mutes |
| `universaladmin.moderation.kick` | op | Kick a player |
| `universaladmin.moderation.ban` | op | Permanently ban a player |
| `universaladmin.moderation.tempban` | op | Temporarily ban a player |
| `universaladmin.moderation.ipban` | op | Ban a player's IP address |
| `universaladmin.moderation.mute` | op | Permanently mute a player |
| `universaladmin.moderation.tempmute` | op | Temporarily mute a player |
| `universaladmin.moderation.warn` | op | Warn a player |
| `universaladmin.moderation.unban` | op | Revoke an active ban |
| `universaladmin.moderation.unmute` | op | Revoke an active mute |
| `universaladmin.moderation.removewarn` | op | Remove a single warning |
| `universaladmin.moderation.freeze` | op | Freeze a player |
| `universaladmin.moderation.unfreeze` | op | Unfreeze a player |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list, and [staff-tools.md](staff-tools.md#permissions)
for Vanish/Godmode/Collision/Staff Mode's nodes.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `moderation.reasons.presets` | Cheating, Griefing, Spam, Advertising, Harassment, Bug Abuse, Other | Reason presets offered in the GUI wizard |
| `moderation.durations.presets` | 10m, 30m, 1h, 6h, 1d, 3d, 7d, 30d, permanent | Duration presets offered in the GUI wizard |

Both are ordinary lists, editable in `config.yml` like any other setting -
not a bespoke config surface. A custom reason/duration is always available
alongside the presets.

## Database

`punishments` (id, type, target_id, target_last_known_name, target_ip,
actor_id, actor_name, reason, created_at, expires_at, active, revoked_at,
revoked_by, metadata) - created by `ModerationPunishmentMigration` (version
1002); `ModerationPunishmentIndexMigration` (version 1003) indexes
`(target_id, type, active)` for the join/chat checks, `(active,
expires_at)` for the housekeeping sweep, and `target_ip` for the IP-ban
join check.

## Tests

`DurationParserTest` (every preset shape, compound durations, invalid
input, `permanent`/`perm`), `PunishmentServiceTest` (in-memory repository
fake - issuance, expiry, revoke), `JdbcPunishmentRepositoryTest` (real
temp SQLite - save/find/query, expiry-boundary correctness, the bulk
expiry sweep, and two punishments racing to revoke the same row
concurrently - only one may win, guarded by `WHERE id = ? AND active =
TRUE` on the `UPDATE`), and `ModerationActionsWiringTest` (permission/
module/validator wiring, mirroring `PlayerActionRegistrarTest`).
