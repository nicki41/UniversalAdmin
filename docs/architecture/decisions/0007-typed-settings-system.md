# 0007 - A Typed Settings System Instead of Scattered `config.getString(...)` Calls

## Status

Accepted

## Context

`config.yml` was always meant to carry more than the handful of values
`YamlConfigService` originally read directly from `FileConfiguration`
(database, locale). Adding GUI, audit, module-toggle, performance, and
maintenance settings on top grows the number of config values considerably
- and with it the risk that every spot in the code brings its own
`config.getString("something")` call with its own default and its own (or
missing) validation. That's exactly the pattern the
[development rules](../../development/architecture-rules.md) explicitly
mean to avoid.

At the same time, the system should later be usable by modules and
extensions too (see
[decisions/0005-extension-ready-design.md](0005-extension-ready-design.md))
- not just the core.

## Decision

- **One** access path from `config.yml` to application code: a registered
  `SettingDefinition<T>` (key, type, default, description,
  `requiresRestart` flag, validator), resolved via `SettingsService.get(key)`.
  No code outside `dev.universaladmin.settings.YamlSettingsService` reads
  `config.yml` directly.
- `SettingKey<T>` deliberately separates two strings: the `configPath` (the
  literal YAML path, e.g. `gui.page-size` - globally unique across every
  namespace, because it's the same line in the same file) and the
  `namespace` (who owns the setting - `core`, a module via
  `ModuleDescriptor.settingsNamespace()`, later an extension id). See
  [../modules.md](../modules.md) for the connection to `ModuleDescriptor`.
- Parsing (`SettingType<T>`) and validation (`SettingValidator<T>`) are
  separate, combinable building blocks rather than one monolithic parse
  function per setting.
- **An invalid value never crashes the server.** `YamlSettingsService` falls
  back to the registered default on a parse or validation error and logs a
  clear warning - both at the initial start and on `/admin reload`.
- `config-version` plus `ConfigMigrationRunner` (in the now-leaner `config`
  package) versions the file itself, analogous to `storage.Migration` for
  the database schema - an existing user's config is never silently
  overwritten on an update.

Detail: [docs/development/settings.md](../../development/settings.md).

## Consequences

- New config values need more ceremony than a one-line `getString(...)`
  call (a `SettingKey` constant, a `SettingDefinition` registration).
  Accepted as the price for central type safety, validation, and a uniform
  reload mechanism.
- Every setting explicitly declares whether it's live-changeable
  (`requiresRestart`). That forces a deliberate decision per value instead
  of a blanket "reload just refreshes everything" - see
  `ReloadConfigAction`.
- The `config` and `settings` packages are now clearly separated: `config`
  only knows about file versioning, `settings` owns the actual type
  system. That's a visible shift from the access originally centralized in
  `config.ConfigService`.

## Alternatives

- **A generic `Map<String, Object>`-based config object** without
  registration: saves the definition ceremony, but loses the central place
  where every setting (with description, default, restart flag) is visible
  for docs/GUI/extensions - exactly what `SettingRegistry` provides.
- **Adopt an existing config framework (e.g. Configurate):** would have
  brought similar type safety, but an additional dependency for a problem
  solvable with the already-present Bukkit `FileConfiguration` and a thin
  layer of our own - see "no new dependencies without a clear reason" in
  the [development rules](../../development/architecture-rules.md).
