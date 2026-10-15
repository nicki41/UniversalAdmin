# Extensions

Since 1.0, UniversalAdmin has a public, versioned extension API. An
extension is an ordinary Paper plugin that declares
`depend: [UniversalAdmin]` and registers one or more `Module`s - the exact
interface the built-in modules implement. Decision and rationale:
[ADR 0010](decisions/0010-extension-api.md). How to write one:
[docs/extensions/getting-started.md](../extensions/getting-started.md).

## The API boundary

The API is the Gradle project `universaladmin-api` (`api/`). It contains the
interfaces and framework classes an extension compiles against; the core
(`universaladmin-core`, the repository root) contains the implementations
and the built-in modules. Package names are shared - the split is a jar
boundary, not a rename - and the plugin jar ships every API class
unrelocated.

**From 1.0.0 on, everything in `universaladmin-api` follows semantic
versioning**: no breaking change without a major version. Classes that are
only in the core jar (`bootstrap`, `modules.*`, `*.jdbc`, the `Yaml*`
implementations, ...) are internal and may change in any release.
`UniversalAdminProvider.API_VERSION` exposes the API level at runtime.

## What an extension can contribute

| Extension point | API | Registered via `ModuleContext` |
|---|---|---|
| Modules (lifecycle, dependencies) | `Module`, `ModuleDescriptor` | `extensions().register(plugin, module)` |
| Main-menu entries | `ModuleDescriptor.Builder#menu(page, permission)` | descriptor |
| GUI pages | `AbstractGuiPage`, `AbstractListGuiPage`, dialogs, `GuiTextInput` | `registerPage` |
| Player-profile buttons | `PlayerMenuEntry` | `registerPlayerMenuEntry` |
| `/admin` subcommands (+ shortcuts) | `AdminCommand`, `BaseAdminCommand`, `CommandInvocation` | `registerCommand` |
| Actions (authorized, validated, audited) | `Action`, `ActionDefinition`, `ActionExecutor` | `registerAction` |
| Permissions | `PermissionDefinition` | `registerPermission` |
| Settings (shown in the settings GUI) | `SettingDefinition`, `SettingTypes`, `SettingValidators` | `registerSetting` |
| Translations | `MessageBundles`, `MessageService#registerDefaults` | - |
| Database tables | `Migration`, `StorageService`, `Repository`, `Transactions` | `registerMigration` (in `onLoad`) |
| Shared services | `ServiceRegistry` | `registerService` |
| Bukkit listeners | - | `registerListener` |
| Observing every action | `ActionEventListener` | `subscribe` |
| Observing every audit entry | `AuditListener` | `subscribeAudit` |
| Notifications | `NotificationService` | - |
| Threading | `TaskScheduler` (`supplyAsync`, `runOnMainThread`, `supplyOnMainThread`) | - |

Every `register*` call undoes itself when the module is disabled - which
happens automatically when the owning plugin is disabled.

## Rules that keep extensions safe

- **Own namespace.** Extension ids use their own namespace
  (`myextension:tickets`); `core` is reserved and refused by the loader.
  Permission nodes, lang keys and table names should carry the same prefix.
- **Same pipeline.** An extension's action runs through the same
  `ActionExecutor` as a built-in one: permission check, validation, audit.
  There is no shortcut for built-ins an extension could not use
  ([ADR 0005](decisions/0005-extension-ready-design.md)).
- **Same process.** An extension runs in the server's JVM like any plugin;
  there is no sandbox. Install extensions you trust, like any plugin.
- **Main thread for registration.** `ExtensionManager#register` must be
  called on the main thread after UniversalAdmin enabled (from the
  extension's `onEnable`). Its migrations run synchronously right then.

## Distribution

Extensions are distributed like any plugin (Modrinth, Hangar, GitHub
releases) and installed by dropping the jar into `plugins/`. A curated list
of extensions is planned - see [ROADMAP.md](../../ROADMAP.md).

## Official extensions

- **UniversalAdmin Discord** (`extensions/discord`) - posts selected audit
  events to a Discord channel via a webhook. User docs:
  [docs/extensions/discord.md](../extensions/discord.md).
- **Example extension** (`examples/example-extension`) - not for production;
  a complete, commented template that uses every extension point above.
