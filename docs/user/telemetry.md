# Anonymous Usage Statistics (Telemetry)

UniversalAdmin can, at regular intervals, send a very small, anonymous
message ("heartbeat"). This document **fully** describes what is transmitted,
what is explicitly not transmitted, how often it happens, and how to turn it
off.

The rule behind it: **nothing is collected that isn't documented here.** A
new field in the heartbeat and a change to this document are the same
change - the test
`TelemetryPayloadTest#sendsNoFieldBeyondTheSevenDocumentedOnes` fails if
someone adds a field.

## Current Status: Nothing Is Sent By Default

**As of today, UniversalAdmin sends nothing unless you configure an
endpoint.** `telemetry.endpoint` defaults to empty, and there is no built-in
fallback host baked into the software. The wire format (see below) is
generic - `pluginId`/`pluginVersion` rather than a UniversalAdmin-specific
field name - because it's designed for a shared backend any of nicki41's
plugins can report to (a separate project, `nicki41-telemetry`), not a
UniversalAdmin-only one; nothing about that changes what's collected or the
opt-out below. As long as no endpoint is configured:

- no request is made anywhere,
- no installation id is generated,
- no file is created for one,
- no background timer runs.

The log says so at startup, too:

```
Anonymous usage statistics are enabled but no endpoint is configured
(telemetry.endpoint is empty), so nothing is sent.
```

The rest of this document describes what happens once there is an official
endpoint (or someone configures their own).

## Purpose

Three questions should become answerable - and only those:

1. **How many installations are active?** I.e. is continued development
   worth it, and how fast does a release spread.
2. **How many players in total are online across these servers?** An
   aggregated number across all installations.
3. **How do versions distribute?** UniversalAdmin, Minecraft, and Java
   version - the basis for deciding what still needs to be supported.

Not the purpose: identifying, comparing, publicly listing, or ranking
individual servers. There is no server list and no way to build one from the
data (see below - no address and no name is ever transmitted).

## Exactly What Is Transmitted

A heartbeat is an HTTP POST with exactly this JSON body - seven fields,
nothing more:

```json
{
  "pluginId": "universaladmin",
  "installationId": "0123456789abcdef0123456789abcdef",
  "pluginVersion": "0.1.0-alpha",
  "minecraftVersion": "1.21.4",
  "javaMajorVersion": 25,
  "onlinePlayers": 17,
  "maxPlayers": 100
}
```

| Field | Meaning | What it's for |
|---|---|---|
| `pluginId` | fixed: `"universaladmin"` (`TelemetryPayload.PLUGIN_ID`) | scopes a heartbeat to this plugin on a backend shared with others |
| `installationId` | 128 random bits, see below | Merges two heartbeats from the same installation, so "active installations" doesn't just mean "requests received" |
| `pluginVersion` | plugin version | version distribution |
| `minecraftVersion` | e.g. `1.21.4` | which Minecraft versions still need support |
| `javaMajorVersion` | e.g. `25` | whether raising the Java requirement would strand installations |
| `onlinePlayers` | count only | aggregated "Players Online" total |
| `maxPlayers` | server slot count | scale for that number |

Technically unavoidable, as with any HTTP request: the sending server's IP
address is known to the receiving endpoint at the transport layer. It is
**not part of the payload**, is not used as an identifier, and how a future
backend handles it (not logging it, or discarding it immediately) belongs in
that backend's own privacy policy - see "Open Items" below.

### Two Fields Deliberately *Not* Sent

Both were considered and dropped under the minimalism principle:

- **Paper build string** (`git-Paper-123 (MC: 1.21.4)`): doesn't answer any
  of the three questions above beyond what `minecraftVersion` already
  answers, but would be a finer-grained distinguishing mark.
- **Client timestamp**: the backend's own receipt time is the authoritative
  time anyway (see "What 'Active Server' Means"), and a clock on a foreign
  server can't be trusted. A field that adds nothing reliable isn't sent.

### What Is Explicitly Never Transmitted

- Server IP, hostname, domain, port, MOTD, server name
- Player names, player UUIDs, player IP addresses
- Chat messages, executed commands
- World names, coordinates, world sizes
- other installed plugins
- File contents, database contents, audit log entries, configuration values
- Hardware characteristics, MAC addresses, serial numbers, machine
  fingerprints
- OS username, absolute file paths

The player count is exclusively a number. There is no way to reconstruct a
player identity from it.

## The Installation ID

- Generated **once** - on the first start where telemetry could actually send
  (enabled **and** an endpoint configured).
- 128 bits from `SecureRandom`, represented as 32 hex characters. **Not**
  derived from IP, MAC address, hardware, hostname, server address, file
  path, or player data - from nothing at all. Pure randomness.
- Deliberately not in UUID string format, so it can never be confused with a
  player UUID in any log or database.
- Lives in `plugins/UniversalAdmin/installation-id.yml` and stays the same
  across restarts.
- If the file is deleted, a new id is created on the next start. The old
  installation is then no longer attributable; a backend would simply stop
  counting it once its activity window (see below) expires.
- Anyone who doesn't want the id to follow a server copy just deletes the
  file when copying the plugin folder. That's exactly why it isn't in
  `config.yml`.

## Interval

- The **first** heartbeat arrives at the earliest around 5 minutes after a
  successful start (plus a random component) - never during startup.
- After that: every `telemetry.interval` (default 30 minutes, minimum 5
  minutes, maximum 24 hours), **plus a random extra of up to half that
  value**. With default configuration, that's roughly every 30-45 minutes.
- The random extra (jitter) is drawn fresh for every interval, so many
  servers don't send in lockstep - e.g. after a shared outage.

## Failure Behavior

Telemetry is the least important thing this plugin does, and it behaves that
way:

- Never runs on the Paper main thread. Player counts are read on the main
  thread (that's main-thread state), the request itself runs on a background
  thread - see [docs/architecture/threading.md](../architecture/threading.md).
- Short timeouts (5s connect, 10s total).
- No retry, no queue, no caching. A lost heartbeat is lost.
- An endpoint outage has **no** effect on the server or on any plugin
  functionality.
- No log spam: the first failure per server run is a single warning,
  everything after that is `FINE` only.
- Endpoint responses are discarded, never parsed. A backend can't instruct
  the server through this channel.
- Redirects (HTTP redirects) aren't followed: a relocated endpoint needs to
  be reconfigured, not automatically chased to a different host.

## Turning It Off (Opt-out)

In `plugins/UniversalAdmin/config.yml`:

```yaml
telemetry:
  enabled: false
```

Then `/admin reload` (or a server restart). With `enabled: false`:

- no request of any kind, including no "necessary" or "essential" one,
- no payload is even built,
- no installation id is generated or read,
- no timer runs.

`telemetry.enabled` is read fresh from configuration on **every** heartbeat.
An `/admin reload` with `enabled: false` stops statistics immediately, no
restart needed.

The full settings overview is in [configuration.md](configuration.md).

## What "Active Server" Means

For a future backend, the semantics are recorded here so later numbers aren't
misleading:

- **Active installation** = a unique `installationId` that sent at least one
  valid heartbeat within the **last 24 hours** (counted from server-side
  receipt time).
- **Not** the total count of installation ids ever seen. Presenting a
  lifetime count as "active servers" would simply be wrong.
- **Players Online** = the sum of `onlinePlayers` from the **most recent**
  valid heartbeat of each active installation. Not the sum of all heartbeats
  in a time window (that would count the same player dozens of times).

That makes numbers like this possible later:

```
UniversalAdmin Network
Active Servers: 1,284
Players Online: 18,492
```

Individual servers are not publicly listed - there's no data for that
either.

## Implementation

| Class | Responsibility |
|---|---|
| `InstallationIdentity` / `InstallationIdentityStore` | generating and persisting the id |
| `TelemetryPayload` | the heartbeat, exactly as it goes over the wire |
| `TelemetryEnvironment` / `PlayerCounts` | the payload's inputs |
| `TelemetryClient` | interface; `HttpTelemetryClient` (JDK HTTP client) and `NoOpTelemetryClient` (default) |
| `TelemetryService` | builds and sends a heartbeat; enforces the guarantees above |
| `TelemetryScheduler` | interval, jitter, lifecycle |
| `TelemetryBootstrap` | wiring at startup, three outcomes (off / no endpoint / active) |

All under `dev.universaladmin.telemetry`, no new dependency (the HTTP client
and the JSON encoder come from the JDK, or are six lines). The tests under
`src/test/java/dev/universaladmin/telemetry` make zero real network requests.

## Open Items

Stated honestly rather than claiming compliance:

- **There is no privacy policy yet.** Before a real endpoint goes into
  operation, a separate review has to happen (including how the transport IP
  is handled). This document describes the technical implementation; it is
  not a legal guarantee and not a statement about GDPR compliance.
- **Retention period** is a backend decision and still open. The goal:
  short-lived raw data, aggregates afterward.
- **Opt-in instead of opt-out** was deliberately not chosen (the default is
  `enabled: true`), but as long as no endpoint exists, the practical effect
  is identical: nothing is sent. Before an endpoint goes live, this decision
  needs to be made deliberately again and announced with the release.
- **Modrinth** may require disclosing telemetry in the project description,
  depending on the rules in effect at the time - see
  [../release/modrinth.md](../release/modrinth.md).
