# 0010 - A Separate API Jar, and Extensions as Ordinary Plugins

## Status

Accepted (1.0.0). Supersedes the "not yet" parts of
[0006](0006-optional-web-architecture.md) for `universaladmin-api`.

## Context

[0005](0005-extension-ready-design.md) kept every extension point in shape
for external use; [0006](0006-optional-web-architecture.md) deferred the
actual `-api` split until there was real content for it. With 1.0 the
platform is stable enough to promise compatibility, and two real consumers
exist: the example extension and the official Discord extension. Two
questions had to be answered: where the API boundary is, and how an
extension gets loaded.

## Decision

**API boundary.** A second Gradle project, `universaladmin-api` (`api/`),
holds the public surface: the module system, actions, permissions,
commands, the GUI framework, settings, storage interfaces, audit,
localization, notifications, the scheduler, and the extension manager.
Package names stay the same as in the core - the cut is a jar boundary, so
moving classes needed no import changes. The plugin jar shades the API
unrelocated (extensions reference these exact names) and never minimizes
it. From 1.0.0 the API follows semantic versioning.

`UniversalAdmin` became an interface (implemented by `UniversalAdminPlatform`
in the core), `StorageService` an interface (`JdbcStorageService`), so the
API carries no HikariCP or implementation types.

**Loading.** Extensions are ordinary Paper plugins with
`depend: [UniversalAdmin]`. In their `onEnable` they call
`UniversalAdminProvider.get().extensions().register(this, module)`. The
`ExtensionManager` loads the module, applies its migrations, enables it,
and syncs its permissions to Bukkit; on `PluginDisableEvent` for the owner
it disables the module, and `ModuleResources` removes every command, page,
action, permission, setting, listener and subscription the module
registered through its `ModuleContext`.

## Consequences

- Extensions are distributed and installed like any plugin; Paper resolves
  load order, versions are visible in `/plugins`, Modrinth/Hangar work as-is.
- The `core` namespace is reserved for built-ins; the loader rejects it.
- Migrations of an extension run synchronously when it enables - during
  server start that is before players join; a hot-loaded extension blocks
  briefly. Documented, acceptable.
- Split packages across two jars rely on both being loaded by the same
  class loader, which the shaded plugin jar guarantees.
- Every change to `api/` is now an API change and needs the corresponding
  care (deprecate first, remove in the next major).

## Alternatives

- **Jars in `plugins/UniversalAdmin/extensions/`, loaded by our own class
  loader.** Rejected: re-implements what Paper already does (dependency
  ordering, class loading, metadata), and breaks familiar tooling and
  distribution channels.
- **Compile against the full plugin jar only.** Kept as a fallback
  (Modrinth Maven), but without an API jar there is no visible boundary of
  what is stable.
- **Renaming API packages to `dev.universaladmin.api.*`.** Rejected: a large
  churn for no runtime benefit, and every existing link in the docs would
  have changed.
