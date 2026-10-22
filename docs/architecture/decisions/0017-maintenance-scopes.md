# 0017 - Maintenance per Server or for the Whole Network

## Status

Accepted (1.2.1)

## Context

Maintenance mode stored a single row (`server_maintenance_state`,
`id = 1`). Without network mode that is fine. In network mode every server
reads the same database, but each one kept its own in-memory copy: putting
survival into maintenance left the lobby open until the lobby restarted -
and from then on the lobby was in maintenance too. The documentation
promised maintenance per server; the storage did not deliver it.

At the same time a network often needs the opposite: close everything at
once for an update, including the proxy, so players are not bounced from
one closed server to the next.

## Decision

- **Two scopes** (`MaintenanceScope.SERVER`, `NETWORK`), each with its own
  reason, message and allow-list. A player gets in only if neither
  refuses them; `universaladmin.bypass.maintenance` passes both. The
  network's maintenance is checked first, so its message wins.
- **One row per scope key** in `ua_maintenance` (migration 1012): `local`
  without network mode, `server:<name>` per network server, `network` for
  the whole network. The old row is copied to `local`; a network server
  without a row of its own reads `local` until it saves one, so an upgrade
  changes nothing at that moment. The old table stays for a downgrade.
- **Separate actions and a separate permission** for the network scope
  (`server.maintenance.network.*`, `universaladmin.server.maintenance.network`)
  instead of a scope field on the existing actions: an action has one
  permission, and the audit log should say which maintenance was changed.
  The network actions are refused without network mode.
- **Propagation:** after saving the network row the server publishes
  `server.maintenance` on the message channel; the others re-read the row
  (and remove refused players when asked to). Every server also re-reads
  both rows every 10 seconds, which covers a lost message and an unnamed
  server learning its proxy name.
- **The proxy** (UniversalAdmin Velocity) reads only the `network` row, for
  every login (cached for 3 seconds) and the server list. It fails open:
  if the row cannot be read the servers still check on join. A server's own
  maintenance stays the server's business - the proxy cannot know which
  server a player will end up on.

## Consequences

- One server of a network can be closed without touching the others, and
  the whole network with one command.
- Rights on the proxy come from its permissions plugin; without one, only
  the network allow-list lets staff through the proxy during network
  maintenance.
- A server without `network.server-name` lets the first player in before
  it knows its own name (and with it its maintenance), then removes them if
  that maintenance refuses them. Documented; setting the name avoids it.

## Alternatives

- **Keep the shared row and make it "the network's".** Fixes the
  inconsistency, but takes away maintenance for a single server, which is
  the more common case (a new world, a broken minigame).
- **Store the server's own state in a local file.** No name dependency,
  but two storage mechanisms for one feature, and no way to see or change
  another server's state later (web panel).
- **A scope field on the existing actions.** Fewer action ids, but one
  permission for both scopes.
