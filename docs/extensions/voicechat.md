# UniversalAdmin Voice Chat (Simple Voice Chat)

Source and releases: [universaladmin-mc/universaladmin-voicechat](https://github.com/universaladmin-mc/universaladmin-voicechat).

The official extension for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat):
mutes reach the voice chat, vanished staff stay silent, the profile shows a
player's voice status, and staff get a voice group of their own. Why it
works the way it does: [ADR 0022](../architecture/decisions/0022-mute-kinds-and-voice-chat.md).

## Setup

1. Install Simple Voice Chat (the Paper/Bukkit plugin) on the server.
2. Put `universaladmin-voicechat-<version>.jar` from the
   [GitHub releases](https://github.com/universaladmin-mc/universaladmin-voicechat/releases) into
   `plugins/` (it needs UniversalAdmin 1.5 or newer).
3. Restart. `/admin modules` lists **Voice Chat**.

In a network, install it on every server where players talk.

## Mutes in the voice chat

With the extension, a mute can silence a player in the chat, in the voice
chat, or both:

| Kind | Command | Text chat | Voice chat |
|---|---|---|---|
| Full | `/mute`, `/tempmute` | blocked | blocked |
| Chat only | `/chatmute` | blocked | free |
| Voice chat only | `/voicemute` | free | blocked |

- The punish form asks **Mute in** for every mute; templates take
  `chatmute [duration]` and `voicemute [duration]` steps.
- A muted player's microphone is dropped on the server, and the action bar
  tells them why (at most every few seconds).
- Mutes from before 1.5.0, and every `/mute`, are full mutes. `/unmute`
  lifts every kind.
- In a network the mute takes effect on every server at once.

## Vanish

Vanished staff cannot be heard - proximity voice would give them away.
In the staff voice group they can still talk. Switch it off with
`voicechat.silence-vanished: false`.

## Staff voice group

`/admin voicegroup` (shortcut `/voicegroup`) joins the staff voice group, or
leaves it. The group is hidden, isolated - its members hear only each other
and nobody outside hears them - and has a random password: players only get
in through UniversalAdmin.

## Profile

**Voice Chat** on the profile of a player on this server: whether the mod is
installed and connected (or switched off by the player), and which group
they are in. A click takes them out of that group.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.voicechat.staffgroup` | op | Join and leave the staff voice group |
| `universaladmin.voicechat.manage` | op | See the voice status on profiles, take players out of a group |
| `universaladmin.moderation.mute` / `.tempmute` | op | Mutes of every kind (core permissions) |

## Settings

In-game under `/admin` → Settings → voicechat, or in `config.yml`:

| Key | Default | Meaning |
|---|---|---|
| `voicechat.silence-vanished` | true | Vanished staff cannot be heard (except in the staff voice group) |
| `voicechat.notices` | true | Action bar notice for muted and vanished players who talk |
| `voicechat.staff-group-name` | Staff | Name of the staff voice group, used when it is created |

## Audit log

Joining or leaving the staff group and taking a player out of a group are
actions with audit entries (`voicechat:staff-group`,
`voicechat:remove-from-group`). Mutes are logged by the core as always.

## Notes

- Simple Voice Chat itself is not open source; its API is published for
  addons. The extension compiles against `voicechat-api` 2.5.0 and bundles
  none of it.
- Developers: a voice chat integration registers a
  `dev.universaladmin.voice.VoiceChat` service; while one is registered the
  core offers mute kinds. The voice part of a mute is
  `PlayerStatus#voiceMute` (API 1.4).
