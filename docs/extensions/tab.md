# UniversalAdmin TAB

Source and releases: [universaladmin-mc/universaladmin-tab](https://github.com/universaladmin-mc/universaladmin-tab).

The official extension for [TAB](https://modrinth.com/plugin/tab-was-taken)
(by NEZNAMY): the tab list and name tags follow UniversalAdmin's nicks,
vanish and staff mode.

## Setup

1. Install TAB 6 or newer on the server.
2. Put `universaladmin-tab-<version>.jar` from the
   [GitHub releases](https://github.com/universaladmin-mc/universaladmin-tab/releases) into
   `plugins/` (it needs UniversalAdmin 1.7 or newer).
3. Restart. `/admin modules` lists **TAB**.

In a network with TAB on the backend servers, install it on each of them.

## Nicks

A [nicked](../user/modules/nick.md) player would still carry their rank:
TAB takes prefix, suffix and sorting from the permission group, and an
admin called "Notch" at the top of the list in red gives the nick away. The
extension gives nicked players the TAB group `tab.nick-group` (`default`)
instead, and lists everyone under the name their profile has now - TAB's
`%player%` only knows the name a player joined with. Name tags follow too:
TAB moves its name-tag team to the nick.

## Vanish and staff mode

- **Vanish:** UniversalAdmin sets the `vanished` player metadata that TAB
  (and other plugins) look for, so TAB treats vanished players as vanished
  in its own features (player counts, layouts, name-tag teams; staff see
  them there with `tab.seevanished`). The extension adds
  `tab.vanish-marker` (` &7[V]`) after the name of vanished players - only
  staff who see them notice it.
- **Staff mode:** `tab.staff-mode-marker` (` &c[Staff]`) after the name of
  players in staff mode.

The markers go after the tab-list suffix and - with
`tab.markers-on-name-tags` - after the name-tag suffix. TAB's colour codes
apply (`&7`, `#RRGGBB`).

## Settings

In-game under `/admin` → Settings → tab, or in `config.yml`:

| Key | Default | Meaning |
|---|---|---|
| `tab.nick-group` | `default` | TAB group of nicked players (prefix, suffix, sorting); empty = their own |
| `tab.vanish-marker` | ` &7[V]` | After the name of vanished players; empty = none |
| `tab.staff-mode-marker` | ` &c[Staff]` | After the name of players in staff mode; empty = none |
| `tab.markers-on-name-tags` | `true` | The markers also go after the name tag |

## How it works

Twice a second the extension compares what UniversalAdmin says (nicks,
vanish, staff mode) with what it last told TAB, and sends only the
differences through TAB's API. It only ever resets values it set itself, so
TAB's configuration and other plugins' API values stay untouched; after
`/tab reload` it sets everything again. It has no commands and nothing in
the audit log.
