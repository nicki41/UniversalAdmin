# Permissions

UniversalAdmin registers its permission nodes at runtime (not statically in
`plugin.yml`) - see
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md)
for the rationale. Works with any standard permission plugin (LuckPerms and
similar) that doesn't require Vault in the core.

## Currently Registered Nodes

| Node | Module | Default | Description |
|---|---|---|---|
| `universaladmin.reload` | Core (no module) | op | Reload UniversalAdmin's configuration (`/admin reload`) |
| `universaladmin.menu.open` | Core (no module) | op | Open the `/admin` main menu (see [gui-framework.md](../development/gui-framework.md)) |
| `universaladmin.update.notify` | Core (no module) | op | Receive a notice when a new UniversalAdmin version is available |
| `universaladmin.update.apply` | Core (no module) | op | Download the latest UniversalAdmin release (`/admin update`) |
| `universaladmin.players.view` | Players | op | View player profiles (browser, profile page) |
| `universaladmin.players.ip` | Players | op | View a player's IP address |
| `universaladmin.players.teleport` | Players | op | Teleport players (admin↔player, world spawn, bed, coordinates) |
| `universaladmin.players.heal` | Players | op | Heal a player |
| `universaladmin.players.feed` | Players | op | Feed a player |
| `universaladmin.players.extinguish` | Players | op | Extinguish a player |
| `universaladmin.players.effects.clear` | Players | op | Clear a player's potion effects |
| `universaladmin.players.effects.add` | Players | op | Add a potion effect to a player |
| `universaladmin.players.effects.remove` | Players | op | Remove one potion effect from a player |
| `universaladmin.players.set.health` | Players | op | Set a player's health |
| `universaladmin.players.set.food` | Players | op | Set a player's food level |
| `universaladmin.players.set.xp` | Players | op | Set a player's experience progress |
| `universaladmin.players.set.level` | Players | op | Set a player's experience level |
| `universaladmin.players.fly.toggle` | Players | op | Toggle whether a player may fly |
| `universaladmin.players.fly.speed` | Players | op | Set a player's fly speed |
| `universaladmin.players.walk-speed` | Players | op | Set a player's walk speed |
| `universaladmin.players.glow` | Players | op | Toggle a player's glowing effect |
| `universaladmin.players.gravity` | Players | op | Toggle whether gravity affects a player |
| `universaladmin.players.collision` | Players | op | Toggle whether a player collides with entities |
| `universaladmin.players.gamemode` | Players | op | Change a player's gamemode |
| `universaladmin.players.inventory.view` | Players | op | View a player's main inventory, armor, and offhand |
| `universaladmin.players.inventory.edit` | Players | op | Edit or clear a player's main inventory, armor, and offhand |
| `universaladmin.players.enderchest.view` | Players | op | View a player's ender chest |
| `universaladmin.players.enderchest.edit` | Players | op | Edit a player's ender chest |
| `universaladmin.moderation.use` | Moderation | op | Open the Moderation GUI |
| `universaladmin.moderation.view` | Moderation | op | View punishment history, warnings, bans, and mutes |
| `universaladmin.moderation.kick` | Moderation | op | Kick a player |
| `universaladmin.moderation.ban` | Moderation | op | Permanently ban a player |
| `universaladmin.moderation.tempban` | Moderation | op | Temporarily ban a player |
| `universaladmin.moderation.ipban` | Moderation | op | Ban a player's IP address |
| `universaladmin.moderation.mute` | Moderation | op | Permanently mute a player |
| `universaladmin.moderation.tempmute` | Moderation | op | Temporarily mute a player |
| `universaladmin.moderation.warn` | Moderation | op | Warn a player |
| `universaladmin.moderation.unban` | Moderation | op | Revoke an active ban |
| `universaladmin.moderation.unmute` | Moderation | op | Revoke an active mute |
| `universaladmin.moderation.removewarn` | Moderation | op | Remove a single warning |
| `universaladmin.moderation.freeze` | Moderation | op | Freeze a player |
| `universaladmin.moderation.unfreeze` | Moderation | op | Unfreeze a player |
| `universaladmin.moderation.vanish` | Moderation | op | Toggle your own vanish status |
| `universaladmin.bypass.vanish` | Moderation | op | See vanished players |
| `universaladmin.moderation.godmode` | Moderation | op | Toggle your own godmode status |
| `universaladmin.moderation.collision` | Moderation | op | Toggle your own no-collision status |
| `universaladmin.moderation.staffmode` | Moderation | op | Enter/exit staff mode |
| `universaladmin.moderation.staffmode.recover` | Moderation | op | Manually recover a pending staff-mode snapshot (`/admin staff recover`) |
| `universaladmin.server.view` | Server | op | Open the server dashboard |
| `universaladmin.server.broadcast` | Server | op | Broadcast messages/titles/actionbars to every online player |
| `universaladmin.server.maintenance` | Server | op | Enable/disable maintenance mode and manage its allow-list |
| `universaladmin.server.restart` | Server | op | Restart the server (with confirmation/countdown) |
| `universaladmin.server.shutdown` | Server | op | Shut down the server (with confirmation/countdown) |
| `universaladmin.bypass.maintenance` | Server | op | Join the server while maintenance mode is enabled |
| `universaladmin.worlds.view` | Worlds | op | Open the world browser and profile pages |
| `universaladmin.worlds.view.seed` | Worlds | op | View a world's seed |
| `universaladmin.worlds.teleport` | Worlds | op | Teleport to a world's spawn |
| `universaladmin.worlds.spawn.set` | Worlds | op | Set a world's spawn point |
| `universaladmin.worlds.time.set` | Worlds | op | Set a world's time |
| `universaladmin.worlds.weather.set` | Worlds | op | Set a world's weather |
| `universaladmin.worlds.difficulty.set` | Worlds | op | Set a world's difficulty |
| `universaladmin.worlds.border.manage` | Worlds | op | Manage a world's border |
| `universaladmin.worlds.gamerule.manage` | Worlds | op | Change a world's gamerules |
| `universaladmin.whitelist.view` | Whitelist | op | View the whitelist status and members |
| `universaladmin.whitelist.toggle` | Whitelist | op | Enable/disable the whitelist |
| `universaladmin.whitelist.add` | Whitelist | op | Add a player to the whitelist |
| `universaladmin.whitelist.remove` | Whitelist | op | Remove a player from the whitelist |
| `universaladmin.whitelist.temporary` | Whitelist | op | Give a whitelist entry an expiration |
| `universaladmin.performance.view` | Performance | op | View performance diagnostics (dashboard, world/entity breakdown); also who receives alert notifications |
| `universaladmin.performance.entity-clear` | Performance | op | Clear non-player entities matching a filter, with preview and confirmation |
| `universaladmin.audit.view` | Audit Log | op | View the audit log (list) |
| `universaladmin.audit.details` | Audit Log | op | View full detail (old/new values, metadata) of an audit entry |
| `universaladmin.settings.manage` | Settings | op | Manage UniversalAdmin settings |

Aside from Players, Moderation, Server, Worlds, Whitelist, and Performance,
this list is deliberately coarse (one node per module) - the remaining
modules are still at skeleton level (see
[docs/architecture/modules.md](../architecture/modules.md)). Players,
Moderation, Server, Worlds, Whitelist, and Performance are the modules
where the finer-grained nodes have actually landed (e.g. separate
`.inventory.view`/`.inventory.edit` instead of a blanket `.manage`,
separate `.kick`/`.ban`/`.tempban`/`.ipban`/`.mute`/`.tempmute`/`.warn`/
`.unban`/`.unmute`/`.removewarn` instead of a blanket `.use`, separate
`.broadcast`/`.maintenance`/`.restart`/`.shutdown` instead of a blanket
`.manage`, a separate `.view.seed` next to `.view`, a separate
`.temporary` next to `.add`, or a separate `.entity-clear` next to
`.view`) - fully documented in
[docs/user/modules/players.md](modules/players.md#permissions),
[docs/user/modules/moderation.md](modules/moderation.md#permissions),
[docs/user/modules/server.md](modules/server.md#permissions),
[docs/user/modules/worlds.md](modules/worlds.md#permissions),
[docs/user/modules/whitelist.md](modules/whitelist.md#permissions), and
[docs/user/modules/performance.md](modules/performance.md#permissions).
More modules will follow this pattern as their feature scope grows.

## Default ("op")

Every current node defaults to `op` - without a permission plugin, only
server operators have access. With a permission plugin, nodes can be
granted to specific roles independent of op status.

## How a Permission Is Actually Checked

Every action that needs a permission declares it via
`ActionDefinition.Builder#permission(...)` at registration time (see
[docs/architecture/actions.md](../architecture/actions.md)) -
`ActionExecutor` checks it centrally before the action runs. There is
deliberately no code path that calls `player.hasPermission("...")` with a
raw string literal scattered through GUI/command code; every check goes
through the `PermissionEvaluator` the respective `Actor` carries.

Wildcards (e.g. a role with `universaladmin.*` in LuckPerms) need no logic
of their own - `PermissiblePermissionEvaluator` delegates directly to
Bukkit's `Permissible.hasPermission`, so they work automatically through
any installed permission plugin.

## Adding a New Permission (for Contributors)

See [docs/development/adding-module.md](../development/adding-module.md)
step 7 - a `PermissionDefinition` is registered in the respective module,
not entered in `plugin.yml`.
