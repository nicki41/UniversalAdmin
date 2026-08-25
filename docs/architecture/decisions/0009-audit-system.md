# 0009 - Central Audit System Built on `ActionExecutor`

## Status

Accepted

## Context

[0008](0008-action-authorization-pipeline.md) already gave `ActionExecutor`
an audit *hook* (`AuditService` injection, called after successful
execution), but deliberately without building the actual audit system - the
`AuditEvent` shape at the time (`type`, `actor`, `summary`, `targetId`) was
a placeholder that carried just enough to demonstrate the hook. Without a
central, complete audit system, every module (Moderation for kick/ban,
Whitelist for changes, Settings for reload, ...) would have built its own
logging solution - exactly the pattern UniversalAdmin already avoided for
business logic (0002) and authorization (0008), just one level further down.

## Decision

`AuditEvent` is expanded to the full "audit entry" shape (actor, action
type, module, target, source, success, reason, old/new value, world/
position, summary, metadata, correlation id - see
[docs/user/audit-log.md](../user/audit-log.md)), and `ActionExecutor`
builds this entry **automatically** from
`ActionDefinition`/`ActionContext`/`ActionResult`, complemented by optional,
action-specific `AuditDetails`. A feature developer never has to fill in
more than the handful of fields that genuinely apply to their action
(typically just the old/new value) - see
[../actions.md#audit-hook](../actions.md#audit-hook).

Persistence stays on the existing `AuditEventRepository`/JDBC pattern
(0003): a new, forward-only `AuditSchemaMigrationV2` extends `audit_log`
with the new columns instead of retroactively changing version 1. Metadata
is stored as flat, hand-coded JSON (`audit.jdbc.MetadataJson`) - no new
dependency (see docs/development/architecture-rules.md's "Dependencies"
section) and explicitly no Java serialization. Queries (filtering by actor/
target/action/module/source/success/time range, pagination) run through
`AuditService#query(AuditQuery)`, implemented directly on top of the
existing `AuditService`/`AuditEventRepository` pair rather than an
additional, essentially delegating "query service" layer.

The GUI (`AuditLogListPage`/`AuditLogDetailPage`) is deliberately **not**
built on `AbstractListGuiPage` - that base class seals `renderContent`, and
the list page additionally needs a permanent filter-toggle button in the
chrome row, for which that class has no extension point. It loads the
latest 200 entries (filtered) and paginates over them client-side - the
same "load once, slice in memory" shape as every other list page in this
framework, not real server-side pagination of a potentially large table.

Retention (`audit.retention-days`, `0` = unlimited) runs via an hourly
`BukkitTask` (`AuditLogModule`) that only triggers
`AuditService#cleanupExpired()` - the actual `DELETE` work runs async
through `TaskScheduler` like any other repository call, not on every server
tick.

## Consequences

- Any action that should be audited gets that automatically, as soon as
  it's registered via `ActionDefinition` - no module writes its own audit
  code.
- `AuditEvent` is now a considerably larger record than before; the new
  `audit_log` columns are all nullable/have defaults, so rows from version 1
  stay valid without a backfill.
- `action` and `audit` remain mutually coupled (`Actor` lives in `action`,
  `ActionExecutor` knows `AuditService`/`AuditEventType`) - that was already
  accepted with 0008 and isn't reopened here.
- GUI filtering is deliberately limited to one dimension (success/failure)
  for now ("filter foundation"); the query layer already carries more, a
  richer filter UI is deferred.

## Alternatives

- **A separate audit table/service per module:** closer to "every feature
  logs itself", but exactly the pattern this decision is meant to avoid -
  no uniform query/GUI/retention layer across every module.
- **An external JSON library for metadata:** would have simplified the
  codec, but violates "no new dependency without a clear reason" for a
  requirement (a flat string/number/boolean/null object) a small
  hand-written codec fully covers.
- **Server-side pagination in the GUI:** more correct for a very large
  `audit_log` table, but a break from the "load once, paginate
  client-side" pattern every other list page in the GUI framework uses
  today - deferred until the framework itself gets an extension point for
  it.
