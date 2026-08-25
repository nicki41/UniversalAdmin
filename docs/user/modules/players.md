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
  data, see "Offline limitations" below) and a name sort.
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

- **Inventory** (`PlayerInventoryPage`) - the 36 main+hotbar slots in the
  content area, plus armor (helmet/chestplate/leggings/boots) and offhand in
  row 5, so the whole live equipped state is one page, not a separate one a
  viewer has to already know exists.
- **Ender Chest** (`PlayerEnderChestPage`) - the 27 ender chest slots; the
  rest of the grid is locked filler.

A viewer with only `...inventory.view`/`...enderchest.view` gets a read-only
mirror - nothing can be moved in, out, or around. A viewer with `...edit`
gets a live, draggable mirror - no Save button; whatever is in the mirror
when the viewer actually closes it (walks away, presses Esc, or clicks
Back/Close) is what gets written back.

**Design note - live edits, still through `ActionExecutor`.** The simplest
implementation would open the target's real `PlayerInventory` object
directly (`viewer.openInventory(target.getInventory())`) - what most
Essentials-style plugins do. That makes edits instant with almost no code,
but it bypasses `ActionExecutor` entirely: no audit trail, no
permission-gated read-only mode, no clean failure if the target disconnects
mid-edit, and (since a raw `PlayerInventory` isn't a `GuiView`) none of
`GuiListener`'s own click protections either. That directly contradicts
"every change is audited" and docs/development/architecture-rules.md's "no module builds its own
audit logging" rule. Instead, these pages still render a *mirror* of the
inventory in the GUI's own `GuiView` - `GuiListener` still owns every click/
drag against it, same as any other UniversalAdmin page - and edits happen
freely inside that mirror (drag items in/out/around) while it's open. The
"no Save button" part is `GuiView#onClose`: a callback the page registers
that runs once the view genuinely closes (not when navigating to another
UniversalAdmin page), by which point every drag/click the viewer made has
already settled - the callback reads the mirror's final contents and calls
`SetPlayerInventoryContentsAction`/`SetPlayerEnderChestContentsAction`,
which re-resolves the target on the main thread and only then writes to
their real inventory - audited, permission-checked, and failing cleanly
(`CONFLICT`) if they logged off while the mirror was open.

The audit entry for an inventory/ender-chest change records a coarse
before/after slot-occupancy count ("12/36 slots occupied" style), **never**
the actual items - see `InventoryChangeSummary`. Positions and item contents
are exactly the kind of "nur soweit nötig" sensitive data docs/development/architecture-rules.md asks to
keep out of the audit log.

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
