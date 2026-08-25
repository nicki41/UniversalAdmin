# Architecture

This is the short version. For the full walkthrough of every package see
[docs/architecture/overview.md](docs/architecture/overview.md); for *why*
specific choices were made, see the [ADRs](docs/architecture/decisions/).

## Layers

```
Frontend            GUI pages, commands, (later) the web app and REST API
    ↓
Application Services Orchestrate one or more repositories/actions for a use case
    ↓
Actions / Domain     The actual business logic, invokable by any frontend
    ↓
Repositories          Persistence, one interface per aggregate, no SQL leaks up
    ↓
Paper / Database      Bukkit API and JDBC, the only places allowed to touch them directly
```

A frontend never contains business logic - it collects input, calls a
service or an `Action`, and renders the result. This is what lets the same
"kick a player" logic be triggered by a GUI button, a command, and later a
REST endpoint without duplicating it three times. See
[docs/architecture/actions.md](docs/architecture/actions.md) and
[docs/architecture/gui.md](docs/architecture/gui.md).

## Composition, not a framework

There is no dependency-injection framework. `UniversalAdminPlugin#onEnable`
(the [bootstrap](src/main/java/dev/universaladmin/bootstrap) layer) builds
every *critical* service by hand in a dedicated `bootstrapCore` step and
wires it into one `UniversalAdmin` instance (the composition root, see
[src/main/java/dev/universaladmin/core/UniversalAdmin.java](src/main/java/dev/universaladmin/core/UniversalAdmin.java)),
which is handed to modules via `ModuleContext`. Everything below that is
plain constructor injection. If `bootstrapCore` itself throws, the whole
plugin fails to start - see [Modules](#modules) for why that's different
from a single module failing. See
[docs/architecture/decisions/0001-modular-core.md](docs/architecture/decisions/0001-modular-core.md).

## Modules

A [`Module`](src/main/java/dev/universaladmin/module/Module.java) is a
self-contained unit of functionality that registers actions, GUI pages,
permissions, and migrations into shared registries, then gets out of the
way. UniversalAdmin ships eight built-in modules (Players, Moderation,
Server, Worlds, Whitelist, Performance, Audit Log, Settings) that all
implement this exact interface - the same one a future external extension
will implement.

`ModuleManager` drives every module through `DISCOVERED → LOADED → ENABLED`
(and back to `DISABLED`) in dependency order (`ModuleDescriptor.dependencies()`),
tracked by `ModuleRegistry`. A module is never a critical component: if one
throws while loading/enabling, only that module is marked `FAILED` (logged
in full) and the rest of the server keeps starting - critical failures
(config, storage, the shared registries themselves) abort the whole plugin
instead, in `bootstrapCore` above. Listeners/tasks/registry entries a
module registers during `onEnable` are released automatically on disable
via `ModuleResources`. See [docs/architecture/modules.md](docs/architecture/modules.md)
(the full lifecycle, failure-isolation, and dependency-ordering rules) and
[docs/architecture/decisions/0005-extension-ready-design.md](docs/architecture/decisions/0005-extension-ready-design.md).

## Storage

Repository pattern over JDBC. SQLite is the default (zero setup, one file
in the plugin's data folder); MySQL/MariaDB is opt-in via `config.yml`. No
service or action ever imports `java.sql.*` directly - only
`*Repository` implementations do. Schema changes are versioned
`Migration`s applied once at startup. See
[docs/architecture/storage.md](docs/architecture/storage.md) and
[docs/architecture/decisions/0003-repository-storage.md](docs/architecture/decisions/0003-repository-storage.md).

## Threading

Database and other blocking IO run on a virtual-thread executor
(`TaskScheduler`); anything touching Bukkit API hops back to the main thread
through the same scheduler. See
[docs/architecture/threading.md](docs/architecture/threading.md).

## What's prepared but not built yet

The web app, the REST API, WebSockets, and the public extension API/SDK do
not exist yet - by design, per the current step's scope. What *does* exist
is the seam they will attach to without a rewrite: `Action`, `GuiPage`,
`Module`, `PermissionRegistry`, and `AuditService` are already the shape an
extension or a web endpoint would use. See
[docs/architecture/extensions-future.md](docs/architecture/extensions-future.md)
and [docs/architecture/web-future.md](docs/architecture/web-future.md).

## Gradle layout

Single Gradle project (`universaladmin-core`) today. A split into
`universaladmin-api` / `-sdk` / `-web` is documented, not built - see
[docs/architecture/decisions/0006-optional-web-architecture.md](docs/architecture/decisions/0006-optional-web-architecture.md).
