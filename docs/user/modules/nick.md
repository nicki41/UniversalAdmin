# Nick

Staff play under another name and skin - to watch a suspected cheater, or
just to play without being recognised. The nick is everywhere a player
looks: the chat, the tab list, the name above the head, the skin, tab
completion, join and quit messages, death messages. UniversalAdmin keeps the
real identity in its records, and staff who may see it see it. Why it works
the way it does: [ADR 0025](../../architecture/decisions/0025-nick-system.md).

## Commands

| Command | What it does |
|---|---|
| `/nick <name>` | Play as `<name>` - with the skin of the account called that, if there is one |
| `/nick <name> <account>` | Name and skin chosen separately: the skin of `<account>` |
| `/nick <name> own` | Keep your own skin |
| `/nick <name> random` | A skin from `nick.random-skins` |
| `/nick random` | A made-up name (or one from `nick.random-names`) and a random skin |
| `/unnick` or `/nick off` | Be yourself again |
| `/unnick <player>` or `/nick off <player>` | End another player's nick (`universaladmin.nick.others`) |
| `/nick list` | Every nick shown right now, with the real name, skin and server (`universaladmin.nick.see`) |

The commands are also `/admin nick ...` and `/admin unnick ...`; `/nick`
and `/unnick` are shortcuts while `commands.shortcuts` is on. The same is
in `/admin` → **Nick**: a random nick in one click, a form for name and
skin, and - for staff who may see real names - every active nick, a click
ends it.

## Which names are allowed

- A valid Minecraft name: 3-16 letters, digits or `_`.
- Nobody may be shown under it right now - not a player online here or on
  another server of the network, not another nick.
- Not the name of a player who has played here (on any server sharing the
  database), unless `nick.allow-known-names` is on - so a nick never
  impersonates one of your players.
- Not a nick an offline player keeps for when they come back.

When a player joins whose name is somebody's nick, that nick ends: the
name is theirs.

## Who sees the real name

- **Everyone else** sees a nicked player like any other player.
- **Staff with `universaladmin.nick.see`** see the real name in the player
  list (a "Nick of ..." line and search by the real name), on the profile
  (the **Nick** button) and in `/nick list`.
- **The records keep the real identity.** Punishments, notes, reports,
  inventory edits, the player profiles and the audit log name the real
  player. The audit log keeps the nick in the entry's metadata
  (`actor-nick`, `target-nick`).
- **Actions run under the real name.** A nicked staff member who bans,
  kicks or locks the chat does so as themselves - the announcement names
  them. A nick hides who you are while you play, not who punished someone.
- Commands of UniversalAdmin find a nicked player by the nick and by the
  real name.

## Across rejoins and the network

With `nick.restore-on-rejoin` (default) a nick stays until it is removed -
leave and come back, and you arrive under the nick. Without it, a nick ends
when its player leaves; switching servers in a network keeps it.

In a network the nick is stored in the shared database: it follows its
player to every server, `/nick list` and the player list show the nicks of
the other servers too, and a nick ended on one server ends everywhere.

## Good to know

- **Other plugins** see the nick: `Player#getName()` returns it, so chat
  formats, scoreboards and placeholders such as `%player_name%` show the
  nick. Their own records (permissions, economy) stay with the player's
  UUID and real name.
- **Vanilla commands** (`/msg`, `/tp` ...) and plugins that look players up
  by their exact name find a nicked player by the real name, not by the
  nick - Paper keeps the name the player joined with for that. Plugins
  using `Bukkit#getPlayer(name)` find the nick too.
- The **UUID** stays the player's own; a client mod that shows UUIDs leads
  to the real account. Death messages lose the UUID hover of nicked players.
- A **tab-list plugin** still sorts and prefixes by the permission group.
  With [TAB](https://modrinth.com/plugin/tab-was-taken),
  [UniversalAdmin TAB](../../extensions/tab.md) shows nicked players in an
  ordinary group.
- **Proxy features** (a proxy chat or tab list) see the real name.
- Changing the nick respawns you in place for a moment, so your own client
  shows the new skin. On join it is set before anyone sees you.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `nick.restore-on-rejoin` | `true` | A nick stays when its player leaves and comes back |
| `nick.allow-known-names` | `false` | Allow nicks with the name of a player who has played here |
| `nick.skin-of-name` | `true` | `/nick <name>` wears the skin of the account called that, if there is one |
| `nick.random-names` | `[]` | Names `/nick random` picks from; empty = made-up names |
| `nick.random-skins` | `[MHF_Steve, MHF_Alex]` | Accounts whose skins a random skin comes from; empty = own skin |
| `modules.nick` | `true` | Switch the whole module off (restart) |

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.nick.use` | op | Nick yourself |
| `universaladmin.nick.others` | op | End other players' nicks |
| `universaladmin.nick.see` | op | See the real name behind a nick |

## Audit log

Taking a nick (`core:nick.set`, the nick as new value, the skin in the
metadata) and ending one (`core:nick.clear`) are audited.
