# Conventions

Detailed rationale is in the [development rules](architecture-rules.md) and
the [ADRs](../architecture/decisions/). Here's the short version as a
reference.

## Packages

- Root: `dev.universaladmin`.
- Architecture packages (`core`, `module`, `action`, `gui`, `command`,
  `permission`, `storage`, `audit`, `config`, `localization`,
  `notification`, `scheduler`) - platform-wide abstractions.
- Built-in modules: `dev.universaladmin.modules.<name>` (plural
  `modules`). Adapter/Bukkit-specific code in a subpackage like `jdbc`, not
  in the interface package.

## Naming

- Interfaces without a prefix/suffix: `Repository`, `Module`, `Action`,
  `SettingsService`.
- Implementations with a descriptive prefix naming the technology/context:
  `JdbcPlayerProfileRepository`, `YamlSettingsService`,
  `InGameNotificationService`, `PaperTaskScheduler`.
- Typed IDs instead of raw strings for anything that lands in a registry:
  `ModuleId`, `ActionId`, `GuiPageId`, `AuditEventType` (all via
  `dev.universaladmin.core.id.Key`, format `namespace:name`).
  `PermissionNode`/`MessageKey` are their own, simple dotted-string
  records (external conventions, see their Javadoc).
- `ModuleId.core(...)`/`ActionId.core(...)`/... as shorthand for the
  `core` namespace every built-in module uses.

## Domain Models

- `record`, not classes with setters. A state change produces a new record
  (`PlayerProfile.withLastSeen(...)`), never mutation.
- No `null` returns for "not found" - `Optional<T>` for repository lookups,
  `ActionResult.Failure` for actions.

## Error Handling

- Actions: `ActionResult<R>` (sealed `Success`/`Failure` with
  `FailureReason`), no exceptions for expected failure cases (not found,
  no permission, validation).
- Repository/storage errors: a dedicated, module-specific unchecked
  exception (`PlayerStorageException`, `AuditStorageException`) wrapping a
  `SQLException` - no raw `SQLException` passed through to a caller outside
  the `jdbc` layer.

## Formatting

- UTF-8, the `-parameters` compiler flag is on (see `build.gradle.kts`).
- No fixed auto-formatter tool set up currently; follow the existing style
  in the respective package (4-space indentation, roughly ~110 character
  line length, one import per line, no wildcard imports).
- Javadoc on public interfaces/classes explains *why*, not *what* (see
  existing classes as examples) - no Javadoc requirement for
  private/obvious methods.
