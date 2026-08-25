# Settings

How UniversalAdmin's typed configuration system works, and how a module (or,
later, an extension) registers its own settings. For the end-user-facing
reference (every current default, valid range, description), see
[docs/user/configuration.md](../user/configuration.md).

## Why not `config.getString(...)`

Scattered `FileConfiguration.getString("foo.bar")` calls have no single
place to see what keys exist, no shared validation, and no consistent
fallback behavior when a user typos a value. UniversalAdmin has exactly one
path from `config.yml` to application code: a registered
`SettingDefinition<T>`, resolved through `SettingsService`. Nothing else
reads `config.yml` directly.

## The pieces

| Type | Package | Role |
|---|---|---|
| `SettingKey<T>` | `settings` | Namespaced identifier + the literal dotted path in `config.yml` |
| `SettingType<T>` | `settings` | Parses a raw YAML value into `T` (see `SettingTypes` for the built-ins) |
| `SettingValidator<T>` | `settings` | Extra constraints beyond parsing (min/max, regex, ...) - see `SettingValidators` |
| `SettingDefinition<T>` | `settings` | Key + type + default + description + `requiresRestart` + validator |
| `SettingRegistry` | `settings` | Every registered `SettingDefinition`, keyed by config path |
| `SettingValue<T>` | `settings` | A definition paired with its current resolved value |
| `SettingsService` | `settings` | Runtime access: `get(key)`, `getValue(key)`, `reload()` |
| `YamlSettingsService` | `settings` | The (only) implementation, backed by a Bukkit `FileConfiguration` |

## `SettingKey` namespacing vs. the YAML path

```java
SettingKey<Integer> GUI_PAGE_SIZE = SettingKey.of("core", "gui.page-size");
```

Two different strings, two different jobs:

- **`configPath`** (`"gui.page-size"`) is the literal dotted path into
  `config.yml`. It must be globally unique - `SettingRegistry.register`
  throws if two definitions claim the same path, *regardless of namespace*,
  because they'd be fighting over the same line in the same file.
- **`namespace`** (`"core"`) is who owns the setting for registry
  bookkeeping - it is *not* part of the YAML path. Core platform settings
  use `"core"`; a built-in module uses its own
  `ModuleDescriptor.settingsNamespace()`; a future extension would use its
  extension id.

## Registering a setting

```java
registry.register(SettingDefinition.builder(GUI_PAGE_SIZE, SettingTypes.INTEGER, 45)
        .description("Inventory GUI page size; must be a multiple of 9 between 9 and 54.")
        .validator(SettingValidators.multipleOf(9).and(SettingValidators.intRange(9, 54)))
        .build());
```

- **Default is mandatory** and is itself validated at registration time -
  `SettingDefinition`'s constructor throws `IllegalArgumentException` if the
  default fails its own validator. A setting can never be defined in a
  state where its own default is invalid.
- **`requiresRestart(true)`** for anything that can't safely change while
  the plugin is running (database connection parameters, which built-in
  modules are registered at all). See [Reload](#reload) below.
- Built-in `SettingType`s: `STRING`, `BOOLEAN`, `INTEGER`, `LONG`,
  `DOUBLE`, `DURATION` (accepts `30s`, `5m`, `1h`, `2d`, or a bare number of
  seconds), `STRING_LIST`, and `SettingTypes.enumOf(MyEnum.class)`.
- Built-in `SettingValidator`s: `intRange`/`longRange`/`doubleRange`/`durationRange`,
  `multipleOf`, `matches(regex, description)`, `notBlank`, and `.and(...)`
  to combine two.

## A module registering its own settings

Core settings live in `CoreSettings` (see below); a module registers its
own the same way, in `onEnable`, under its own namespace:

```java
@Override
public void onEnable(ModuleContext context) {
    SettingKey<Duration> cooldown = SettingKey.of(descriptor().settingsNamespace(), "kick-cooldown");
    context.platform().settingRegistry().register(SettingDefinition.builder(cooldown, SettingTypes.DURATION, Duration.ofSeconds(30))
            .description("Minimum time between two kicks of the same player.")
            .build());
}
```

(`SettingRegistry` is reachable the same way every other shared registry is
- through `UniversalAdmin`, handed to the module via `ModuleContext`.) No
built-in module does this yet - only `CoreSettings` (the platform-level
`general`/`database`/`gui`/`audit`/`modules`/`performance`/`maintenance`/`web`
sections) is registered today. This is the pattern for when one needs to.

## Reading a setting

```java
int pageSize = context.platform().settings().get(CoreSettings.GUI_PAGE_SIZE);
```

Never fails: an absent key returns the default; a value that fails to
parse or validate is logged as a warning and *also* falls back to the
default. This is deliberate - see docs/architecture/overview.md - a typo in
`config.yml` should degrade gracefully, not take the server down.

## Reload

`SettingsService.reload()` re-reads `config.yml`, re-runs
`ConfigMigrationRunner` if needed, and re-resolves every registered
setting:

- A changed value for a setting **without** `requiresRestart` is applied
  immediately.
- A changed value for a setting **with** `requiresRestart` is reported in
  `ConfigReloadResult.pendingRestart()` and *not* applied - the old value
  stays live until the next actual restart.

`/admin reload` (see [`ReloadConfigAction`](../../src/main/java/dev/universaladmin/settings/ReloadConfigAction.java)
and [`UniversalAdminCommand`](../../src/main/java/dev/universaladmin/command/UniversalAdminCommand.java))
is the only way to trigger this - never Bukkit's global `/reload`, which
would bypass every plugin's lifecycle on the server, not just
UniversalAdmin's. The action itself runs off the main thread (see
docs/architecture/threading.md) since it's file IO, even though it isn't a
startup-blocking migration.

## Config file versioning

`config-version` at the top of `config.yml` is managed by
`dev.universaladmin.config.ConfigMigrationRunner`, not by hand. A future
schema change to `config.yml` (renaming a key, restructuring a section)
gets a new `ConfigMigration` (mirrors `storage.Migration` for the
database) instead of assuming every user's file already matches the new
shape - see [`ConfigMigration`](../../src/main/java/dev/universaladmin/config/ConfigMigration.java).
There is nothing to migrate *from* yet (version 1 is the baseline), so
none exist today; the runner still stamps `config-version: 1` into a file
that predates this system entirely (no `config-version` key at all).

## Localization

`MessageService`/`MessageKey` are unrelated to `SettingsService` in code,
but `general.language` (a `CoreSettings` entry) controls which locale
`YamlLocaleMessageService` reads from. See
[configuration.md](../user/configuration.md#localization) for the
fallback chain and how to add a new locale.
