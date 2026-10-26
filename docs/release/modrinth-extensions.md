# Modrinth Pages of the Official Extensions

Source of truth for the Modrinth projects of the official extensions.
Edit here first, then copy into Modrinth. Versions are uploaded by
`release.yml` like the plugin's (see [releasing.md](releasing.md#modrinth));
texts, icons and gallery are maintained by hand. All projects are in the
Modrinth collection "UniversalAdmin".

## UniversalAdmin Discord

| Field | Value |
|---|---|
| Name | UniversalAdmin Discord |
| Slug | `universaladmin-discord` |
| Categories | Management, Social, Utility |
| Environment | Server-side only |
| Loaders / game versions | Paper, Purpur · same as UniversalAdmin (set by `release.yml`) |
| License | GPL-3.0-or-later (from 1.5.0; up to 1.4.0 Apache-2.0) |
| Icon | `assets/icon-discord.png` |
| Links | Source and issues: the UniversalAdmin repository; wiki: `docs/extensions/discord.md` |

### Summary

> UniversalAdmin in Discord: admin actions as embeds, two-way staff chat, /ban, /mute and /history from Discord, and reports with buttons.

### Description

---

# UniversalAdmin Discord

The official Discord extension for
[UniversalAdmin](https://modrinth.com/plugin/universaladmin) - your staff
team's work in Discord.

## Two modes

**Webhook** - paste a webhook URL, done: bans, mutes, warnings, reports,
restarts, maintenance and whitelist changes appear in a channel as tidy
embeds.

**Bot** - everything above, plus:

- 💬 **Staff chat both ways** - `/sc` in game shows up in Discord, and what
  your team writes in the staff channel shows up in game.
- 🔨 **Moderate from Discord** - `/ban`, `/mute`, `/kick`, `/warn`,
  `/punish` with UniversalAdmin's escalating punishment templates, `/unban`,
  `/unmute`, `/history`, `/check`, `/find`, and `/ua` for any other admin
  command - with player name completion.
- 📣 **Reports with buttons** - every new report appears with **Claim**,
  **Resolve** and **Reject**; the card follows the report's status.

## Safe by design

- Discord roles map to UniversalAdmin permissions: a moderator role that
  may mute but not ban can `/mute` but not `/ban`.
- Everything from Discord runs through the same checks and the same audit
  log as in game, as `Discord:<name>`.
- No pings, ever: mentions are switched off, and text from the game cannot
  format Discord messages.
- Discord traffic never runs on the server's main thread.

## Requirements

- [UniversalAdmin](https://modrinth.com/plugin/universaladmin) of the same
  version.
- Bot mode: a Discord application with a bot token
  ([setup guide](https://github.com/nicki41/UniversalAdmin/blob/main/docs/extensions/discord.md#bot-mode)).
  On the first start the server downloads the Discord library (about
  20 MB) once.

[Setup guide](https://github.com/nicki41/UniversalAdmin/blob/main/docs/extensions/discord.md) ·
[Source](https://github.com/nicki41/UniversalAdmin) ·
[Issues](https://github.com/nicki41/UniversalAdmin/issues)

---

## UniversalAdmin Placeholders

| Field | Value |
|---|---|
| Name | UniversalAdmin Placeholders |
| Slug | `universaladmin-placeholders` |
| Categories | Utility, Management |
| Environment | Server-side only |
| Loaders / game versions | Paper, Purpur · same as UniversalAdmin (set by `release.yml`) |
| License | GPL-3.0-or-later (from 1.5.0; up to 1.4.0 Apache-2.0) |
| Icon | `assets/icon-placeholders.png` |
| Links | Source and issues: the UniversalAdmin repository; wiki: `docs/extensions/placeholderapi.md` |

### Summary

> PlaceholderAPI placeholders for UniversalAdmin: online count without vanished players, mutes, warnings, open reports, maintenance and network counts.

### Description

---

# UniversalAdmin Placeholders

The official [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)
extension for [UniversalAdmin](https://modrinth.com/plugin/universaladmin) -
put your admin data on scoreboards, tab lists, chat formats and holograms.

## Highlights

- `%universaladmin_online%` - the online count **without vanished
  players**, so your tab list never gives a vanished moderator away.
- `%universaladmin_staff_online%` - visible staff online.
- `%universaladmin_muted%`, `_mute_reason`, `_mute_remaining` - show a
  muted player how long is left.
- `%universaladmin_warnings%`, `_punishments` - per player.
- `%universaladmin_open_reports%`, `_maintenance` - for staff scoreboards.
- `%universaladmin_vanished%`, `_staffmode`, `_frozen`, `_godmode`.
- Networks: `%universaladmin_server%`, `_network_online`,
  `_network_servers`, `_network_online_<server>` - vanished players are
  not counted there either.

All values come from memory - safe for scoreboards that update every tick.
No `/papi ecloud` download needed.

## Requirements

- [UniversalAdmin](https://modrinth.com/plugin/universaladmin) 1.3 or newer
  (Placeholders 1.2 works with UniversalAdmin 1.2).
- [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi).

[Every placeholder](https://github.com/nicki41/UniversalAdmin/blob/main/docs/extensions/placeholderapi.md) ·
[Source](https://github.com/nicki41/UniversalAdmin) ·
[Issues](https://github.com/nicki41/UniversalAdmin/issues)

---

## UniversalAdmin Voice Chat

The project id goes into the repository variable `MODRINTH_VOICECHAT_ID`;
`release.yml` and `compatibility.yml` publish to it from then on.

| Field | Value |
|---|---|
| Name | UniversalAdmin Voice Chat |
| Slug | `universaladmin-voicechat` |
| Categories | Management, Social, Utility |
| Environment | Server-side only |
| Loaders / game versions | Paper, Purpur · same as UniversalAdmin (set by `release.yml`) |
| License | GPL-3.0-or-later |
| Icon | `assets/icon.png` until it has its own |
| Links | Source and issues: the UniversalAdmin repository; wiki: `docs/extensions/voicechat.md` |
| Dependencies | UniversalAdmin (required), Simple Voice Chat (required) - set by `release.yml` |

### Summary

> Simple Voice Chat for UniversalAdmin: mute in the chat, in the voice chat or both, silent vanish, voice status on the profile and a staff voice group.

### Description

---

# UniversalAdmin Voice Chat

The official [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)
extension for [UniversalAdmin](https://modrinth.com/plugin/universaladmin) -
mutes that reach the voice chat, and a voice group for your staff.

## Highlights

- **Chat, voice or full mutes.** `/mute` silences a player everywhere,
  `/chatmute` only in the chat, `/voicemute` only in the voice chat - also
  in the punish menu and in punishment templates. The player sees why
  nobody hears them.
- **Silent vanish.** Vanished staff cannot be heard - proximity voice
  would give them away.
- **Staff voice group.** `/admin voicegroup` - hidden and isolated: only
  staff hear each other.
- **Voice status on the profile** - installed, connected, which group - and
  one click takes a player out of a group.
- **Networks.** Mutes apply on every server at once.
- Every action in UniversalAdmin's audit log.

## Requirements

- [UniversalAdmin](https://modrinth.com/plugin/universaladmin) 1.5 or newer.
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) on the
  server.

[Documentation](https://github.com/nicki41/UniversalAdmin/blob/main/docs/extensions/voicechat.md) ·
[Source](https://github.com/nicki41/UniversalAdmin) ·
[Issues](https://github.com/nicki41/UniversalAdmin/issues)

---
