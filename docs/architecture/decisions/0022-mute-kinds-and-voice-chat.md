# 0022 - Mute Kinds and the Voice Chat Extension

## Status

Accepted (1.5.0)

## Context

With Simple Voice Chat on a server, a muted player can keep talking: a
mute only ever blocked the text chat. Staff want to choose - silence a
player in the chat, in the voice chat, or both - and a mute should reach
the voice chat on every server of a network, like every other punishment.

## Decision

- **One mute, three kinds.** A mute (`MUTE`/`TEMP_MUTE`) has a kind:
  `CHAT`, `VOICE` or `FULL` (`dev.universaladmin.status.MuteKind` in the
  API). It is stored with the punishment, in `punishments.mute_kind`
  (migration 1016). `NULL` reads as `FULL`, so every mute issued before
  1.5.0 also silences the voice chat - a mute was always meant to silence,
  and one that still lets someone talk would surprise staff.
- **The core enforces the chat, the extension the voice chat.** The chat
  listener blocks `CHAT` and `FULL` mutes. The voice chat extension asks
  `PlayerStatus#voiceMute` - answered from memory, safe off the main thread,
  which matters because microphone packets arrive on the voice chat's own
  thread - and drops the packets of `VOICE` and `FULL` mutes.
- **The choice only appears where it means something.** An extension that
  connects a voice chat registers a `dev.universaladmin.voice.VoiceChat`
  service. Only while one is registered do the punish form and the template
  preview offer the kind, and only then does `/voicemute` work; without one,
  mutes look exactly as before. `/mute` and `/tempmute` stay full mutes;
  `/chatmute` and `/voicemute` (each with an optional duration) choose the
  kind, and templates can use `chatmute [duration]` and
  `voicemute [duration]` steps. `/unmute` lifts every kind.
- **Immediately across the network.** The kind travels in the shared table;
  the network message that tells other servers about a mute now also makes
  them reload that player's status, so a voice mute takes effect there at
  once instead of within a minute.
- **The extension: UniversalAdmin Voice Chat** (`universaladmin-voicechat`),
  for Simple Voice Chat. Besides voice mutes:
  - **Vanished staff stay silent** (`voicechat.silence-vanished`, on by
    default) - proximity voice would give them away.
  - **A staff voice group** (`/admin voicegroup`): hidden and isolated, so
    only staff hear each other; joined through UniversalAdmin, never through
    a password.
  - **The profile** shows whether the mod is installed and connected and
    which group the player is in; staff can take a player out of a group.
    Both actions are audited.
- **Built against the published API only.** Simple Voice Chat itself is not
  open source; its API is published for addons. The extension compiles
  against `voicechat-api` 2.5.0 (the oldest version with everything it uses)
  and bundles none of it.

## Consequences

- A voice mute needs the extension on every server where players talk - the
  documentation says so. Servers without it still store and show voice
  mutes, but cannot enforce them.
- Every mute before 1.5.0 is a full mute. Staff who want an old mute to
  cover only the chat lift it and mute again with `/chatmute`.
- `PlayerStatus.Mute` gains a `kind`; the old two-argument constructor stays
  and means `FULL`. API version 1.4.

## Alternatives

- **Separate punishment types** (`VOICE_MUTE`, `TEMP_VOICE_MUTE`, ...).
  Doubles every switch, list and import mapping for what is one punishment
  with a scope.
- **A voice mute table of the extension's own.** A full mute would then be
  two punishments in two places, with two histories and two unmutes.
- **Mutes block the voice chat always, without a choice.** Simpler, but
  staff asked for exactly that choice.
