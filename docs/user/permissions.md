# Permissions

Every action UniversalAdmin can perform has its own node, so you can give a
helper "kick and mute" without "ban", or a builder "world time and weather"
without anything else. Nodes are registered at runtime (including those of
extensions), so LuckPerms and other permission plugins list and
auto-complete them. Without a permission plugin, every node except
`universaladmin.reports.create` is available to operators only.

Wildcards work as usual - `universaladmin.*` grants everything,
`universaladmin.moderation.*` all of moderation.

## Role templates

Copy-paste starting points for LuckPerms (`/lp group <group> permission set
<node>`). Adjust to taste.

**Helper** - answers reports, warns, mutes, kicks:

```
universaladmin.menu.open
universaladmin.reports.view
universaladmin.reports.manage
universaladmin.chat.staff
universaladmin.moderation.use
universaladmin.moderation.view
universaladmin.moderation.notify
universaladmin.moderation.notes
universaladmin.moderation.kick
universaladmin.moderation.warn
universaladmin.moderation.mute
universaladmin.moderation.tempmute
universaladmin.moderation.unmute
universaladmin.moderation.freeze
universaladmin.moderation.unfreeze
universaladmin.moderation.vanish
universaladmin.bypass.vanish
universaladmin.players.view
universaladmin.players.teleport
universaladmin.network.view
universaladmin.network.goto
```

**Moderator** - everything a helper can, plus bans, staff mode and inventories:

```
universaladmin.moderation.*
universaladmin.bypass.vanish
universaladmin.reports.*
universaladmin.chat.*
universaladmin.players.view
universaladmin.players.teleport
universaladmin.players.inventory.view
universaladmin.players.enderchest.view
universaladmin.audit.view
universaladmin.whitelist.view
universaladmin.whitelist.add
universaladmin.network.view
universaladmin.network.goto
universaladmin.network.send
```

The `network.*` nodes only matter with [network mode](network.md).

**Admin** - everything:

```
universaladmin.*
```

## Every node

| Node | Default | What it allows |
|---|---|---|
| **Core** | | |
| `universaladmin.menu.open` | op | Open the `/admin` main menu |
| `universaladmin.status` | op | `/admin status`, `/admin modules` |
| `universaladmin.reload` | op | `/admin reload` |
| `universaladmin.update.notify` | op | Be told when a new version is out |
| `universaladmin.update.apply` | op | `/admin update` |
| `universaladmin.settings.manage` | op | Settings GUI and `/admin settings` |
| **Players** | | |
| `universaladmin.players.view` | op | Player browser, profiles, `/admin info` |
| `universaladmin.players.ip` | op | See a player's IP address |
| `universaladmin.players.teleport` | op | Teleport to/with players |
| `universaladmin.players.heal` / `.feed` / `.extinguish` | op | Heal, feed, extinguish |
| `universaladmin.players.effects.add` / `.remove` / `.clear` | op | Potion effects |
| `universaladmin.players.set.health` / `.food` / `.xp` / `.level` | op | Set values |
| `universaladmin.players.fly.toggle` / `.fly.speed` / `.walk-speed` | op | Flight and speed |
| `universaladmin.players.glow` / `.gravity` / `.collision` | op | Toggles |
| `universaladmin.players.gamemode` | op | Gamemode |
| `universaladmin.players.inventory.view` / `.inventory.edit` | op | Inventory editor (edit includes clearing) |
| `universaladmin.players.enderchest.view` / `.enderchest.edit` | op | Ender chest editor |
| **Moderation** | | |
| `universaladmin.moderation.use` | op | Moderation GUI, punishment wizard |
| `universaladmin.moderation.view` | op | Punishment history, `/check` |
| `universaladmin.moderation.kick` | op | Kick |
| `universaladmin.moderation.ban` / `.tempban` / `.ipban` | op | Bans |
| `universaladmin.moderation.mute` / `.tempmute` | op | Mutes |
| `universaladmin.moderation.warn` / `.removewarn` | op | Warnings |
| `universaladmin.moderation.punish` | op | Punishment templates (`/punish`); each step also needs its own punishment's node |
| `universaladmin.moderation.alts` / `.alts.forget` | op | Possible alts and the alt hint on join / delete a player's stored addresses |
| `universaladmin.bypass.alt-check` | op | Join although an account sharing the address is banned (with `moderation.alts.refuse-banned-alts`) |
| `universaladmin.moderation.unban` / `.unmute` | op | Lift bans/mutes |
| `universaladmin.moderation.freeze` / `.unfreeze` | op | Freeze |
| `universaladmin.moderation.notes` | op | Staff notes; "player with notes joined" alert |
| `universaladmin.moderation.import` | op | `/admin import` - import punishments from other plugins |
| `universaladmin.moderation.notify` | op | Be told when staff punish or pardon someone |
| `universaladmin.moderation.vanish` | op | Vanish yourself |
| `universaladmin.bypass.vanish` | op | See vanished players - also in network lists, `/admin find` and counts |
| `universaladmin.moderation.godmode` | op | Godmode for yourself |
| `universaladmin.moderation.collision` | op | No-collision for yourself |
| `universaladmin.moderation.staffmode` | op | Staff mode |
| `universaladmin.moderation.staffmode.recover` | op | `/admin staff recover` |
| **Reports** | | |
| `universaladmin.reports.create` | **everyone** | `/report` |
| `universaladmin.reports.view` | op | See reports, get notified about new ones |
| `universaladmin.reports.manage` | op | Claim, resolve, reject, teleport to reports |
| `universaladmin.reports.bypass-limits` | op | No report cooldown or open-report limit |
| **Chat** | | |
| `universaladmin.chat.staff` | op | Read and write staff chat |
| `universaladmin.chat.manage` | op | Lock, slow mode, clear chat |
| `universaladmin.chat.bypass` | op | Chat while locked/slowed; history survives a clear |
| **Nick** | | |
| `universaladmin.nick.use` | op | Nick yourself |
| `universaladmin.nick.others` | op | End other players' nicks |
| `universaladmin.nick.see` | op | See the real name behind a nick (player list, profile, `/nick list`) |
| **Server** | | |
| `universaladmin.server.view` | op | Server dashboard, `/admin server status` |
| `universaladmin.server.broadcast` | op | Broadcasts, titles, action bars |
| `universaladmin.server.maintenance` | op | Maintenance mode and its allow-list |
| `universaladmin.server.maintenance.network` | op | The whole network's maintenance and its allow-list (network mode; also needs `server.maintenance`) |
| `universaladmin.server.shutdown` / `.restart` | op | Shutdown/restart with countdown |
| `universaladmin.bypass.maintenance` | op | Join during maintenance - this server's and the network's |
| **Worlds** | | |
| `universaladmin.worlds.view` / `.view.seed` | op | World browser; the seed separately |
| `universaladmin.worlds.teleport` | op | Teleport to a world's spawn |
| `universaladmin.worlds.spawn.set` / `.time.set` / `.weather.set` / `.difficulty.set` | op | World settings |
| `universaladmin.worlds.border.manage` / `.gamerule.manage` | op | Border, gamerules |
| **Whitelist** | | |
| `universaladmin.whitelist.view` / `.toggle` / `.add` / `.remove` | op | Whitelist |
| `universaladmin.whitelist.temporary` | op | Whitelist with an expiry date |
| `universaladmin.whitelist.network` | op | Manage the network whitelist (with `.toggle`/`.add`/`.remove`) |
| `universaladmin.bypass.network-whitelist` | op | Join while the network whitelist is on without being on it |
| **Performance** | | |
| `universaladmin.performance.view` | op | Dashboard, `/admin perf`; receives performance alerts |
| `universaladmin.performance.entity-clear` | op | Entity clear |
| **Audit log** | | |
| `universaladmin.audit.view` | op | Audit log list, `/admin audit` |
| `universaladmin.audit.details` | op | Full detail of an entry (old/new values, metadata) |
| **Network** (only with [network mode](network.md)) | | |
| `universaladmin.network.view` | op | Network menu, `/admin network`, `/admin find` |
| `universaladmin.network.goto` | op | Go to another server or to a player there (`/admin tp`, `/admin connect`) |
| `universaladmin.network.send` | op | Move other players between servers (`/admin tphere`, `/admin send`) |
| `universaladmin.network.alert` | op | `/admin alert` on every server |
| **Discord extension** | | |
| `universaladmin.discord.manage` | op | `/admin discord` |
| **Proxy plugins** (on the proxy) | | |
| `universaladmin.velocity.status` | - | `/uavelocity` (Velocity) |
| `universaladmin.bungee.status` | - | `/uabungee` (BungeeCord) |

## How checks work

GUI buttons and commands only *hide* what you may not use; the real check
happens when the action runs (`ActionExecutor`), so a permission can never
be bypassed by a stale GUI or a typed command. Every check goes through
Bukkit's normal permission system, so any permission plugin works.

## For developers

A module or extension registers its nodes through
`ModuleContext#registerPermission` - never in `plugin.yml`. See
[docs/extensions/getting-started.md](../extensions/getting-started.md).
