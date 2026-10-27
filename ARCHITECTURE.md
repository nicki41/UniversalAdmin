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
way. UniversalAdmin ships ten built-in modules (Players, Moderation,
Reports, Chat, Server, Worlds, Whitelist, Performance, Audit Log, Settings)
that all implement this exact interface - the same one extensions
implement.

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

## Frontends

The in-game GUI (`dev.universaladmin.gui`) and `/admin` commands
(`dev.universaladmin.command`, [ADR 0011](docs/architecture/decisions/0011-command-framework.md))
are both thin frontends over the same actions. Modules contribute main-menu
entries (`ModuleDescriptor#menu`), pages, profile buttons
(`PlayerMenuRegistry`) and subcommands through registries - nothing in the
core knows a module by name. All texts come from `lang/<locale>.yml`; message
arguments are escaped ([ADR 0012](docs/architecture/decisions/0012-safe-messages.md)).

## Network mode

Servers behind a Velocity/BungeeCord proxy that share one MySQL database
see each other through `NetworkService` (`dev.universaladmin.network`):
heartbeats, online players and messages between servers live in three
tables that every server polls; moving players goes through the proxy's
BungeeCord plugin messaging channel. Cross-server operations are ordinary
actions of the `network` module. Without network mode the service is a
no-op. Maintenance and the whitelist exist per server or for the whole
network, keyed by scope in the shared tables, and vanished players are left
out of what the network shows. The optional proxy plugins (`proxy/velocity`,
`proxy/bungee`, both on `proxy/common`) refuse
banned players - and everyone without access during network maintenance or
while the network whitelist is on - at the proxy
([ADR 0021](docs/architecture/decisions/0021-bungeecord-proxy-plugin.md)). See
[docs/user/network.md](docs/user/network.md),
[ADR 0013](docs/architecture/decisions/0013-network-mode.md),
[ADR 0017](docs/architecture/decisions/0017-maintenance-scopes.md) and
[ADR 0019](docs/architecture/decisions/0019-network-whitelist-and-vanish.md).

## Extensions

Extensions are ordinary Paper plugins (`depend: [UniversalAdmin]`) that
register `Module`s through `UniversalAdminProvider.get().extensions()`. They
compile against the public API project `universaladmin-api` and get exactly
the extension points the built-in modules use. See
[docs/architecture/extensions.md](docs/architecture/extensions.md),
[ADR 0010](docs/architecture/decisions/0010-extension-api.md) and the
[developer guide](docs/extensions/getting-started.md). A web app/REST API is
still planned - [docs/architecture/web-future.md](docs/architecture/web-future.md) -
and so is a drag-and-drop extension builder in the browser with a place to
share extensions - [docs/architecture/extension-builder.md](docs/architecture/extension-builder.md).

## Gradle layout

| Project | Directory | Contents |
|---|---|---|
| `universaladmin-api` | `api/` | Public, semantically versioned extension API |
| `universaladmin-core` | `/` | The plugin: bootstrap, implementations, built-in modules; shades the API |
| `example-extension` | `examples/example-extension/` | Template extension, built in CI only |
| `universaladmin-proxy-common` | `proxy/common/` | The checks both proxy plugins share (bans, network maintenance, network whitelist) |
| `universaladmin-velocity` | `proxy/velocity/` | Optional Velocity plugin for network mode (own jar) |
| `universaladmin-bungee` | `proxy/bungee/` | Optional BungeeCord plugin for network mode (own jar) |

The official extensions have repositories of their own in the
[universaladmin-mc](https://github.com/universaladmin-mc) organization -
[Discord](https://github.com/universaladmin-mc/universaladmin-discord),
[Placeholders](https://github.com/universaladmin-mc/universaladmin-placeholders) and
[Voice Chat](https://github.com/universaladmin-mc/universaladmin-voicechat) - built against the published
API like any third-party extension
([ADR 0024](docs/architecture/decisions/0024-updates-and-repositories.md)).

Every jar is Java 21 bytecode with `api-version: '1.21.7'` and runs on
every version in [supported-versions.txt](supported-versions.txt) - see
[ADR 0014](docs/architecture/decisions/0014-version-compatibility.md).
