# 0021 - A BungeeCord Plugin on Shared Proxy Code

## Status

Accepted (1.4.0)

## Context

Network mode works behind Velocity and BungeeCord, but only Velocity had
the optional proxy plugin that refuses banned players at the proxy (and,
since 1.2.1 and 1.3.0, closes it during network maintenance and to players
not on the network whitelist). Many networks still run BungeeCord.

## Decision

- **One implementation of the checks, two adapters.** The checks move into
  `proxy/common` (`ProxyChecks` with the lookups, the ban screen and the
  settings); `proxy/velocity` and the new `proxy/bungee` only translate
  their proxy's events. Both plugins read the same `config.yml` and behave
  the same.
- **A refusal names the node that lifts it.** A ban cannot be bypassed;
  maintenance and the network whitelist can, with the same nodes the
  servers check. Velocity knows permissions at login and decides there.
  BungeeCord knows them only after the login, so the BungeeCord plugin runs
  the checks (off the event thread) in `LoginEvent`, refuses a ban there and
  decides the rest in `PostLoginEvent` - before the player reaches a server.
- **Adventure on BungeeCord.** The texts are MiniMessage everywhere.
  BungeeCord has no Adventure, so the plugin shades and relocates Adventure,
  MiniMessage and SnakeYAML and turns components into BungeeCord components
  through the legacy format with hex colours (kick screens and the server
  list need nothing more).
- **Distribution like Velocity's:** `universaladmin-bungee-<v>.jar` on the
  GitHub release and as version `<v>-bungee` of the same Modrinth project
  (loaders `bungeecord`, `waterfall`).

## Consequences

- A fix to a check fixes both proxies; the Velocity plugin's behaviour is
  unchanged.
- The BungeeCord jar is larger (about 2 MB) because of the shaded libraries.
- A player refused on BungeeCord for maintenance or the whitelist completes
  the login before being disconnected; they never reach a server.

## Alternatives

- **Copying the Velocity plugin.** Quicker once, then two copies of every
  check to keep in step.
- **adventure-platform-bungeecord.** Full Adventure support including click
  events, not needed for kick screens and the server list, and another
  dependency to follow.
