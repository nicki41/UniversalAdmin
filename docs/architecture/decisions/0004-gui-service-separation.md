# 0004 - Strict Separation of GUI/Command Frontend and Application Service

## Status

Accepted

## Context

GUI click handlers and command executors are reliably where business logic
ends up in admin plugins, because it's the shortest path to a visible
result. That makes the logic neither testable (it's tied to a Bukkit
`InventoryClickEvent`) nor reusable (a future web app or a command for the
same action would have to duplicate it).

## Decision

- `GuiPage` and command `Executor` implementations may only call services or
  `Action`s. No computation, no persistence, no independent authorization
  logic (permission *checks* yes, permission *decisions with their own
  logic* no) directly in the handler.
- A `GuiPage` implementation gets its dependencies (services/actions) via
  the constructor, not through runtime access to a global `UniversalAdmin`
  object when the page opens. That forces a page to explicitly declare its
  actual dependencies and makes it testable without a running server (fake
  the service, call the click-handler method directly).
- Detail and code example: [../gui.md](../gui.md).

## Consequences

- Every new GUI feature needs (at least) a service/action underneath it,
  even when "it would be simpler to just write it here" is true. That's the
  deliberate trade-off.
- Reviews can check this rule mechanically: if `Connection`,
  `PreparedStatement`, or a multi-line computation shows up in a
  `GuiPage`/command class, that's a rule violation.
- The web app (see [../web-future.md](../web-future.md)) can later reuse the
  same services/actions, because GUI/commands never "contaminated" them
  with logic.

## Alternatives

- **Logic directly in the click handler, "it'll get refactored when
  needed":** the starting state this project is explicitly meant to avoid
  (see the project philosophy). Refactoring after the fact rarely happens
  in practice once a plugin already works.
