# UniversalAdmin Placeholders (PlaceholderAPI)

The official extension that makes UniversalAdmin's state available to every
plugin using [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi):
scoreboards, tab lists, chat formats, holograms.

## Setup

1. Install PlaceholderAPI.
2. Put `universaladmin-placeholderapi-<version>.jar` from the
   [GitHub release](https://github.com/nicki41/UniversalAdmin/releases) into
   `plugins/` (version 1.3 needs UniversalAdmin 1.3 or newer).
3. Restart. `/papi parse me %universaladmin_online%` shows the number of
   visible players. No `/papi ecloud download` needed.

## Placeholders

About the player the placeholder is shown to:

| Placeholder | Value |
|---|---|
| `%universaladmin_vanished%` | yes/no (PlaceholderAPI's true/false texts) |
| `%universaladmin_staffmode%` | yes/no |
| `%universaladmin_frozen%` | yes/no |
| `%universaladmin_godmode%` | yes/no |
| `%universaladmin_muted%` | yes/no |
| `%universaladmin_mute_reason%` | the reason, empty if not muted |
| `%universaladmin_mute_remaining%` | `2h 15m`, `permanent` (translated), empty if not muted |
| `%universaladmin_warnings%` | warnings in force |
| `%universaladmin_punishments%` | every punishment ever (kicks and warnings included) |

About the server:

| Placeholder | Value |
|---|---|
| `%universaladmin_online%` | online players **without the vanished ones** - use it instead of the plain online count so vanish is not given away |
| `%universaladmin_staff_online%` | visible staff online (permission `universaladmin.menu.open`) |
| `%universaladmin_vanished_count%` | vanished players online |
| `%universaladmin_open_reports%` | open reports |
| `%universaladmin_maintenance%` | yes/no |

Network mode ([network.md](../user/network.md)):

| Placeholder | Value |
|---|---|
| `%universaladmin_server%` | this server's name, empty without network mode |
| `%universaladmin_network_online%` | players on the whole network, vanished ones left out (like `online` without network mode) |
| `%universaladmin_network_servers%` | servers online |
| `%universaladmin_network_online_<server>%` | players on that server without vanished ones, e.g. `%universaladmin_network_online_survival%` |

## Notes

- All values come from memory - safe for scoreboards that update every
  tick. Mutes, warnings and punishment counts are loaded when a player
  joins and refreshed after every moderation action; changes made on
  another server of a network show up within a minute.
- A placeholder whose module is switched off (e.g. Reports) shows an empty
  text.
- Developers: the same values are available without PlaceholderAPI through
  the `dev.universaladmin.status` services of the extension API -
  `PlayerStatus`, `ReportOverview`, `MaintenanceStatus`.
