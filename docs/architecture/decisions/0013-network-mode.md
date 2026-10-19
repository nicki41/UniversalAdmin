# 0013 - Network Mode over the Shared Database

## Status

Accepted (1.1.0)

## Context

Many servers run behind a Velocity or BungeeCord proxy. Staff there expect
one admin view of the whole network: which servers exist, who is where,
teleports to a player on another server, and bans, mutes and staff chat
that apply everywhere at once. UniversalAdmin already supports a shared
MySQL/MariaDB database, and punishments, notes, reports and the audit log
live there. What was missing is live state (who is online where) and a way
for servers to tell each other about events (a ban, a staff chat message,
"teleport this player when they arrive").

## Decision

- **The shared database is the channel.** With `network.enabled`, every
  server writes a heartbeat (`ua_network_servers`), its online players
  (`ua_network_players`) and messages for the others
  (`ua_network_messages`: id, topic, sender, optional target server,
  payload). Each server polls for new messages every `network.poll-interval`
  (default 1s) on the async storage executor, with a small id lookback and
  a dedupe set so rows committed out of order are not lost. Old rows are
  pruned. No extra infrastructure (Redis, a message broker) and no proxy
  plugin are required.
- **The proxy is used only for what only it can do** - moving a player to
  another server, removing a player from the whole network, and listing
  servers and players without UniversalAdmin - through the standard
  BungeeCord plugin messaging channel that Velocity and BungeeCord both
  implement (`Connect`, `ConnectOther`, `KickPlayerRaw`, `GetServer`,
  `GetServers`, `PlayerList`).
- **Cross-server actions stay actions.** `network.goto`, `network.bring`,
  `network.send`, `network.connect` and `network.alert` go through the
  `ActionExecutor` like everything else (permission, validation, audit).
  A cross-server teleport is a proxy move plus an *arrival* message: the
  target server teleports the player once they join there.
- **The database stays the source of truth** for everything that lasts.
  Bans and mutes are checked against it on join and on every chat message,
  so they apply on every server even if a message is missed; the
  `moderation.applied` message only makes them take effect immediately on
  the server where the target is online.
- **`NetworkService` is part of the public API**
  (`UniversalAdmin.network()`); without network mode it is a no-op
  implementation, so modules and extensions never branch on configuration.
- **Schema migrations take a database lock** (`GET_LOCK` on MySQL) and are
  applied by version set, because several servers start against the same
  database at once.
- **UniversalAdmin Velocity** is a separate, optional, read-only proxy
  plugin: it reads the punishments table on login and refuses banned
  players before they reach a server. It shares no code with the Paper
  plugin besides the table layout.

## Consequences

- Network mode needs MySQL/MariaDB; with SQLite it stays off.
- Messages arrive within about one poll interval - fine for admin events,
  not for anything per tick. Polling costs one small indexed query per
  server per second.
- Proxy requests travel over a player connection, so they need at least
  one player online on the sending server. Server-only operations (status,
  alerts, chat) do not.
- Per-server features (maintenance, whitelist, worlds, vanish, staff mode)
  remain per server.

## Alternatives

- **Redis pub/sub.** Lower latency, but another service every server owner
  would have to run and secure. Can be added later behind the same
  `NetworkService` interface.
- **Proxy plugin as the hub** (all servers talk to a Velocity/BungeeCord
  plugin). Would make the proxy plugin mandatory and need two proxy
  implementations; plugin messages also only flow while a player is
  connected.
- **Only shared tables, no messages.** Bans would apply on the next join
  only, and staff chat or teleports across servers would be impossible.
