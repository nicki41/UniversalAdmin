# 0008 - Central Authorization/Validation via `ActionExecutor`

## Status

Accepted

## Context

[0002](0002-action-system.md) established `Action<I, R>` as the only place
for business logic, but not *who* is allowed to run an action or how
inputs/targets are checked before the actual call. In practice that landed
wherever was historically most convenient:
`UniversalAdminCommand#handleReload` checked
`sender.hasPermission("universaladmin.reload")` as a raw string literal,
completely separate from the `PermissionDefinition` registered through
`PermissionRegistry` with the same node - and `GetPlayerProfileAction` had
no permission check at all despite a registered
`universaladmin.players.view` permission. With more frontends (web API,
extensions), this pattern would have multiplied, with diverging checks per
frontend - exactly the problem 0002 had already solved for the business
logic itself, just one level up.

## Decision

An `ActionExecutor` is the only place a frontend runs an `Action` - never
`Action.execute(...)` directly. Modules no longer register the raw `Action`,
but an `ActionDefinition<I, R>` (permission, validator, self-target policy,
feature-enabled check, audit configuration). The executor checks permission
→ feature-enabled → self-target → input validation before the action even
runs, calls `AuditService.record(...)` on success, and fires `ActionEvent`s
(`Executing`/`Executed`/`Failed`) for anything that wants to observe the
pipeline (a future extension API/WebSocket).

Authorization itself runs through an `Actor`-carried `PermissionEvaluator`
instead of scattered `Permissible.hasPermission(...)` calls - a Bukkit
`Permissible` is adapted via
`dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator`
(analogous to the `storage`/`storage.jdbc` pattern), so `permission` and
`action` themselves stay free of Paper imports. Detail and code examples:
[../actions.md](../actions.md).

## Consequences

- Every authorization/validation rule for an action lives in exactly one
  place (`ActionDefinition`), not duplicated per frontend.
  `UniversalAdminCommand#handleReload` was reworked accordingly: the
  command no longer checks a permission itself, it only renders the
  `ActionResult` the executor returns.
- A frontend that calls `Action.execute(...)` directly instead of going
  through `ActionExecutor` completely bypasses authorization/validation/
  audit - the one thing docs/architecture/actions.md explicitly flags as
  "doesn't follow the pattern".
- `ActionResult` gains `messageKey`/`messageArgs`/`metadata` in addition to
  the existing `message`, so errors from the pipeline (missing permission,
  disabled feature, self-target) can be rendered localized the same way as
  errors from the action itself.
- Undo preparation (`ReversibleAction`) and the audit hook are deliberately
  only the contract/attachment point, not the complete undo or audit
  system - see [0009](0009-audit-system.md) for the latter.

## Alternatives

- **Keep the permission check per frontend, only centralize business logic
  (the status quo before this decision):** exactly the pattern that already
  led the `/admin reload` command to a raw string permission literal that
  bypassed the registered `PermissionDefinition` - no structural protection
  against the same thing for future actions/frontends.
- **Authorization as part of `Action#execute` itself (each action checks
  its own permission):** would save `ActionDefinition`, but every action
  would have to write its own permission check instead of registering it
  declaratively - and a frontend could no longer generically ask an action
  (without knowing it) "do I need a permission for this", which would take
  away a future dynamic web UI's ability to hide buttons/actions ahead of
  time.
