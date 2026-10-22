# Network Mode (Velocity / BungeeCord)

UniversalAdmin on a proxy network: install it on every server behind your
proxy, point them all at the **same MySQL/MariaDB database**, switch on
network mode, and the servers know each other.

| What you get | How |
|---|---|
| A **Network** menu (`/admin` → Network): every server with players, TPS and version - also servers without UniversalAdmin, as the proxy reports them - and the players on each | heartbeat in the shared database, plus the proxy's server list |
| **Where is someone?** `/admin find Steve` - "Steve is on survival (for 12m)" | shared player list |
| **Teleports across servers**: `/admin tp Steve` takes you to Steve's server, right next to him; `/admin tphere Steve` brings him to you; `/admin send Steve minigames`; `/admin connect survival` | proxy messaging + an arrival teleport on the target server |
| **Bans, mutes, warnings, kicks and freezes work everywhere at once**: ban Steve on the lobby while he plays on survival - survival removes him from the whole network immediately | shared punishments + a message between the servers |
| **Staff chat across servers**: `[Staff] [lobby] Mod: hi` | message channel |
| **Staff notifications and report alerts from every server**, tagged with the server name | message channel |
| **Network alerts**: `/admin alert <message>` on every server | message channel |
| **One audit log** for the whole network, every entry tagged with its server | shared database |
| Commands find **every player of the network**, also ones who only ever played on another server | shared player profiles |
| **Maintenance for one server or the whole network**: `/admin maintenance on` closes this server, `/admin maintenance network on` every server at once | shared database + a message between the servers |
| **A network whitelist**: `/admin whitelist network on` - one list for every server (and the proxy), on top of each server's own | shared database + a message between the servers |
| **Vanish across the network**: a vanished player stays vanished when switching servers and does not show up in the network list, `/admin find` or player counts for anyone without `universaladmin.bypass.vanish` | heartbeat in the shared database |

Optional: the **UniversalAdmin Velocity** or **BungeeCord** plugin turns banned players away at
the proxy itself - before they reach any server, including servers without
UniversalAdmin - and keeps the proxy closed during network maintenance and to
players who are not on the network whitelist while that is on.

## Setup

### 1. The proxy

UniversalAdmin works with **Velocity** (recommended) and **BungeeCord /
Waterfall**. Set up player-info forwarding as usual - for Velocity
(`velocity.toml`):

```toml
player-info-forwarding-mode = "modern"

[servers]
lobby = "127.0.0.1:25566"
survival = "127.0.0.1:25567"
```

and keep `bungee-plugin-message-channel = true` (the default) - that is the
channel UniversalAdmin uses to move players and to ask the proxy for its
server list. On every Paper server, `config/paper-global.yml`:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: '<the content of the proxy's forwarding.secret>'
```

(BungeeCord: `settings.bungeecord: true` in `spigot.yml` instead.) Real IP
addresses and UUIDs then reach the servers - IP bans need that.

### 2. One database for all servers

Every server's `plugins/UniversalAdmin/config.yml`:

```yaml
database:
  type: mysql
  host: db.example.net
  port: 3306
  database: universaladmin
  username: universaladmin
  password: "..."

network:
  enabled: true
  # exactly the name from velocity.toml [servers]
  server-name: lobby
```

`server-name` may stay empty - UniversalAdmin then asks the proxy when the
first player joins and uses that name. Setting it is better: the server
appears in the network list right from the start.

Restart the servers. The log says
`Network mode: this server is 'lobby'.` - and `/admin status` shows
`Network: lobby (2 server(s), 0 player(s))`.

All servers may start at the same time, also on an empty database: the
schema is created under a database lock, one server after the other.

### 3. Optional: UniversalAdmin Velocity / BungeeCord

Put `universaladmin-velocity-<version>.jar` (Velocity) or
`universaladmin-bungee-<version>.jar` (BungeeCord and its forks) from the
GitHub release or Modrinth into the proxy's `plugins/` folder, start the
proxy once, then fill in `plugins/universaladmin-velocity/config.yml`
(`plugins/universaladmin-bungee/config.yml` on BungeeCord) with the **same
database**:

```yaml
database:
  host: db.example.net
  port: 3306
  database: universaladmin
  username: universaladmin
  password: "..."
```

Restart the proxy. `/uavelocity` (permission
`universaladmin.velocity.status`) or `/uabungee`
(`universaladmin.bungee.status`) shows whether the checks run and how many
logins they refused. Both plugins do the same; the file and its options are
identical. It only reads from the database. If the proxy starts
before the database is ready, it keeps checking on every login instead of
giving up. `ban-check.allow-on-error` decides what happens while the database
is unreachable: let players in (default - the servers still check on join)
or keep everyone out. The ban screen texts are in the same file.

During [network maintenance](#maintenance) the proxy refuses every login
except players with `universaladmin.bypass.maintenance` - on the proxy, so
from a permissions plugin there (see [Permissions](#permissions)) - and names
on the network allow-list. The server list shows `maintenance-check.motd`
and, in red instead of the player count, `maintenance-check.version-text`
(empty = unchanged). `maintenance-check.enabled: false` switches this off;
the servers still refuse players on join. A server's own maintenance does not
concern the proxy: players are refused when they switch to that server.

While the [network whitelist](modules/whitelist.md#network-whitelist) is on,
the proxy also refuses every login that is not on it, except players with
`universaladmin.bypass.network-whitelist` on the proxy
(`whitelist-check.enabled`, message `messages.not-whitelisted`).

Works on Velocity 3.4+ and 4.x, and on BungeeCord 1.21+ (Java 21). On
BungeeCord a player's permissions are only known once they logged in, so a
ban refuses right away and maintenance and the network whitelist right after
the login - before the player reaches a server.

## Settings

| Key | Default | |
|---|---|---|
| `network.enabled` | `false` | Network mode on/off. Needs `database.type: mysql`. Restart. |
| `network.server-name` | `""` | This server's name as the proxy knows it. Empty: asked from the proxy. Restart. |
| `network.poll-interval` | `1s` | How often messages from the other servers are fetched (250ms - 10s). Restart. |
| `network.list-other-servers` | `true` | Also list servers without UniversalAdmin (from the proxy). |

## Permissions

| Node | Allows |
|---|---|
| `universaladmin.network.view` | Network menu, `/admin network`, `/admin find` |
| `universaladmin.network.goto` | Go to another server or to a player on another server (`/admin tp`, `/admin connect`) |
| `universaladmin.network.send` | Move other players between servers (`/admin tphere`, `/admin send`) |
| `universaladmin.network.alert` | `/admin alert` |

Network maintenance needs `universaladmin.server.maintenance.network` in
addition to `universaladmin.server.maintenance` - see [Maintenance](#maintenance).

**OP and permissions count per server.** `op Steve` on the lobby makes Steve
an operator of the lobby only - not of survival and not of the proxy
(Velocity has no OP at all). UniversalAdmin does not copy rights between
servers on purpose: who may ban on which server is a decision, not
something to sync silently. For the same rights everywhere use a permissions
plugin on every server **and** on the proxy with one shared database -
[LuckPerms](https://luckperms.net/) does exactly that (`storage-method:
MariaDB`, the same database settings in every LuckPerms config, optionally
`messaging-service: sql` for instant updates). Give your staff a group with
`universaladmin.*` or individual nodes from
[permissions.md](permissions.md). Without LuckPerms, the log of a server in
network mode says so at startup.

## Maintenance

Two kinds, each with its own reason, message and allow-list:

| | Command | Closes | Permission |
|---|---|---|---|
| **This server** | `/admin maintenance on [kick] [reason]` | only the server you run it on - the others stay open | `universaladmin.server.maintenance` |
| **The whole network** | `/admin maintenance network on [kick] [reason]` | every server with UniversalAdmin at once, and the proxy with UniversalAdmin Velocity | additionally `universaladmin.server.maintenance.network` |

`off`, `status`, `list`, `allow <player>` and `deny <player>` work the same
way, with `network` in front for the network's. `/admin maintenance status`
shows both. The GUI (`/admin` → Server → Manage Maintenance Mode) has a
second row for the network.

- A player gets in only if **neither** refuses them: the bypass permission
  `universaladmin.bypass.maintenance` passes both, the two allow-lists are
  separate. If both refuse, the network's message is shown.
- `kick` removes everyone without access: for this server's maintenance they
  go to the proxy's fallback server, for the network's they leave the
  network - on every server.
- Switching the network's maintenance reaches the other servers within a
  second (message channel) and is re-read every 10 seconds anyway; a server
  that starts later reads it from the database.
- Give every server a `network.server-name`. A server without one learns its
  name from the proxy when the first player joins; until then it does not
  know its own maintenance yet and lets that first player in - once the name
  is known, players its maintenance refuses are removed again.

## How it works

- **Heartbeat.** Every 5 seconds each server writes its row (players, TPS,
  version) into `ua_network_servers` and its players into
  `ua_network_players`; joins and quits are written immediately. A server
  without a heartbeat for 20 seconds shows as offline.
- **Messages.** A small table, `ua_network_messages`, is the channel between
  the servers: each server fetches what is new every `poll-interval`
  (usually within a second). Old messages are deleted after five minutes.
- **The proxy.** Moving players, removing a banned player from the *whole*
  network (instead of the proxy moving them to the fallback server), and the
  list of servers without UniversalAdmin go through the standard BungeeCord
  plugin messaging channel that Velocity and BungeeCord both implement. Those
  requests travel through a player's connection, so they work while at least
  one player is online on the server sending them.
- **The database** is the truth for everything that lasts: punishments,
  notes, reports, the audit log, player profiles. Bans and mutes are checked
  against it on join and on every chat message, so they apply on every
  server even without a message.

## Limits

- **All servers with UniversalAdmin need the same database.** SQLite can't
  be shared - with `database.type: sqlite` network mode stays off (the log
  says so).
- **Servers without UniversalAdmin** appear in the network list with their
  players, and staff can be sent there - but nobody there can place you next
  to a player, and their bans are only enforced at the proxy (UniversalAdmin
  Velocity) or on the next UniversalAdmin server.
- **IP bans** need the player on the server where the ban is issued (their
  address is only known there): `/admin tp <player>` first, or ban by name.
- **Freeze** and **vanish** follow the player when they switch servers (vanish
  also with `vanish.restore-on-reconnect: false`); staff mode, godmode and
  live inventory editors are per server. Another server learns that someone
  vanished with its next heartbeat - a few seconds.
- **Gamerules and worlds** stay per server - they usually differ between
  servers. Maintenance and the whitelist can be either - see
  [Maintenance](#maintenance) and the
  [network whitelist](modules/whitelist.md#network-whitelist).
