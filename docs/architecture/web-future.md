# Web App (Future)

This document also describes a **planned**, not-yet-built capability - see
[decisions/0006-optional-web-architecture.md](decisions/0006-optional-web-architecture.md)
for why no Gradle module exists for it today.

## Core Requirement: the Core Runs Fully Without the Web

The web app is optional and additive, not a prerequisite. A server operator
who only uses the in-game GUI/commands never installs a web-server part.
That's already structurally enforced: nothing in `dev.universaladmin.core`,
`.module`, `.action`, `.storage`, etc. has a dependency pointing toward
"web" - the dependency only points the other way (a future web layer
depends on the core, not the reverse).

## Same Services, Same Actions

The web app is meant to call the same application services and `Action`s as
the GUI and commands - no separate "web business logic" path. That's why
`Actor`/`ActorType` already has a `WEB` case (see [actions.md](actions.md))
and why GUI click handlers may not contain logic (see [gui.md](gui.md)):
any logic stuck only in a click handler would have to be rewritten for the
web app.

## Planned Building Blocks (Not Built)

- **`universaladmin-web` Gradle module** - separate process or embedded
  server (decision open), depends on `universaladmin-api` (see
  extensions-future.md), not directly on core internals.
- **REST API** over the same actions/services.
- **WebSockets/live updates** for dashboard widgets and live views (e.g.
  online players, performance graphs).
- **Web authentication**, separate from the Minecraft account (a server
  admin doesn't necessarily have a Minecraft account for web access) -
  mechanism open.
- **Dashboard widgets** as their own extension point (see
  extensions-future.md), so extensions can contribute their own web views
  without changing the core web code.

## What This Means for Today's Code

- No service/action may accept anything that only exists in an in-game
  context (e.g. requiring an `org.bukkit.entity.Player` directly as a
  parameter where a `UUID`/an `Actor` would do) - that would later force
  the web app to build a fake `Player`. See `Action`/`Actor` in
  [actions.md](actions.md) for the existing pattern.
- `MessageService`/`MessageKey` already exist separate from the GUI, so the
  same translations can later be reused by a web view. Concretely:
  `MessageService.get(key, args...)` returns a plain,
  parameter-substituted `String` (may still contain MiniMessage markup like
  `<red>`, but never an Adventure `Component` instance). The in-game layer
  converts that into a `Component` via
  `dev.universaladmin.localization.ComponentMessages`; a web view would
  instead translate the same string into HTML/CSS (MiniMessage tags → CSS
  classes or similar) - its own, not-yet-existing renderer, not part of
  `MessageService` itself. See
  [docs/development/settings.md](../development/settings.md#localization).
