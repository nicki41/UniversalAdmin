# 0025 - Nick System

## Status

Accepted (1.7.0)

## Context

Staff want to play unrecognised - to watch a suspected cheater, or just to
play without being asked for help every minute. A nick has to hold up
everywhere a player looks: the chat, the tab list, the name above the head,
the skin, tab completion, the social interactions screen. Those last few
come from the client's player list, which carries the profile name - a
display name or a tab-list name alone leaks the real name through every one
of them.

At the same time UniversalAdmin keeps records about players: punishments,
notes, reports, the audit log, the shared player profiles. Those must stay
attached to the real person, whatever name they played under.

The rules forbid packet hacks and NMS in the core.

## Decision

- **A built-in module (`core:nick`)** with `/admin nick <name|random>
  [skin|own|random]`, `/admin nick off [player]`, `/admin nick list`,
  `/admin unnick [player]` (shortcuts `/nick`, `/unnick`), a menu page, a
  profile button and the permissions `universaladmin.nick.use`, `.others`
  and `.see`.
- **The profile is the nick.** A nick is put on with Paper's
  `Player#setPlayerProfile`: same UUID, the nick as the name, the skin of
  another account (Mojang-signed textures, looked up through Paper's profile
  completion and stored with the nick). Every client then shows the nick in
  the tab list, above the head and in its name lists, and
  `Player#getName()` returns it to every plugin - chat formats, death
  messages and join messages follow without UniversalAdmin knowing them.
  The display name is set to the nick as well.
- **Put on while joining.** A stored nick is loaded during the async login
  check and set at `PlayerJoinEvent` (lowest priority), before Paper
  announces the player to anyone - no flicker, no respawn. Set later (by
  `/nick`), Paper re-shows the player to everyone and respawns them in place
  for themselves.
- **Records keep the real identity.** `PlayerNames.real(...)` (API 1.6)
  answers the real name of a nicked player; actors (who did something),
  punishments, notes, reports, inventory edits, the player profiles and the
  maintenance allowlist use it. The audit service additionally normalises
  every entry's actor and target by UUID and keeps the nick in the
  metadata (`actor-nick`, `target-nick`), so nothing recorded by a module
  that only knew the nick ends up under the nick. **Actions a nicked staff
  member takes run under their real name** - a nick hides who you are while
  you play, not who punished someone.
- **Who sees through it.** Staff with `universaladmin.nick.see` see the real
  name next to the nick in UniversalAdmin's player lists, on the profile and
  in `/admin nick list`; everyone else sees a nicked player like any other.
  Commands find a nicked player by the nick and by the real name.
- **Names.** A nick is a valid Minecraft name that nobody is shown under
  right now (online here, online elsewhere in the network, another nick)
  and - unless `nick.allow-known-names` - that no player who has played here
  has. `ua_nicks` (migration 1017) has a unique index on the lower-case
  name, which settles a race between two servers. A player whose real name
  is somebody's nick takes it back when they join: that nick ends.
- **Across rejoins and the network.** The nick is stored in the shared
  database; with `nick.restore-on-rejoin` (default) it is put back on every
  join, otherwise only on a server switch (still listed on another server,
  the rule of ADR 0019). A change is announced on the network topic
  `core:nick`, and every server reloads all stored nicks once a minute, so
  `Nicks` (API 1.6) knows the nicks of players on the other servers too.
- **Extension API.** `dev.universaladmin.status.Nicks` (a service while the
  module runs): `nick(UUID)`, `byName(String)`, `active()`, and the
  permission constant `SEE_REAL_NAME`; plus `PlayerNames.real` and
  `PlayerNames.revealedTo(viewer, id)`. UniversalAdmin TAB uses them to give
  nicked players the tab-list group of an ordinary player.
- **Vanish metadata.** Unrelated to nicks but found on the way: vanish now
  also sets the `vanished` player metadata every vanish plugin sets, so
  other plugins (TAB, map plugins) recognise UniversalAdmin's vanish.

## Consequences

- Paper finds online players by the name they joined with
  (`Bukkit#getPlayerExact`, vanilla commands such as `/msg`) and does not
  update that index when the profile changes: other plugins' and vanilla
  commands keep finding a nicked player by their real name, and not by the
  nick (UniversalAdmin's own commands find both). To keep that index right,
  the real profile is given back while the player quits (hidden first, so
  nobody sees the name flash). After a respawn Paper also knows the nick;
  then the nick stays on at the quit, and Paper keeps the real name pointing
  at the departed player until they join again.
- The UUID stays the player's own - a client mod that shows UUIDs, or the
  UUID hover of a name in chat, leads to the real account. Death messages
  lose that hover for nicked players; other messages built by vanilla or
  other plugins may keep it.
- Prefixes and sorting of a tab-list plugin still follow the player's
  permission group - with TAB, UniversalAdmin TAB fixes that; other plugins
  need their own configuration.
- Proxy-side features (a proxy chat, a proxy tab list) see the real name:
  the nick lives on the Paper servers.
- Mojang's lookup is rate-limited; skins are cached for an hour, and a
  stored nick never needs a lookup again.

## Alternatives

- **Packet rewriting** (ProtocolLib, as most nick plugins do): exact control
  over what every viewer sees, including per-viewer real names - but packet
  hacks are excluded from the core, and every Minecraft version breaks them.
  A packet-based extension stays possible on top of the `Nicks` API.
- **Changing the name at login** (`AsyncPlayerPreLoginEvent#setPlayerProfile`):
  the server would know the player only by the nick, but the server's user
  cache and every other plugin's records (permissions, economy) would store
  the nick as the player's name, and the next `/ban <nick>` could hit the
  wrong account.
- **Display name and tab-list name only**: simple, but the real name stays
  in the client's player list - tab completion and the social interactions
  screen show it - and above the head.
