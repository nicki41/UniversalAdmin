# 0020 - Alt Detection with Keyed Address Hashes

## Status

Accepted (1.4.0)

## Context

Banned players come back on a second account. Staff want to know when an
account connects from the same place as a banned one. The obvious data -
connection addresses - is personal data; many servers would rather not keep
a list of them, and some must not.

## Decision

- **Opt-in.** `moderation.alts.enabled` is `false`; while it is off nothing
  about addresses is written.
- **No addresses, only keyed hashes.** A login stores
  HMAC-SHA256(key, address) with the player and when it was first and last
  seen (`ua_player_addresses`, migration 1015). The key is random, created on
  first use and kept in the database (`ua_alt_key`) - every server of a
  network must compute the same hash, and a key per server would need
  copying by hand. IPv6 counts by its /64 network: providers give every
  household one and devices keep changing the rest, so full addresses would
  not match the same home twice. An IPv4 address in IPv6 form counts as the
  IPv4 address.
- **Retention.** Addresses not seen within `moderation.alts.retention`
  (default 90 days, at least 1 day) are deleted every hour, also while the
  feature is off. `/admin alts <player> forget` deletes one player's
  addresses on request.
- **Inform, do not decide.** Possible alts are shown on the profile and with
  `/admin alts`; a join that shares an address with a banned account gives
  staff a hint, on every server of a network. Refusing such logins is a
  separate setting, off by default, checked in `PlayerLoginEvent` so that
  `universaladmin.bypass.alt-check` can exempt players (the database work
  happens before, in the asynchronous pre-login).
- **Audited.** Viewing someone's alts and forgetting their addresses are
  actions with audit entries; the list itself never goes into the log.

## Consequences

- Shared connections (families, schools, dorms, mobile carriers) show up as
  "possible alts" - the wording and the default of the refusal setting say
  so.
- The hash is not reversible on its own, but whoever has both the database
  and the key can test addresses (the IPv4 space is small). The
  documentation says this plainly; the database already deserves the same
  care as the server logs, which contain addresses.
- Proxies without forwarding would show every player with the proxy's
  address; network mode requires forwarding anyway.

## Alternatives

- **Storing addresses in clear text** (most ban plugins do). Simpler and
  allows subnet matching, but keeps exactly the data many owners do not want.
- **A key per server in a file.** Better separated from the data, but a
  network would only match if every server got the same file.
- **Refusing alts by default.** Rejected: too many false positives for a
  check that cannot know who is behind an address.
