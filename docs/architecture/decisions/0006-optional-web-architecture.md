# 0006 - One Gradle Module Today, a Documented Split for api/sdk/web Later

## Status

Accepted

## Context

The target architecture eventually envisions `universaladmin-core`,
`universaladmin-api`, `universaladmin-sdk`, and `universaladmin-web` as
separate artifacts. But today there is no external extension and no web app
that would compile against `-api`/`-sdk`, and no web process that would need
`-web`.

## Decision

A single Gradle project (`universaladmin-core`, the repo root) for now. No
multi-project scaffold with empty modules. The future module boundary is
instead marked in code:

- The interfaces a future extension will need (`Module`, `Action`,
  `GuiPage`, `PermissionRegistry`, `AuditService`, ...) already live in
  their own, focused packages rather than being scattered - the later cut
  "these packages move to `universaladmin-api`" is thus a mechanical step,
  not a redesign.
- Storage/threading/config are already cut so a web layer could call the
  same services without changing the core (see
  [../web-future.md](../web-future.md)).

When the actual multi-project split happens is in
[ROADMAP.md](../../../ROADMAP.md) (Phase 4 for `-api`/`-sdk`, Phase 6 for
`-web`) and will be decided at the point a module actually has real content.

## Consequences

- Less Gradle overhead and configuration now.
- The split isn't "free" when it happens - it brings real work then
  (versioning, publishing, possibly separate repos for `-sdk` examples).
  That's deliberately accepted rather than front-loading that work today
  with no users yet.
- Until the split, [0005-extension-ready-design.md](0005-extension-ready-design.md)
  still applies: the code has to be written so the split stays feasible.

## Alternatives

- **Multi-project from the start** (empty `api`/`sdk`/`web` modules):
  rejected - pure structure with no content makes the repository harder to
  navigate, with nothing using it today. Can be added at any time once a
  module actually gets real code.
