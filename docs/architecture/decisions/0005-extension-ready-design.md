# 0005 - Built-in Modules Use the Same Abstractions as Future Extensions

## Status

Accepted

## Context

UniversalAdmin should later allow community and official extensions that
can register modules, GUI pages, actions, permissions, migrations, and so
on (full list in [../extensions-future.md](../extensions-future.md)). A
public, versioned extension API is explicitly *not* built at this stage.
The risk: if built-in modules internally work differently from how a future
extension would need to work, the later API cut forces a rewrite of the
built-ins.

## Decision

Built-in modules implement exactly the `Module` interface a future external
extension would also implement - no internal "fast path" for built-ins that
depends on state only the core can see. All registries (`ActionRegistry`,
`GuiRegistry`, `PermissionRegistry`, `ServiceRegistry`, `MigrationRunner`)
are already cut so that origin (built-in vs. external) plays no role in
registration.

Namespacing (`Key`, see `dev.universaladmin.core.id.Key`) is part of every
registry id (`ModuleId`, `ActionId`, `GuiPageId`, `AuditEventType`) from the
start, so a future extension with its own namespace can never collide with
a core namespace (`core:*`).

## Consequences

- Any new capability built "for built-ins only" is a violation of this ADR,
  unless it's explicitly documented as a temporary limitation (e.g. "there
  is no extension loader yet" is fine, "built-ins may do things an
  extension technically couldn't" is not).
- The later "extract the API" step (see
  [0006-optional-web-architecture.md](0006-optional-web-architecture.md))
  becomes a module split with a thin versioning layer on top, rather than a
  redesign of the extension points.
- There is still no real backward-compatibility guarantee today - internal
  interfaces can still change before the API cut. This ADR governs the
  *shape* of the abstractions, not their *stability*.

## Alternatives

- **Build fast internally first, add the API layer separately later:** the
  exact risk this ADR is meant to avoid - "add it separately later" turns
  into a rewrite in practice, once built-ins depend on internal state
  instead of registered interfaces.
