## Summary

What does this PR do, and why?

## Changes

- 
- 

## Testing

How did you verify this works? Unit tests added/run, manual in-game steps
taken, database backend used.

## Documentation

Which docs changed, or why none were needed.

## Checklist

- [ ] `./gradlew build` passes locally (compile, tests, shaded-jar driver check).
- [ ] All tests pass; new business logic (Service/Action/non-trivial Migration) has a unit test - see [docs/development/testing.md](../docs/development/testing.md).
- [ ] No secrets, tokens, passwords, IPs, or personal data in the diff, the tests, or the PR description.
- [ ] Documentation updated in this PR where architecture, module behavior, permissions, or configuration changed (`docs/`, `README.md`, `ROADMAP.md`, `CHANGELOG.md` as applicable).
- [ ] User-facing text goes through `MessageKey`/`MessageService` and is added to **both** `lang/en_US.yml` and `lang/de_DE.yml`.
- [ ] No blocking database calls on the Paper main thread; all IO via `TaskScheduler`, all Bukkit API via `runOnMainThread` - see [docs/architecture/threading.md](../docs/architecture/threading.md).
- [ ] Architecture respected: no business logic in a GUI click handler or command executor, no SQL outside a `*Repository`/`Migration`, mutating operations run through `ActionExecutor` - see [docs/development/architecture-rules.md](../docs/development/architecture-rules.md).
- [ ] No new dependency without a stated reason above (especially no new mandatory dependency on Vault/LuckPerms/PlaceholderAPI/ProtocolLib).
- [ ] If this touches telemetry: [docs/user/telemetry.md](../docs/user/telemetry.md) updated in the same PR - nothing may be collected that isn't documented there.
