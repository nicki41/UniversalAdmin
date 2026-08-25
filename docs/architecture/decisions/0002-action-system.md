# 0002 - An Action System as the Single Place for Business Logic

## Status

Accepted

## Context

The same operation ("kick a player", "set whitelist status") needs to be
triggerable from multiple frontends in the long run: the in-game GUI,
commands, later a REST API. Without an explicit abstraction for that, logic
reliably ends up directly in the GUI click handler or the command executor,
gets copied there when needed, and diverges over time (the GUI kick checks a
different condition than the command kick).

## Decision

An `Action<I, R>` interface identified by `ActionId`, async
(`CompletableFuture<ActionResult<R>>`), errors as a sealed `ActionResult`
(`Success`/`Failure` with `FailureReason`) instead of exceptions. Who
executes an action is described via `Actor`/`ActorType` (`PLAYER`,
`CONSOLE`, `WEB`, `SYSTEM`) instead of a Bukkit `CommandSender`, so `action`
doesn't depend on Paper types.

Frontends (GUI, commands, later web) call actions through `ActionRegistry`
by `ActionId`, not the concrete action class directly - that keeps coupling
loose enough that an extension can later register its own action, called by
a frontend the same way as a built-in one.

Detail and code example: [../actions.md](../actions.md).

## Consequences

- Every operation that needs to be triggerable from more than one frontend,
  or that needs to be audited/authorized, gets an `Action`. Pure internal
  reads within a module don't need to be one.
- `ActionResult` forces every caller to handle the failure case - there's no
  implicit "this won't fail" path.
- More types (input record, `ActionId` constant, action class) per
  operation than a one-line method call. Accepted as the price for reuse
  across frontends.

## Alternatives

- **Command pattern without a registry**, actions injected directly: would
  save the registry indirection, but every frontend would need to know
  every action it wants to call at compile time - incompatible with the
  goal of extensions later contributing their own actions that a generic
  frontend (e.g. a dynamic web UI) can call without knowing them in
  advance.
- **Business logic directly in services, no separate action layer:**
  sufficient for purely in-game-only logic, but loses the single entry
  point for auditing/authorization/web invocation that `Action` provides.
