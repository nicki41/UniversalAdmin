# 0019 - Network Whitelist and Vanish across the Network

## Status

Accepted (1.3.0)

## Context

After maintenance ([ADR 0017](0017-maintenance-scopes.md)) the whitelist was
the next per-server feature that networks asked to switch for all servers at
once. Looking at it showed the same flaw maintenance had: UniversalAdmin's
whitelist metadata (`whitelist_entries`, keyed by player) was one table for
every server of a network, so one server's expiry sweep could remove a
player from another server's native whitelist.

Vanish already followed a player between servers (the vanish marker is in
the shared database and is restored on join), but everything the network
shows - the network list, `/admin find`, player counts, tab completion,
placeholders - listed vanished staff to everyone.

## Decision

- **Whitelist metadata per scope**, with the keys of ADR 0017:
  `ua_whitelist` (scope + player, migration 1014), `local` without network
  mode and `server:<name>` per network server. The old rows are copied to
  `local`; on its first start a network server copies the ones for players
  on its own native whitelist into its own scope. The native whitelist stays
  the truth for a server's own membership.
- **The network whitelist is a second whitelist, not a synchronised native
  one**: scope `network` in the same table plus an on/off row in
  `ua_whitelist_state`, enforced by UniversalAdmin on every server
  (`PlayerLoginEvent`, from memory) and by UniversalAdmin Velocity at the
  proxy. It is a second `WhitelistService` implementation, so actions,
  commands and GUI pages are shared and only choose the scope. It needs
  `universaladmin.whitelist.network` on top of the usual node; players with
  `universaladmin.bypass.network-whitelist` pass.
- **Vanish in the network's view**: every heartbeat writes whether a player
  is vanished (`ua_network_players.vanished`, core migration 4), read from
  the Moderation module's `PlayerStatus` through the service registry so the
  network code does not depend on that module. `NetworkService#vanished` is
  the new API (1.3); `NetworkVisibility` filters lists for viewers without
  `universaladmin.bypass.vanish`. Server player counts written by the
  heartbeat no longer include vanished players.
- **A server switch keeps vanish** even with `vanish.restore-on-reconnect`
  off: a player who is still listed on another server while logging in here
  is switching, not reconnecting.

## Consequences

- One server's whitelist entries and expiry no longer touch another
  server's; temporary entries keep their expiry after the upgrade.
- The network whitelist works on servers that do not run UniversalAdmin
  only through the proxy plugin.
- Other servers learn that someone vanished with that server's next
  heartbeat (5 seconds); staff actions such as teleports still find vanished
  players by name - only what is shown is filtered.
- Adding a record component to `NetworkPlayer` was avoided: the API grows by
  one default method, which keeps 1.x extensions compatible.

## Alternatives

- **Synchronising every server's native whitelist** from a shared list.
  Simpler for players, but it would rewrite entries UniversalAdmin did not
  create (breaking the ownership rule of the whitelist module) and needs
  every server online to apply a change.
- **Hiding vanished players from the network snapshot entirely.** Rejected:
  staff who may see vanished players need them in the lists, and actions
  need to find them.
