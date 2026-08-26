# Staff Tools

Vanish, Freeze, Godmode, No-Collision, and Staff Mode - five staff-facing
capabilities owned by the **Moderation** module (see
[moderation.md](moderation.md)), not a separate module: the permission
nodes are explicitly under `universaladmin.moderation.*`/
`universaladmin.bypass.*`, and Staff Mode's tool kit reuses Moderation's
punishment history/GUI directly. Built entirely on stable Paper/Bukkit
public API - **no NMS, no packet hacks, no ProtocolLib dependency**
anywhere in this module.

Freeze is a punishment (targets another player, goes through the
**Moderate** wizard like Ban/Mute/Warn - see [moderation.md](moderation.md#gui-flow)).
Vanish/Godmode/No-Collision/Staff Mode are self-directed (a staff member
toggles their own state) and live as buttons on the Moderation home page
(`/admin` → **Moderation**) instead.

## Vanish

`universaladmin.moderation.vanish` toggles your own vanish status;
`universaladmin.bypass.vanish` lets a viewer see through anyone else's.

What it does, per toggle:
- **Hide/show** - `Player#hidePlayer(plugin, viewer)`/`showPlayer(...)` for
  every online viewer without the bypass permission, plus
  `Entity#setVisibleByDefault(false)` so a player who joins *after* you
  vanish doesn't need a retroactive per-viewer call.
- **Tab list** - `unlistPlayer`/`listPlayer` per viewer, same bypass split.
- **Staff visibility** - handled by `VanishVisibilityPolicy`
  (`canSee(viewer, vanishedPlayerId, level)`); the default implementation
  checks `universaladmin.bypass.vanish`. `VanishLevel` is a one-value enum
  today (`STANDARD`) deliberately shaped so a future tier (e.g. hidden even
  from junior staff) is an additive change, not a rewrite - see
  [moderation.md#policy-hook-staff-hierarchy](moderation.md#policy-hook-staff-hierarchy)
  for the identical reasoning behind `ModerationPolicy`. No rank system
  exists yet; this only prepares the seam.
- **Collision** - disabled automatically while vanished (composed with the
  standalone No-Collision toggle and Staff Mode's auto-no-collision - see
  "No-Collision" below for how those combine).
- **Mob targeting** - `EntityTargetLivingEntityEvent` is cancelled when the
  target is a vanished player.
- **Item pickup** - optional (`moderation.vanish.block-item-pickup`,
  default on): `EntityPickupItemEvent` cancelled for a vanished player.
- **Join/quit messages** - optional (`moderation.vanish.hide-join-message`/
  `hide-quit-message`, both default on): nulled via `PlayerJoinEvent#joinMessage(null)`/
  `PlayerQuitEvent#quitMessage(null)` for the real connect/disconnect of a
  persistently-vanished player.
- **Reconnect restore** - optional (`moderation.vanish.restore-on-reconnect`,
  default on): if you disconnect while vanished, reconnecting re-applies it
  automatically.
- **GUI status** - the Moderation home page's Vanish button shows the
  current ON/OFF state.

### Fake join/leave

Two more settings, both **default off** (opt-in): `moderation.vanish.fake-leave-on-vanish`
broadcasts a normal-looking "X left the game" message (to everyone without
the bypass permission) when you vanish; `moderation.vanish.fake-join-on-unvanish`
does the same with "X joined the game" on unvanish. These are cosmetic chat
messages only - the real audit entry for the vanish toggle is always
recorded via `ActionExecutor` regardless of this setting, so enabling it
never affects what's auditable, only what non-staff players see in chat.

### Vanish limitations

Paper/Bukkit's public API does not give a plugin full control over every
way a vanished player might be detected, and this module does not reach
for NMS to close those gaps. Documented transparently rather than left as
a surprise:

- **Sound** - footsteps, block breaking/placing, and other
  server-simulated sounds a vanished player causes are still audible
  (positionally) to nearby non-staff players. Bukkit has no public API to
  suppress sound emission per-listener for arbitrary player-caused sounds.
- **World changes** - a vanished player's placed/broken blocks, thrown
  items, etc. are ordinary world state; nothing about vanish hides the
  *effects* of what they do, only their entity.
- **`/list` and raw player-list scans** - vanilla `/list` (run from
  console, or by another plugin reading `Bukkit.getOnlinePlayers()`
  directly instead of going through visibility-aware APIs) is not filtered
  by this module. A vanished player still counts as "online" at that level.
- **Other plugins** - any plugin that tracks players via its own listener
  or a raw entity/player list (rather than checking `Player#canSee`) may
  still reveal a vanished player through its own UI (e.g. a separate
  scoreboard/tab-list plugin).

None of this is a bug in this implementation - it's the actual boundary of
what a plugin can influence without packet interception, which docs/development/architecture-rules.md
rules out for this project.

## Freeze

`universaladmin.moderation.freeze`/`.unfreeze`. A punishment (see
[moderation.md](moderation.md#punishment-model)) - reason required, no
duration (unfreeze explicitly to end it), reachable from the Moderate
wizard or the Freeze Tool (see "Staff Mode tools" below). If the target is
online, `FreezeAction` sends them an immediate notice with the reason -
same shape as `MuteAction`'s notice, sent once at freeze time rather than
repeated on every blocked action (movement in particular fires far more
often than a chat message, so a per-attempt reminder there would spam
them instead of informing them). `UnfreezeAction` does not send a
notice, matching `UnmuteAction`'s precedent.

Five independently configurable blocks, all default **on**:

| Setting | Blocks |
|---|---|
| `moderation.freeze.block-movement` | Position changes (`PlayerMoveEvent`, only when position actually changed - looking around is still allowed) |
| `moderation.freeze.block-teleport` | `PlayerTeleportEvent` |
| `moderation.freeze.block-interaction` | `PlayerInteractEvent` |
| `moderation.freeze.block-inventory` | `InventoryClickEvent` in vanilla inventories - clicks inside a UniversalAdmin GUI page are never blocked, so a frozen staff member isn't locked out of the plugin's own menus |
| `moderation.freeze.block-commands` | `PlayerCommandPreprocessEvent` |

Every check reads an in-memory cache (`FreezeRuntimeState`), never the
database - `PlayerMoveEvent` fires on every tick a player moves, so a
per-event DB query would be a real performance problem, not just a style
concern. The cache is populated from the database once at
`AsyncPlayerPreLoginEvent` (async-safe, before enforcement is even
possible) and updated directly by Freeze/Unfreeze.

**Disconnect while frozen** is audited and reported to staff
(`NotificationService.notifyStaff`, gated on `universaladmin.moderation.freeze`)
- routed through `ActionExecutor` with a system actor
(`ModerationActionIds.FREEZE_DISCONNECT_NOTICE`) rather than the listener
calling `AuditService` directly, since a module is never allowed to write
audit entries outside that one hook.

## Godmode

`universaladmin.moderation.godmode` toggles your own invulnerability via
`Entity#setInvulnerable(boolean)` directly - no `EntityDamageEvent`
listener anywhere. This is deliberate: cancelling damage events per-hit
carries side effects (knockback still applying, fire-tick visuals lingering,
per-cause edge cases) that `setInvulnerable` avoids entirely by blocking
damage at the source. Status shown on the Moderation home page's Godmode
button; not persisted (nothing requires it to survive a restart).

## No-Collision

`universaladmin.moderation.collision` toggles your own collision via
`LivingEntity#setCollidable(boolean)`. Composed from three independent
inputs (manual toggle, Vanish, Staff Mode's optional auto-no-collision) so
turning any one off doesn't clobber the others - see `CollisionState#refresh`.
Not persisted, same reasoning as Godmode.

## Staff Mode

`universaladmin.moderation.staffmode` enters/exits (same button on the
Moderation home page, label changes based on current state).

### Entering

1. Your current inventory (36 main storage slots + helmet/chestplate/
   leggings/boots/offhand), gamemode, XP, level, and flight state are
   snapshotted and **persisted to the database before anything is
   touched** - see "Crash safety" below for why this ordering matters.
2. Only once that write has confirmed: your inventory is cleared and
   replaced with the eight-tool staff kit (see "Tools"), unconditional
   protections turn on (damage via `setInvulnerable`, item pickup, block
   breaking), and the optional auto-toggles apply per settings:

| Setting | Default | Effect on entry |
|---|---|---|
| `moderation.staffmode.auto-fly` | on | Grants flight |
| `moderation.staffmode.auto-vanish` | on | Vanishes you |
| `moderation.staffmode.auto-nocollision` | on | Disables collision |

### Exiting

Restores the snapshot exactly (inventory, gamemode, XP, level, flight),
reverses the auto-toggles, and deletes the snapshot row.

### Crash safety and recovery

The snapshot write happens **before** the inventory is ever mutated - not
a flag, an ordering property: the code path that clears your inventory
literally cannot run until the database save future has already resolved
successfully. If the server crashes between those two steps, nothing was
mutated - you keep your real inventory, and there is no snapshot for the
next login to even find. If it crashes *after* entry succeeded (you're
mid-session with the staff kit), the snapshot survives the crash in the
database, and **recovery runs automatically at your next login** - your
real inventory is restored before you can do anything with the leftover
staff kit, and you're notified in chat.

If a snapshot already exists for you (entering while one is somehow still
pending - a bug, a race, whatever), entering again is **refused**
(`CONFLICT`), never silently overwritten - the existing snapshot could be
your real, unrecovered inventory.

`universaladmin.moderation.staffmode.recover` lets an admin trigger
recovery manually: `/admin staff recover` (self) or `/admin staff recover <player>`
(another *online* player - recovery needs a live inventory to hand items
back to; an offline target fails with a clear error). This is the explicit
recovery path the automatic login-time recovery is meant to make mostly
unnecessary, not a replacement for it.

### Tools

Nine items fill your hotbar on entry, each tagged via
`PersistentDataContainer`/`NamespacedKey` (not matched by display
name/lore, so a renamed item is still recognized and nothing else can be
mistaken for one). Every tool click **cancels the triggering interaction**
before dispatching - a Player-Inspector-holding staff member right-clicking
a cow doesn't feed it, right-clicking a block doesn't place/break it.
`PlayerInteractEntityEvent` fires once per hand for a single physical
click; every entity-targeted tool below only reacts to the main-hand
firing, so a click never dispatches twice.

| Tool | Trigger | Effect |
|---|---|---|
| Player Inspector | Right-click a player | Opens that player's punishment history. Its icon/name/lore also live-update to whoever is currently in your crosshair - see "Live target display" below - regardless of which tool you're actually holding. |
| Freeze Tool | Right-click a player | Toggles Freeze/Unfreeze immediately (fixed reason "Staff Freeze Tool" - no prompt, so using it never interrupts world interaction) |
| Inventory Inspector | Right-click a player | Live mirror of their main inventory + armor/offhand. Editing requires `universaladmin.players.inventory.edit` (the same permission the Players module's own inventory editor uses); every click/drag mirrors onto the real target immediately, not just on close. Without that permission, view-only. |
| Ender Chest Inspector | Right-click a player | Same as the Inventory Inspector, for their ender chest - requires `universaladmin.players.enderchest.edit` to write. |
| Teleport to/Bring Player | Right-click air | Opens a picker over every online player, then a "Teleport to them" / "Bring them to me" choice - runs through the Players module's own audited teleport action (`TeleportKind.ADMIN_TO_PLAYER`/`BRING_TO_ADMIN`), unlike "Teleport to Player" below. |
| Vanish Toggle | Right-click air | Runs the Vanish action on yourself |
| Moderate Tool | Right-click a player | Opens the full Moderate wizard (Kick/Ban/Mute/Warn) for that player directly - no need to leave Staff Mode and find them again in the Player Browser |
| Teleport to Player | Right-click a player | Teleports you directly to that player (unaudited - a movement convenience, not a moderation action) |
| Exit Staff Mode | Right-click air | Runs Exit Staff Mode |

While Staff Mode is active (regardless of which tool is held, or none):
item pickup and block breaking are unconditionally cancelled, and you are
invulnerable.

### Live target display

`StaffModeTargetTracker` runs every 10 ticks (twice a second) for every
online Staff-Mode-active player. Unlike a vanilla ray trace, it deliberately
does **not** stop at walls: it searches every online player within
`moderation.staffmode.target-range-blocks` (default 40 blocks, ignores line
of sight entirely) for whoever is closest to your exact look direction,
within a narrow angular tolerance - so it keeps working through walls and at
much longer range than the ~6-block reach the tools themselves interact at
(a Minecraft client limit this plugin can't change either way). If it finds
someone:

- The Player Inspector tool always shows their real skin, name, and a quick
  Frozen/Vanished status line, whichever tool you're actually holding.
- Whichever other status-aware tool you're currently holding (Freeze Tool,
  Inventory/Ender Chest Inspector, Moderate Tool, Teleport to Player) shows
  the same status line too, reset back to its default icon the moment you
  look away or switch tools.
- An actionbar message ("Looking at: `<name>`", or "Looking at: nobody")
  is resent every tick a target exists, keeping it visible continuously
  instead of fading out after Minecraft's default few seconds.

This is read-only presentation; it never changes who gets affected by an
actual tool click, which always re-resolves the right-clicked entity itself.

### Tool kit is locked

The hotbar (and the rest of your inventory, which Staff Mode leaves
otherwise empty) cannot be rearranged, dropped, or shift-clicked into
another inventory while Staff Mode is active -
`StaffModeGuardListener` cancels `InventoryClickEvent`/`InventoryDragEvent`/
`PlayerDropItemEvent` outright for a Staff-Mode-active player, except
inside one of UniversalAdmin's own GUI pages (Player Inspector, Inventory
Inspector, Ender Chest Inspector, Moderate), which `GuiListener` already
owns completely on its own. `StaffModeTargetTracker`'s periodic pass also calls
`StaffToolItems#restoreIfTampered`, which re-gives the whole kit if any
tool slot is ever found empty or wrong - a backstop for an edge case the
click/drag/drop cancellation didn't anticipate, not the primary defense.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.moderation.vanish` | op | Toggle your own vanish status |
| `universaladmin.bypass.vanish` | op | See vanished players |
| `universaladmin.moderation.godmode` | op | Toggle your own godmode status |
| `universaladmin.moderation.collision` | op | Toggle your own no-collision status |
| `universaladmin.moderation.staffmode` | op | Enter/exit staff mode |
| `universaladmin.moderation.staffmode.recover` | op | Manually recover a pending staff-mode snapshot |
| `universaladmin.moderation.freeze` | op | Freeze a player |
| `universaladmin.moderation.unfreeze` | op | Unfreeze a player |

See [docs/user/permissions.md](../permissions.md) for the platform-wide list.

## Database

Two new tables, alongside `punishments` (Freeze reuses that table - see
[moderation.md](moderation.md#database)):

- `vanish_state` (`VanishStateMigration` version 1004) -
  `player_id` primary key, `vanished_at`. Row existence means "was
  vanished, restore on reconnect if enabled" - purely for persistence; the
  in-memory `VanishRuntimeState` is what every hot-path listener actually
  reads.
- `staff_mode_snapshots` (`StaffModeSnapshotMigration` version 1005) -
  `player_id` primary key, `inventory_data` (`ItemStack.serializeItemsAsBytes(...)`
  over the combined 36+5-slot array, `MEDIUMBLOB` on MySQL/MariaDB since
  the default `BLOB` cap is too tight for a full inventory of heavy-NBT
  items, plain `BLOB` on SQLite), `gamemode`, `exp`, `level`,
  `allow_flight`, `flying`, `created_at`. One row per player; existence is
  "has a pending snapshot".

## Tests

`VanishRuntimeStateTest`/`FreezeRuntimeStateTest`/`StaffStateTrackersTest`
(toggle idempotency for every in-memory tracker), `StaffModeServiceTest`
(the enter/exit decision branches that short-circuit before ever touching
a live inventory - "snapshot already exists" and "nothing to restore" -
against a fake `StaffModeSnapshotRepository`; the actual Bukkit inventory
mutation needs a running Paper server, same documented exclusion
`PlayerServiceTest` uses for its own `snapshot()`), and
`JdbcStaffModeSnapshotRepositoryTest` (real temp SQLite, a synthetic
`byte[]` payload rather than real `ItemStack.serializeAsBytes()` output -
proves the BLOB column/dialect/migration round-trip independent of live
Bukkit item serialization).
