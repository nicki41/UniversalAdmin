# Actions

## Purpose

An `Action<I, R>` ([`src/main/java/dev/universaladmin/action/Action.java`](../../src/main/java/dev/universaladmin/action/Action.java))
is a single business operation - "kick a player", "set a whitelist entry",
"create a backup" - callable the same way from any frontend: a GUI button, a
command, later a REST endpoint.

```java
public interface Action<I, R> {
    ActionId id();
    CompletableFuture<ActionResult<R>> execute(ActionContext context, I input);
}
```

- `I` is the input, typically a small record.
- `R` is the success result.
- `ActionContext` carries `Actor` (who) and `Source` (how/from where - see
  below). If more is needed later (locale, a request id for web tracing),
  it goes here, not into the `execute` signature.

`Action` itself contains **no** authorization, validation, or audit logic -
deliberately. Those concerns live centrally in `ActionExecutor` (see
below), not duplicated in every action implementation.

## The Pipeline: `ActionExecutor`

**No frontend calls `Action.execute(...)` directly.** The GUI, commands,
the future web API, and extensions exclusively call
`ActionExecutor.execute(...)` - the one place where authorization,
validation, the action itself, and auditing come together:

```
Frontend (GUI / Command / Web / Extension)
                ↓
        ActionExecutor.execute(ActionRequest)
                ↓
   Permission check → feature-enabled check → self-target check → validation
                ↓
           Action.execute(...)
                ↓
        AuditService.record(...)  (on success, if audited)
                ↓
           ActionResult
```

Before and after every step, the executor fires an
[`ActionEvent`](../../src/main/java/dev/universaladmin/action/ActionEvent.java)
(`Executing` → `Executed`/`Failed`) - see [Events](#events).

```java
ActionRequest<Void> request = ActionRequest.of(SomeAction.ID, actor, Source.COMMAND, null);
platform.actionExecutor().<Void, SomeResult>execute(request)
        .thenAccept(result -> /* render result */);
```

`UniversalAdminCommand#handleReload` is the reference example: it builds an
`Actor` from the Bukkit `CommandSender`, an `ActionRequest` with
`Source.COMMAND`, and afterward only renders the returned `ActionResult` -
no permission check of its own left in the command itself.

## `ActionDefinition` - What Gets Registered

Modules don't register the raw `Action`, but an `ActionDefinition<I, R>`
that wraps the action with everything the executor needs for
authorization/validation/audit:

```java
context.platform().actions().register(ActionDefinition.builder(new KickPlayerAction(service))
        .permission(PermissionNode.core("moderation.kick"))
        .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
        .forbidSelfTarget()
        .validator((ctx, input) -> input.reason().isBlank()
                ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("moderation.kick.reason-required")))
                : Optional.empty())
        .build());
```

Every field except the action itself is optional with sensible defaults (no
permission required, no target, `SelfTargetPolicy.ALLOWED`, always
enabled, always audited). `GetPlayerProfileAction`'s registration in
`PlayersModule` is the minimal example (only a permission, `.notAudited()`
because a pure read doesn't need an audit entry every time),
`ReloadConfigAction`'s registration in `UniversalAdminPlugin` the
permission-only example.

| Field | Purpose |
|---|---|
| `permission` | `PermissionNode` the `Actor` must have, or `null` |
| `validator` | synchronous `ActionValidator<I>` for cheap input checks (see below) |
| `targetExtractor` | returns a generic `ActionTarget` (type/id/display name) from the input, for the self-target check and the audit `targetId` |
| `selfTargetPolicy` | `ALLOWED` (default) or `FORBIDDEN` |
| `enabledCheck` | fine-grained "is this feature currently active", independent of the module's enabled state |
| `audited` | whether a success produces an audit entry (default `true`) |
| `auditSummary` | builds the audit summary text from the input |

## Validation

`ActionExecutor` checks, in this order, **before** the action runs:

1. **Permission** - `Actor.hasPermission(definition.permission())`.
2. **Feature enabled** - `definition.enabledCheck()`.
3. **Self-target** - only if `selfTargetPolicy() == FORBIDDEN`: the
   `ActionTarget` extracted from the input must not point at the executing
   `PLAYER` actor.
4. **Input validation** - `definition.validator()`, a synchronous
   `ActionValidator<I>` for cheap, static checks (empty string, invalid
   format).

All four failure cases are returned as a structured `ActionResult.Failure`,
never as an exception - see
[`ActionResult`](#actionresult-instead-of-exceptions).

**"Target state"** (does the target still exist, is the player online, ...)
is deliberately **not** checked generically by the executor - that needs
domain knowledge (a repository lookup) only the action itself has. Such
checks run in the action's own `execute(...)` and come back as an ordinary
`ActionResult.Failure`, just like any other business-rule error.

## `ActionResult` Instead of Exceptions

```java
sealed interface ActionResult<R> {
    record Success<R>(R value, Map<String, Object> metadata) implements ActionResult<R> {}
    record Failure<R>(FailureReason reason, String message, MessageKey messageKey,
                       List<Object> messageArgs, Map<String, Object> metadata) implements ActionResult<R> {}
}
```

`FailureReason` (`VALIDATION`, `NOT_FOUND`, `NOT_PERMITTED`, `CONFLICT`,
`FEATURE_DISABLED`, `INTERNAL_ERROR`) gives every frontend enough
information to render a meaningful error message without knowing the
action's internals. Frontends must handle the failure case - there's no
implicit "just throw an exception" path.

`messageKey`/`messageArgs` let a failure (or success, via `metadata`) be
rendered localized through `MessageService`, instead of hardcoding a raw
English string - see `GetPlayerProfileAction`, which returns
`MessageKey.of("players.not-found")` instead of a string literal. `message`
stays available for non-localized debug/log purposes. `metadata` is a
small, action-defined bag for anything else (e.g. an affected count),
without inventing a new result type for every action.

## `Actor` Instead of Bukkit `CommandSender`

`Actor`/`ActorType` (`PLAYER`, `CONSOLE`, `WEB`, `SYSTEM`) describe who is
acting, without the `action` package depending on Bukkit types -
deliberately, with the future web app in mind: a web session isn't a
`CommandSender`, but should still be able to trigger an `Action` the same
way.

Every `Actor` carries a `PermissionEvaluator` ([`dev.universaladmin.permission.PermissionEvaluator`](../../src/main/java/dev/universaladmin/permission/PermissionEvaluator.java)):

```java
public interface PermissionEvaluator {
    boolean has(PermissionNode node);
}
```

That's the centrally encapsulated "permission resolver" - code asks
`actor.hasPermission(node)`, never `player.hasPermission(...)` scattered
through GUI/command code. For a real Bukkit `Permissible`
(`Player`/`ConsoleCommandSender`),
[`PermissiblePermissionEvaluator`](../../src/main/java/dev/universaladmin/permission/bukkit/PermissiblePermissionEvaluator.java)
(in the `.bukkit` adapter subpackage, so `permission` itself stays free of
Paper imports) delivers exactly the behavior a permission plugin (LuckPerms
and similar) already provides - **wildcards work automatically as a
result**, with no wildcard logic of our own:
`PermissiblePermissionEvaluator` just delegates to
`Permissible.hasPermission`.

- `Actor.player(UUID, String, PermissionEvaluator)` - a real player.
- `Actor.console()` / `Actor.system(String)` - always authorized
  (`PermissionEvaluator.allowAll()`), since the console and internal system
  tasks are inherently trusted.
- `Actor.web(String, PermissionEvaluator)` - a placeholder for a future web
  session (see ROADMAP.md Phase 6); there's no web layer yet, but the spot
  for its own session-based `PermissionEvaluator` is already there.

## `Source` - How the Action Was Triggered

```java
public enum Source { GUI, COMMAND, WEB, API, EXTENSION, SYSTEM }
```

Deliberately separate from `Actor`: the same player can trigger actions
both via the GUI and via `/admin` in the same session - `Source` is a
property of the individual request (`ActionContext`), not of the actor
identity itself.

## `ActionRequest`

```java
public record ActionRequest<I>(ActionId id, ActionContext context, I input) {
    public static <I> ActionRequest<I> of(ActionId id, Actor actor, Source source, I input) { ... }
}
```

Bundles a single request to `ActionExecutor` - useful when a frontend wants
to build the request and only actually execute it later (e.g. after a
confirmation-dialog interaction).

## Events

`ActionExecutor` fires `ActionEvent`s (`Executing` before every check,
after that exactly one of `Executed`/`Failed`) to registered
`ActionEventListener`s. Not a Bukkit event type - deliberately, so a future
web session or extension can listen without a running Paper event bus, and
`action` stays free of Bukkit imports:

```java
platform.actionExecutor().subscribe(event -> switch (event) {
    case ActionEvent.Executing<?> e -> ...;
    case ActionEvent.Executed<?, ?> e -> ...;
    case ActionEvent.Failed<?, ?> e -> ...;
});
```

A throwing listener doesn't abort the pipeline - the executor catches and
logs it. Meant as an attachment point for a future extension API/WebSocket
live view, not a complete event system today.

## Undo (`ReversibleAction`)

```java
public interface ReversibleAction<I, R> extends Action<I, R> {
    CompletableFuture<ActionResult<Void>> undo(ActionContext context, I input, R result);
}
```

Opt-in: an action that can reverse its own effect additionally implements
this interface. `ActionExecutor.undo(id, context, input, result)` checks
the same `permission()` as the forward action and calls `undo(...)` if the
registered action is actually reversible - otherwise
`ActionResult.Failure(VALIDATION, "... is not reversible")`.

**There is no undo *history* yet** (a stack of past calls, an "undo last
action" GUI button) - that's deliberately deferred; this is only the
contract a later undo system builds on.

## Audit Hook

`ActionExecutor` gets an `AuditService` injected and automatically builds a
complete `AuditEvent` after every run
([docs/user/audit-log.md](../user/audit-log.md) for the full field list):
`type` mirrors the `ActionId` (`namespace:name`), `module`/`target`/
`source` come from `ActionDefinition`/`ActionContext`/`targetExtractor`,
`success` from the `ActionResult`. A success is always audited unless the
action set `.notAudited()` (e.g. `GetPlayerProfileAction` - a pure read
where every call would otherwise just be log noise); a failure (permission
denied, invalid input, unexpected error) is only audited if the action
explicitly requested that via `.auditFailures()` - meant for
security-relevant actions where "someone tried and was denied" is itself
worth logging.

For anything beyond the generic fields (reason, old/new value, world/
position, metadata, correlation id), an action optionally supplies an
`AuditDetails` instance via
`ActionDefinition.Builder#auditDetails(BiFunction<I, ActionResult<R>, AuditDetails>)`
- so a feature developer only fills in the fields that actually make sense
for their action, instead of hand-building a full `AuditEvent` every time:

```java
ActionDefinition.builder(new SetGamemodeAction(playerService))
        .permission(PermissionNode.core("players.gamemode"))
        .module("players")
        .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
        .auditDetails((input, result) -> AuditDetails.builder()
                .oldValue(input.previousGamemode().name())
                .newValue(input.gamemode().name())
                .build())
        .build();
```

A failed audit write doesn't abort the pipeline (only logged) - a frontend
should never fail because of an audit error.

This is deliberately the only place where `action` meets `audit` - see
[docs/user/audit-log.md](../user/audit-log.md) for the actual audit system
(repository, query service, GUI) built on this foundation.

## Where Actions Get Registered

`ActionRegistry` is a typed registry (`ActionId → ActionDefinition<?,?>`).
Modules register their actions in `onEnable`:

```java
context.platform().actions().register(ActionDefinition.builder(new GetPlayerProfileAction(playerService))
        .permission(PermissionNode.core("players.view"))
        .notAudited()
        .build());
```

A frontend builds an `ActionRequest` with the `ActionId` and calls
`platform.actionExecutor().execute(request)` - never `Action.execute(...)`
and never a concrete action class directly.

## When an Action Instead of a Direct Service Call?

Not every service call has to be an `Action`. An `Action` is worth it once
an operation needs to be triggerable from more than one kind of frontend,
or needs to be audited/authorized. Purely internal reads within a module
can go straight through the service.
