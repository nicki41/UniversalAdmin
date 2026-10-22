# Commands

Everything lives under **`/admin`** (aliases `/ua`, `/uadmin`). `/admin` on
its own opens the main menu; `/admin help` lists every command *you* may use,
clickable, and every command has tab completion.

Most commands also have a **shortcut**: `/kick Steve` is the same as
`/admin kick Steve`. Shortcuts can be switched off as a whole
(`commands.shortcuts.enabled: false`) or one by one
(`commands.shortcuts.disabled: [ban, mute]`) if another plugin should keep a
label - `/universaladmin:<label>` always works, whatever other plugins do.
`/report` and `/staffchat` are always registered.

**Arguments:** `<required>`, `[optional]`. A *player* can be anyone who has
been on the server before (online or offline) unless the command says
*online*. A *duration* looks like `30m`, `2h`, `7d`, `2w` or `1d12h`;
`permanent` means no expiry.

Every command runs the same audited action as the matching GUI button - the
permission listed is checked by the action itself, not just by the command.

## Core

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin` · `/admin menu` | - | `universaladmin.menu.open` | Main menu (console: status report) |
| `/admin help [page]` | - | - | Commands you may use |
| `/admin status` | - | `universaladmin.status` | Version, uptime, modules, database |
| `/admin modules` | - | `universaladmin.status` | Every module and extension with its state |
| `/admin reload` | - | `universaladmin.reload` | Reloads `config.yml` and the language files (never Bukkit's `/reload`) |
| `/admin update` | - | `universaladmin.update.apply` | Downloads the newest release; applied on the next restart |
| `/admin settings [list [section] \| get <path> \| set <path> <value> \| reset <path>]` | - | `universaladmin.settings.manage` | Settings GUI, or read/change settings from chat |

## Moderation

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin kick <player> [reason]` | `/kick` | `moderation.kick` | Kick (player must be online) |
| `/admin ban <player> [duration] [reason]` | `/ban` | `moderation.ban` (`moderation.tempban` with a duration) | Ban - `/ban Steve 7d griefing` is a temp-ban |
| `/admin tempban <player> <duration> [reason]` | `/tempban` | `moderation.tempban` | Temporary ban |
| `/admin ipban <player> [duration] [reason]` | `/ipban` | `moderation.ipban` | Ban the IP the player last joined from |
| `/admin mute <player> [duration] [reason]` | `/mute` | `moderation.mute` (`moderation.tempmute` with a duration) | Mute |
| `/admin tempmute <player> <duration> [reason]` | `/tempmute` | `moderation.tempmute` | Temporary mute |
| `/admin warn <player> [reason]` | `/warn` | `moderation.warn` | Warning (shown to the player, kept in history) |
| `/admin unban <player>` | `/unban` | `moderation.unban` | Lift a ban |
| `/admin unmute <player>` | `/unmute` | `moderation.unmute` | Lift a mute |
| `/admin unwarn <id>` | `/unwarn` | `moderation.removewarn` | Remove a warning (id from `/history`) |
| `/admin freeze <player> [reason]` | `/freeze` | `moderation.freeze` | Freeze in place |
| `/admin unfreeze <player>` | `/unfreeze` | `moderation.unfreeze` | Unfreeze |
| `/admin history <player> [page]` | `/history` | `moderation.view` | Full punishment history with ids |
| `/admin check <player>` | `/checkban` | `moderation.view` | Banned / muted / frozen right now? |
| `/admin moderate <player>` | `/punish` | `moderation.use` | Opens the punishment wizard GUI |
| `/admin notes <player> [add <text>]` · `/admin notes remove <id>` | - | `moderation.notes` | Staff notes about a player |
| `/admin vanish` | `/vanish`, `/v` | `moderation.vanish` | Toggle your vanish |
| `/admin godmode` | `/god` | `moderation.godmode` | Toggle your godmode |
| `/admin nocollision` | `/nocollision` | `moderation.collision` | Toggle your collision |
| `/admin staff` | `/staff` | `moderation.staffmode` | Enter/leave staff mode |
| `/admin staff recover [player]` | - | `moderation.staffmode.recover` | Restore a stuck staff-mode inventory |
| `/admin import [source] [confirm]` | - | `moderation.import` | Import punishments from LiteBans, AdvancedBan, the vanilla ban lists or EssentialsX - without `confirm` only a preview ([import.md](import.md)) |

(Permission names in these tables omit the leading `universaladmin.`.)

## Players

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin player <player>` | - | `players.view` | Opens the player's profile |
| `/admin info <player>` | `/seen`, `/whois` | `players.view` | First join, last seen, playtime, location, state |
| `/admin heal [player]` | `/heal` | `players.heal` | Full health |
| `/admin feed [player]` | `/feed` | `players.feed` | Full hunger and saturation |
| `/admin extinguish [player]` | `/extinguish` | `players.extinguish` | Stop burning |
| `/admin fly [player]` | `/fly` | `players.fly.toggle` | Toggle flight |
| `/admin gamemode <mode> [player]` | `/gm` | `players.gamemode` | `survival`/`creative`/`adventure`/`spectator`, `s`/`c`/`a`/`sp`, `0`-`3` |
| `/admin speed <fly\|walk> <0-10> [player]` | `/speed` | `players.fly.speed` / `players.walk-speed` | 1 = default fly, 2 = default walk speed |
| `/admin tp <player> [destination]` | `/tpto` | `players.teleport` | To a player, or one player to another. Network mode: a player on another server - you are sent there, next to them (`network.goto`) |
| `/admin tphere <player>` | `/tphere` | `players.teleport` | Bring a player to you. Network mode: also from another server (`network.send`) |
| `/admin invsee <player>` | `/invsee` | `players.inventory.view` (`.edit` to change) | Live inventory editor |
| `/admin endersee <player>` | `/endersee` | `players.enderchest.view` (`.edit` to change) | Live ender chest editor |
| `/admin clearinv <player> confirm` | `/clearinv` | `players.inventory.edit` | Empty a player's inventory |

`[player]` defaults to yourself. In network mode every command that takes a
player name finds players of the whole network - also ones who only ever
played on another server.

## Reports

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/report <player> <reason>` | always on | `reports.create` (everyone) | Report a player to the staff team |
| `/admin reports` | - | `reports.view` | Reports GUI (console: open reports) |
| `/admin reports open\|all` | - | `reports.view` | List reports in chat |
| `/admin reports view <id>` | - | `reports.view` | Report details (GUI for players) |
| `/admin reports claim\|resolve\|reject <id> [note]` | - | `reports.manage` | Handle a report |

## Chat

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/staffchat [message]` | `/sc`, always on | `chat.staff` | Message the staff chat; without a message: toggle staff chat mode |
| `/admin chat lock\|unlock` | - | `chat.manage` | Only staff (`chat.bypass`) can talk while locked |
| `/admin chat slow <duration\|off>` | - | `chat.manage` | One message per player per interval |
| `/admin chat clear` | - | `chat.manage` | Clear the chat for everyone without `chat.bypass` |
| `/admin chat status` | - | `chat.manage` | Lock and slow-mode state |

Staff can also start a chat message with `#` to send it to staff chat
(`chat.staff-chat.prefix`).

## Server

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin broadcast <message>` | `/broadcast`, `/bc` | `server.broadcast` | Message to everyone (MiniMessage allowed) |
| `/admin server [status]` | - | `server.view` | Version, uptime, players, memory, CPU |
| `/admin server title <title> [\| subtitle]` | - | `server.broadcast` | Title for everyone |
| `/admin server actionbar <text>` | - | `server.broadcast` | Action bar for everyone |
| `/admin server shutdown\|restart [reason]` | - | `server.shutdown` / `server.restart` | With countdown (`server.countdown.*`) |
| `/admin server cancel` | - | `server.shutdown` / `server.restart` | Cancel a pending shutdown/restart |
| `/admin maintenance on [kick] [reason]` | `/maintenance` | `server.maintenance` | Maintenance mode - `kick` also kicks everyone without bypass |
| `/admin maintenance off\|status\|list` | `/maintenance` | `server.maintenance` | `status` also shows the network's |
| `/admin maintenance allow\|deny <player>` | `/maintenance` | `server.maintenance` | Maintenance allow-list |
| `/admin maintenance network on\|off\|status\|list\|allow\|deny ...` | `/maintenance` | `server.maintenance` + `server.maintenance.network` | The whole network's maintenance (network mode) - see [network.md](network.md#maintenance) |

## Worlds

| Command | Permission | What it does |
|---|---|---|
| `/admin worlds` | `worlds.view` | Every loaded world |
| `/admin world [world] info` | `worlds.view` | World details |
| `/admin world [world] tp` | `worlds.teleport` | Teleport to the world's spawn |
| `/admin world [world] time <day\|noon\|night\|midnight\|ticks>` | `worlds.time.set` | |
| `/admin world [world] weather <clear\|rain\|thunder>` | `worlds.weather.set` | |
| `/admin world [world] difficulty <peaceful\|easy\|normal\|hard>` | `worlds.difficulty.set` | |
| `/admin world [world] setspawn` | `worlds.spawn.set` | Spawn at your position |
| `/admin world [world] border <size> [seconds]` · `border center <x> <z>\|here` | `worlds.border.manage` | |
| `/admin world [world] gamerule <rule> <value>` | `worlds.gamerule.manage` | |

Leave out `[world]` to use the world you are in.

## Whitelist

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin whitelist on\|off` | `/wl` | `whitelist.toggle` | |
| `/admin whitelist add <player> [duration] [reason]` | `/wl` | `whitelist.add` (`whitelist.temporary` with a duration) | Works for players who never joined |
| `/admin whitelist remove <player>` | `/wl` | `whitelist.remove` | |
| `/admin whitelist list` | `/wl` | `whitelist.view` | |

## Performance

| Command | Shortcut | Permission | What it does |
|---|---|---|---|
| `/admin perf` | `/lag` | `performance.view` | TPS, MSPT, memory, chunks, entities |
| `/admin entities` | - | `performance.view` | Most common entity types, per-world counts |
| `/admin entities clear <type\|all> [world] confirm` | - | `performance.entity-clear` | Safe entity clear - players and protected types are never removed |

## Audit log

| Command | Permission | What it does |
|---|---|---|
| `/admin audit [player] [page]` | `audit.view` | Newest entries, optionally only one player's |

## Network

Only in network mode - see [network.md](network.md).

| Command | Permission | What it does |
|---|---|---|
| `/admin network [server]` (`/admin net`) | `network.view` | All servers with players and TPS, or the players on one |
| `/admin find <player>` | `network.view` | Which server a player is on |
| `/admin send <player> <server>` | `network.send` | Move a player to another server |
| `/admin connect <server>` | `network.goto` | Go to another server |
| `/admin alert <message>` | `network.alert` | Broadcast on every server (MiniMessage) |

## Extensions

Extensions add their own commands under `/admin` - they appear in
`/admin help` automatically. The official Discord extension adds
`/admin discord <status|test|reload>` (`universaladmin.discord.manage`).
