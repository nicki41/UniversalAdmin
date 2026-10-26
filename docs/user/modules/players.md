# Players

The Players module is a player browser, profile viewer, and admin-action
toolkit built entirely on the existing Module/GUI/Action/Audit/Settings
infrastructure - no shortcuts specific to a built-in module (see docs/development/architecture-rules.md's
"Built-in Modules Stay Extension-Friendly"). Every mutation is an
`Action` run through `ActionExecutor`, so it is permission-checked and
audited the same way regardless of whether it's clicked in the GUI or (in
the future) invoked from a command.

Open via `/admin` → **Players** (needs `universaladmin.players.view`).

## Player Browser

Four entry points from the Players home page:

- **Online Players** - currently connected players, with cycling **World**
  and **Gamemode** filter buttons (only meaningful for online players - an
  offline player's world/gamemode isn't available without loading their
  data, see "Offline limitations" below) and a name sort. In
  [network mode](../network.md) the list holds **every player of the
  network** with the server they are on, plus a **Server** filter and
  sorting by name or by server. Players on another server open the same
  profile: it says where they are and offers **Teleport to player**,
  **Bring to me** and **Send to server...**; what needs them on this server
  (live inventory, ender chest, effects) is left out. Vanished players stay
  hidden from staff without `universaladmin.bypass.vanish`; players on
  servers without UniversalAdmin have no profile and are only in the
  Network menu.
- **Offline Players** - every player who has ever joined, backed by a small
  indexed table (`player_profiles`) rather than Bukkit's own (documented
  slow) `getOfflinePlayers()` scan. Has a name-search box and a sort toggle
  (name / last seen).
- **Recently Seen** - the same offline list, fixed to last-seen-first.
- **Search** - the same page as Offline Players, opened with the search box
  focused.

Every list is capped at `players.gui.max-results` (default 200, see
"Settings" below) profiles per query - a deliberate bound, not a bug: the
underlying query is `ORDER BY ... LIMIT ?` at the database layer, so raising
the cap doesn't change what's *findable* (search still works), just how many
rows one unfiltered browse shows at once.

## Player Profile

Clicking a player opens their profile: Name, UUID, Online Status, First
Join, Last Join, Total Playtime, and (if online) Session Duration, World,
Coordinates, Gamemode, Health, Food, Saturation, XP Progress, Level, Ping,
Locale, Active Effects, and Respawn Location, plus IP Address if the viewer
holds `universaladmin.players.ip`. A field that genuinely isn't available
(everything live-state-only, for an offline target) is **omitted**, not
shown blank - see "Offline limitations".

IP address is never attached to the profile data at all (not just hidden in
the GUI) unless the viewer holds the permission - see
`GetPlayerIpAddressAction`'s javadoc. Viewing a profile or an IP address is
not audited (read-only, `.notAudited()`); every field below the profile
that changes something is.

## Actions

All gated on their own permission node (see "Permissions"), all routed
through `ActionExecutor`, all fail cleanly with a "player is no longer
online" message if the target disconnected between the GUI rendering and
the click landing (a real race, not a hypothetical one).

| Action | Node |
|---|---|
| Teleport Admin → Player, Bring Player → Admin, Player → another Player, Teleport to World Spawn, Teleport to Bed/Respawn, Teleport to Coordinates | `players.teleport` |
| Heal | `players.heal` |
| Feed | `players.feed` |
| Extinguish | `players.extinguish` |
| Clear Effects | `players.effects.clear` |
| Add Effect | `players.effects.add` |
| Remove Effect | `players.effects.remove` |
| Set Health | `players.set.health` |
| Set Food | `players.set.food` |
| Set XP (progress) | `players.set.xp` |
| Set Level | `players.set.level` |
| Toggle Fly | `players.fly.toggle` |
| Set Fly Speed | `players.fly.speed` |
| Set Walk Speed | `players.walk-speed` |
| Toggle Glow | `players.glow` |
| Toggle Gravity | `players.gravity` |
| Toggle Collision | `players.collision` |
| Gamemode (Survival/Creative/Adventure/Spectator) | `players.gamemode` |

Health, food, XP and the two speeds are set with a slider that starts at the
player's current value; the level stays a number field, and **Teleport to
Coordinates** is a form with X, Y, Z and the world as a list.

All six teleport variants share one action/permission (`players.teleport`)
- they're the same risk tier and the same GUI section; the audit summary
still distinguishes which variant ran. Every other action above got its own
node deliberately, so e.g. a moderator role can be granted Heal/Feed without
also getting arbitrary Set Health/Set Food access.

Every action here is online-only. There is no "Kill" action - use Set
Health to a low value if that's the intent.

## Inventory & Ender Chest

Reachable from the profile page **only when the target is online** (hidden,
not shown disabled, per the "hide what can't work right now" rule the whole
GUI framework follows) - editing an offline player's inventory would need
NMS-level player-data-file access, which this project doesn't use (see
docs/development/architecture-rules.md's "Security" section on packet/NMS hacks).

- **Inventory** (`PlayerInventoryPage`) - laid out like the player's own
  screen: main storage in the top three rows, the hotbar below it, then
  helmet, chestplate, leggings, boots and offhand in the bottom row. The book
  in the bottom row explains the layout.
- **Ender Chest** (`PlayerEnderChestPage`) - the 27 ender chest slots; the
  rest of the grid is locked.

Both are **live in both directions**: whatever the player does (picking up,
dropping, eating, moving items) shows up in the editor within one tick, and
with `...inventory.edit`/`...enderchest.edit` every change made in the
editor lands in the player's inventory within one tick - no Save button. A
viewer with only `...view` sees the same live view but cannot move
anything. If the player logs off, the editor closes with a notice. The
Staff Mode Inventory/Ender Chest Inspector tools (see
[staff-tools.md](staff-tools.md#tools)) open exactly these pages.

**How the live sync works.** The editor is a normal UniversalAdmin
`GuiView` (so `GuiListener` still owns every click, including the
read-only mode), and `LiveInventoryService` merges it with the real
inventory once per tick, slot by slot: whichever side changed since the
previous tick is copied to the other. If both changed the same slot in the
same tick, the player's inventory wins - copying the editor's stale copy
back could otherwise bring back an item the player had just used or
dropped. Every slot that is not part of the inventory is locked, so an item
can never end up stranded in the menu.

**Audit.** One entry per editing session, not one per click: when the
editor closes, `players.inventory.edit` / `players.enderchest.edit` runs
through `ActionExecutor` (permission check, audit, action event) and the
staff member gets a summary ("Inventory of Steve edited: 3 slot(s)
changed"). The entry records how many slots changed and a coarse
before/after occupancy ("12/41 slots occupied"), **never** the items
themselves - see `InventoryChangeSummary`; item contents are exactly the
kind of sensitive detail the audit log keeps out. Opening an editor
without changing anything records nothing.

**Clear Inventory** is a dangerous action (clears main storage, armor, and
offhand - matching vanilla `/clear`, never the ender chest) behind a
confirmation dialog (skipped if `gui.confirmations` is off), gated on
`...inventory.edit`.

## Offline limitations

An offline player has no live entity state. What's still available without
touching NMS or a Mojang API call:

- Name, UUID, First Join, Last Seen, Total Playtime (from Bukkit's own
  `OfflinePlayer` stats/usercache - read off the main thread since it's
  file-backed, see `PlayerService#snapshot`)
- Respawn/bed location (`OfflinePlayer#getBedSpawnLocation()`)

Not available offline: World, Coordinates, Gamemode, Health, Food,
Saturation, XP, Level, Ping, Locale, Active Effects, IP address, Inventory,
Ender Chest. The profile page omits these fields entirely for an offline
target rather than showing them blank; every action that needs live state
fails with a "player is no longer online" message instead of silently doing
nothing.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.players.view` | op | Open the player browser and profile pages |
| `universaladmin.players.ip` | op | View a player's IP address |
| `universaladmin.players.teleport` | op | All teleport variants |
| `universaladmin.players.heal` | op | Heal |
| `universaladmin.players.feed` | op | Feed |
| `universaladmin.players.extinguish` | op | Extinguish |
| `universaladmin.players.effects.clear` | op | Clear potion effects |
| `universaladmin.players.effects.add` | op | Add a potion effect |
| `universaladmin.players.effects.remove` | op | Remove one potion effect |
| `universaladmin.players.set.health` | op | Set health |
| `universaladmin.players.set.food` | op | Set food level |
| `universaladmin.players.set.xp` | op | Set XP progress |
| `universaladmin.players.set.level` | op | Set level |
| `universaladmin.players.fly.toggle` | op | Toggle fly |
| `universaladmin.players.fly.speed` | op | Set fly speed |
| `universaladmin.players.walk-speed` | op | Set walk speed |
| `universaladmin.players.glow` | op | Toggle glowing |
| `universaladmin.players.gravity` | op | Toggle gravity |
| `universaladmin.players.collision` | op | Toggle collision |
| `universaladmin.players.gamemode` | op | Change gamemode |
| `universaladmin.players.inventory.view` | op | View main inventory, armor, offhand |
| `universaladmin.players.inventory.edit` | op | Edit/clear main inventory, armor, offhand |
| `universaladmin.players.enderchest.view` | op | View ender chest |
| `universaladmin.players.enderchest.edit` | op | Edit ender chest |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list, and [docs/architecture/actions.md](../architecture/actions.md)
for how a node on `ActionDefinition.Builder#permission(...)` gets enforced.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `players.gui.max-results` | 200 | Upper bound on one Offline Players/Search/Recently Seen query |

Registered under the module's own `players` settings namespace (see
[docs/development/settings.md](../development/settings.md)) - the first
built-in module to actually do so.

## Database

`player_profiles` (id, last_known_name, first_join, last_seen) is an index
for the Offline/Search/Recently-Seen lists, not the source of truth for any
live field - see the "Player Browser" section above. Created by
`PlayerProfileMigration` (version 1000); `PlayerProfileIndexMigration`
(version 1001) adds indexes on `last_known_name` and `last_seen` for the
sort/order-by paths. Name search is a case-insensitive substring match
(`LIKE '%term%'`), which can't use an index - accepted at the expected scale
of "unique players who ever joined a server", not a huge event log.

## Commands

`/admin player`, `info` (`/seen`), `heal`, `feed`, `extinguish`, `fly`,
`gamemode` (`/gm`), `speed`, `tp` (`/tpto`), `tphere`, `invsee`, `endersee`,
`clearinv` - see [commands.md](../commands.md#players).
