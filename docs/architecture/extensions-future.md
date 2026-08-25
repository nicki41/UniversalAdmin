# Extensions (Future)

This document describes a **planned**, not-yet-built capability. There is
currently no public API, no extension loader, and no separation between the
core and a `universaladmin-api` module. What does exist: abstractions
designed so they can later become externally usable without a rewrite. See
[decisions/0005-extension-ready-design.md](decisions/0005-extension-ready-design.md).

## What an Extension Should Later Be Able to Register

From the project brief, as a checklist, with the mechanism that already
exists for it:

| Extension point | Today's mechanism |
|---|---|
| Modules | `Module` interface, `ModuleManager` |
| GUI pages | `GuiPage` interface, `GuiRegistry` |
| Main menu entries | no main menu built yet (Phase 1) |
| Player actions | `Action<I, R>`, `ActionRegistry` |
| Player profile sections | no profile UI built yet (Phase 1) |
| Admin actions | `Action<I, R>`, `ActionRegistry` (no difference from "player actions" in the type system - the difference is who is allowed to call them, via permissions) |
| Settings | `SettingRegistry`/`SettingsService` (see [docs/development/settings.md](../development/settings.md)) - namespacing for core/module/future extension already exists, but currently only `CoreSettings` registers anything |
| Permissions | `PermissionNode`/`PermissionDefinition`, `PermissionRegistry` |
| Events | there is no UniversalAdmin-specific event system yet (currently only Bukkit events, consumed internally by modules) |
| Audit events | `AuditEventType`, `AuditService` |
| Storage/migrations | `Migration`, `MigrationRunner` |
| Dashboard widgets | only exists once the web app exists, see [web-future.md](web-future.md) |
| Web pages | only exists once the web app exists |
| Web API hooks | only exists once the web app/REST API exists |
| Notifications | `NotificationService` (currently only an in-game channel) |

Rows without a mechanism today aren't a contradiction of the
"extension-ready" premise - they simply don't exist as a feature for
anyone yet (not even for built-ins). Once they're built (see
[ROADMAP.md](../../ROADMAP.md)), they follow the same pattern: an
interface plus a registry, no built-in-only shortcut.

## What's Missing Before Extensions Become Real

- **A stable, versioned API boundary.** Currently any internal class can
  change at any time. An extension API needs backward-compatibility
  guarantees the core doesn't need internally.
- **An extension loader.** Open: separate jars in a
  `plugins/UniversalAdmin/extensions/` folder (loaded by UniversalAdmin
  itself) vs. standalone Bukkit plugins with `depend: [UniversalAdmin]`
  (loaded by Paper, UniversalAdmin only as a dependency). Both paths are
  compatible with the current `Module` interface; the decision only
  affects *who* calls `ModuleManager.enable(...)` for an extension.
- **Sandboxing/trust.** An extension runs in the same JVM process as the
  core - there's no isolation. That's normal for a server plugin (same as
  for any other Bukkit plugin), but should be stated explicitly in the
  extension documentation once it's written.
- **`universaladmin-sdk`.** Example extension plus documentation, so third
  parties don't have to guess what a "correct" `Module` looks like.

## What Already Applies Now

Don't build behavior an existing built-in module can do that a
(hypothetical) extension couldn't, because it depends on internal state
instead of a registered interface. That's the one rule that has to be
enforced *now already*, so the later API cut isn't a rewrite.
