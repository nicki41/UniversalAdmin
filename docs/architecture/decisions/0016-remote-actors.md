# 0016 - Remote Actors: Running Admin Commands from Outside the Game

## Status

Accepted (1.2.0)

## Context

The Discord bot lets staff ban, mute or look up a player from Discord, and
the planned web panel will do the same from a browser. Both act for people
who are not on the server. They must not get a second, weaker path into
UniversalAdmin: the same permission checks, validation, actions, messages
and audit entries have to apply as for a player typing the command.

The command framework was built around a Bukkit `CommandSender`, and the
action pipeline around an `Actor` that is a player, the console or the
system.

## Decision

- **A new actor type, `REMOTE`** (`Actor.remote(name, permissions)`):
  someone acting through another application, authenticated by it and with
  permissions it maps - for the Discord bot, from the member's roles
  (`bot.roles`). The audit log shows the name the application gives
  (`Discord:Nick`).
- **`RemoteCommands`** (API service): runs `/admin <command line>` as such
  an actor through the very dispatcher players use. The command sees a
  stand-in `CommandSender` whose permission checks ask the actor and whose
  messages go back to the caller; commands that need a player in game reply
  that they are for players only. The stand-in is a dynamic proxy, so it
  keeps working when Paper adds methods to `CommandSender`.
- **No separate "Discord API"** for bans, mutes and so on: every slash
  command is translated into an `/admin` command line, after its options
  are validated (a player name cannot carry extra arguments). New commands
  and extension commands are usable from Discord through `/ua` without any
  change to the bot.
- **`StaffChat`** (API service): bridges send into the staff chat and
  subscribe to it; messages carry a `via` tag so a bridge never echoes its
  own messages, and remote servers show where a message came from.

## Consequences

- Whatever is possible in game is possible from Discord for the same
  permissions - and nothing more.
- Replies are chat messages; the bot shows them as plain text. Commands
  that only open a GUI have nothing to show remotely.
- The web panel can use the same two services.

## Alternatives

- **A dedicated moderation API for remote clients** (`ban(actor, player,
  ...)`). Cleaner types, but a second path that every new command would
  have to be added to, and easy to get out of sync with the in-game rules.
- **Running commands as the console.** Simple, but no per-member
  permissions and an audit log full of "CONSOLE".
