# Whitelist

The Whitelist module wraps Paper's native whitelist (enable/disable/list/
add/remove) with UniversalAdmin's own metadata - who added a player, when,
why, any notes, and an optional expiration - built entirely on the existing
Module/GUI/Action/Audit/Permission infrastructure. Every mutation is an
`Action` run through `ActionExecutor`, so it is permission-checked and
audited the same way regardless of frontend.

Open via `/admin` → **Whitelist** (needs `universaladmin.whitelist.view`).

**The native whitelist (`whitelist.json`) is always the source of truth for
who is actually whitelisted.** UniversalAdmin's own `whitelist_entries`
table only ever adds metadata on top of that - see "Ownership & the expiry
sweep" below for why this matters.

## GUI

- **Status** - whether the whitelist is currently on, with an Enable/Disable
  toggle (`universaladmin.whitelist.toggle`).
- **Members** - every native whitelist member, each marked either "Managed
  by UniversalAdmin" (has a row - click through to see who added them, when,
  why, and any expiration) or "Externally managed" (no row - added outside
  UniversalAdmin; still a real whitelist member, just not one this module
  annotated).
- **Search Offline Player** - part of the Add wizard: type a name substring,
  pick from the matches (`Bukkit.getOfflinePlayers()`, capped at
  `whitelist.gui.search-max-results`, default 50 - a plain scan is
  proportionate here since this is an occasional, admin-initiated search
  over a small result set, unlike the Players module's full browser).
- **Add** - search → select → optional reason → optional notes → optional
  expiration (needs `universaladmin.whitelist.temporary` to set one) →
  confirm.
- **Remove** - from a member's detail page; removes the native entry and any
  UniversalAdmin metadata for them, regardless of who originally added them
  (an explicit, permission-gated removal is an intentional admin action, not
  something the "don't touch entries we don't own" rule below applies to).

## Temporary whitelist entries

An Add can carry an optional expiration (needs
`universaladmin.whitelist.temporary` in addition to `.add` - an admin can
hold one without the other). Expiry is enforced two ways:

1. **On join** (`WhitelistJoinListener`, `AsyncPlayerPreLoginEvent`) - if a
   player is still natively whitelisted but their UniversalAdmin-tracked
   entry has already expired, the join is denied and the native entry is
   cleaned up immediately. This is the case vanilla's own whitelist check
   can't catch on its own: the player is *still* on the native list at that
   instant.
2. **Periodic sweep** (hourly, same pattern as the Moderation module's
   punishment-expiry sweep) - catches entries whose owner never tries to
   join again, so `whitelist.json` stays accurate over time even without a
   login attempt.

Both paths remove an entry by running the exact same `whitelist.remove`
`Action` through `ActionExecutor` (with `Actor.system("whitelist-expiry")`
in place of an admin) that the GUI's Remove button uses - so an automatic
expiry produces the same kind of audit entry a manual removal would,
distinguishable by actor.

## Ownership & the expiry sweep

**UniversalAdmin only ever tracks metadata for whitelist entries it created
itself.** A player who is natively whitelisted by some other means - vanilla
`/whitelist add`, a hand-edited `whitelist.json`, another plugin - simply
has no row in `whitelist_entries`. No row means UniversalAdmin has no
opinion about that entry at all.

This matters because the automatic paths (the join-time check and the
periodic sweep) **never iterate "everyone on the native whitelist"** - they
only ever iterate UniversalAdmin's own table (`WhitelistEntryRepository#findAll()`).
A native-only entry is therefore never visible to, let alone touched by,
anything automatic - it's not filtered out by a check, it's structurally
never seen in the first place. This is what "Nicht fremde manuell gesetzte
Whitelist-Einträge unbeabsichtigt löschen" means in practice: automatic
code paths have zero code that could reach a foreign entry.

`WhitelistSource` exists to make this an explicit assertion rather than an
implicit "well there's a row so I guess we own it" - every row's `source`
is `UNIVERSAL_ADMIN`, and both the sweep and the join check verify this
before treating an entry as theirs to expire, even though it's the only
value that exists today. If Add is used on a player who happened to already
be natively whitelisted by someone else, that's a legitimate, intentional
ownership transfer (an authorized admin explicitly added them through
UniversalAdmin) - not the "unintentional" case the rule is about.

An explicit **Remove** click is different from the automatic paths: it's an
authorized, intentional admin action and may remove any native entry
regardless of who (or what) added it - it just never invents a fake
ownership history for what it deletes.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.whitelist.view` | op | View the whitelist status and members |
| `universaladmin.whitelist.toggle` | op | Enable/disable the whitelist |
| `universaladmin.whitelist.add` | op | Add a player to the whitelist |
| `universaladmin.whitelist.remove` | op | Remove a player from the whitelist |
| `universaladmin.whitelist.temporary` | op | Give a whitelist entry an expiration |

See [docs/user/permissions.md](../permissions.md) for how these fit into
the platform-wide list, and [docs/architecture/actions.md](../architecture/actions.md)
for how a node on `ActionDefinition.Builder#permission(...)` gets enforced.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `whitelist.gui.search-max-results` | 50 | Maximum offline players returned by one "Search Offline Player" query |

Registered under the module's own `whitelist` settings namespace (see
[docs/development/settings.md](../development/settings.md)).

## Database

`whitelist_entries` (`WhitelistMigration`, version 1007) - one row per
UniversalAdmin-managed whitelist entry: `player_id` (primary key),
`player_name`, `source`, `added_by_id`/`added_by_name`, `added_at`,
`reason` (nullable), `notes` (nullable), `expires_at` (nullable - `NULL`
means permanent). Never the source of truth for whitelist *membership* -
see "Ownership & the expiry sweep" above.
