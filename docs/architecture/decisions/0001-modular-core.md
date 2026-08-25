# 0001 - Modular Core Instead of a Monolithic Plugin Class

## Status

Accepted

## Context

UniversalAdmin has to cover eight built-in areas (Players, Moderation,
Server, Worlds, Whitelist, Performance, Audit Log, Settings) and allow
external extensions in the long run. A single `JavaPlugin`
listener/command tangle per feature reliably leads to exactly the kind of
unmaintainable admin plugin this project is meant to avoid (see the project
philosophy in [../overview.md](../overview.md)).

There is also no dependency-injection framework (deliberately - see
Alternatives below), but still a need for clearly cut, independently
testable units.

## Decision

- A `Module` interface that models feature areas as self-contained units
  that register with shared registries instead of holding their own state.
- A single composition root (`UniversalAdmin`), assembled by hand in
  `UniversalAdminPlugin#onEnable` and passed via constructor to everything
  that needs it. This is the only deliberately allowed "god object" in the
  project.
- No DI-framework dependency (Guice, Spring, etc.). At the current size,
  manual wiring is easier to read and debug than a framework configuration,
  and it avoids another dependency.

## Consequences

- A new service means a new constructor parameter everywhere it's needed.
  That's more typing than a DI framework, but every dependency is visible in
  the code, not hidden behind an annotation.
- `UniversalAdmin` grows with every new cross-cutting service. Accepted, as
  long as it only *references registries/services*, never *contains business
  logic* - see the [development rules](../../development/architecture-rules.md).
- If manual wiring becomes unwieldy as the module count grows, a DI
  framework is a later, deliberate decision (a new ADR), not a silent
  rework.

## Alternatives

- **DI framework (Guice/Spring):** More boilerplate reduction with a large
  module count, but an additional core dependency and an indirection not
  (yet) justified for a project this size.
- **One Bukkit plugin per module:** Would give real process/classloader
  isolation, but every module would need its own database connection and
  its own update/version schema - overhead without benefit as long as all
  modules are developed in the same repository.
