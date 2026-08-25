# Worlds

The Worlds module is a world browser, profile viewer, and world-control
toolkit built entirely on the existing Module/GUI/Action/Audit/Permission
infrastructure - no shortcuts specific to a built-in module (see docs/development/architecture-rules.md's
"Built-in Modules bleiben Extension-freundlich"). Every mutation is an
`Action` run through `ActionExecutor`, so it is permission-checked and
audited the same way regardless of frontend.

Open via `/admin` → **Worlds** (needs `universaladmin.worlds.view`).

Everything this module reads or changes - spawn, border, gamerules,
difficulty, time, weather - is already Minecraft/Bukkit-persisted
(`level.dat`, written by vanilla's own save routine). There is **no**
UniversalAdmin database table for any of it; every action is a thin,
permission-checked, audited wrapper around a `World`/`WorldBorder` call.

## World Browser

One tile per currently **loaded** world (`Bukkit.getWorlds()` - never a
file-system scan, see docs/development/architecture-rules.md's "Keine Welt-Datei-Manipulation im Core"),
showing Name, Environment, Players, Loaded Chunks, Entities, Difficulty,
Time, and Weather. Click a tile to open its profile.

## World Profile

Name, Environment, Spawn, World Border (size), Players, Chunks, Entities,
Time, Weather, Difficulty, plus navigation into the full World Border page
and the Gamerules list. **Seed is shown only if the viewer holds
`universaladmin.worlds.view.seed`** - a separate permission from
`.view`, and the seed is never even attached to the read snapshot when the
viewer lacks it (the same "don't even attach it" pattern
`GetPlayerIpAddressAction` uses for a player's IP address), not just hidden
in the renderer.

## World Actions

| Action | Node |
|---|---|
| Teleport admin to world spawn | `worlds.teleport` |
| Set world spawn (to the admin's current location) | `worlds.spawn.set` |
| Set time (raw ticks, 0-24000) | `worlds.time.set` |
| Set weather (Clear/Rain/Thunder) | `worlds.weather.set` |
| Set difficulty (Peaceful/Easy/Normal/Hard) | `worlds.difficulty.set` |
| World Border management (center/size/damage/warning) | `worlds.border.manage` |
| Gamerule management (any rule) | `worlds.gamerule.manage` |

None of these are "dangerous" in the Shutdown/Restart-confirmation sense -
nothing here is destructive or irreversible, so there's no confirmation
dialog, the same as every non-destructive Players action.

## Gamerules

Read **dynamically** from the Bukkit API - `World#getGameRules()` enumerates
every rule name the server currently knows, nothing is hardcoded. A future
Minecraft version that adds a new gamerule shows up here with **no code
change**.

Each rule's declared type (`GameRule#getType()`) decides the edit widget:

- **Boolean** rules toggle in place on click.
- **Integer** (and any other type a future Minecraft version introduces -
  "weitere Typen entsprechend API") open a text prompt for the new value.

Setting a rule goes through `World#setGameRuleValue(String, String)`, the
untyped string-based setter - the one fully generic mechanism that needs no
per-rule branch. (This method, and its `getGameRuleValue`/`GameRule.getByName`
counterparts, are flagged deprecated-for-removal on newer Paper API builds;
their typed replacement chain is *equally* flagged deprecated on the same
build with no documented alternative yet, so this module deliberately keeps
using the well-documented, still-functional string API rather than guess at
undocumented pre-release replacement semantics - see the `@SuppressWarnings`
notes on `SetGameRuleAction`/`WorldGameRulesListPage`.)

## World Border

Everything `org.bukkit.WorldBorder` exposes ("soweit API"): center, size
(with an optional gradual transition, in seconds), damage amount, damage
buffer, warning distance, and warning time.

## Dangerous Features (out of scope)

**Not implemented, deliberately:** delete world, clone world, reset world.
These are genuinely destructive, file-level operations this module does not
perform - see docs/development/architecture-rules.md's "Keine Welt-Datei-Manipulation im Core". They
belong in a future "Advanced World Manager" extension, not the core.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.worlds.view` | op | Open the world browser and profile pages |
| `universaladmin.worlds.view.seed` | op | View a world's seed |
| `universaladmin.worlds.teleport` | op | Teleport to a world's spawn |
| `universaladmin.worlds.spawn.set` | op | Set a world's spawn point |
| `universaladmin.worlds.time.set` | op | Set a world's time |
| `universaladmin.worlds.weather.set` | op | Set a world's weather |
| `universaladmin.worlds.difficulty.set` | op | Set a world's difficulty |
| `universaladmin.worlds.border.manage` | op | Manage a world's border |
| `universaladmin.worlds.gamerule.manage` | op | Change a world's gamerules |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list, and [docs/architecture/actions.md](../architecture/actions.md)
for how a node on `ActionDefinition.Builder#permission(...)` gets enforced.

## Database

None. Every field this module reads or writes already lives in Bukkit/
Minecraft's own persisted world state - see the module overview above.
