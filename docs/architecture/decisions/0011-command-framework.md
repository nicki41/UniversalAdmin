# 0011 - One /admin Dispatcher, Contributed Subcommands, Optional Shortcuts

## Status

Accepted (1.0.0)

## Context

Until 1.0 the root command knew a handful of subcommands by name and had to
mirror action ids of modules it was not allowed to import. Every module
needed commands, and so would every extension. Admins also expect the short
forms they know from other plugins (`/kick`, `/ban`, `/vanish`).

## Decision

- `AdminCommand` (API) is one `/admin` subcommand; modules and extensions
  register them in `CommandRegistry` through `ModuleContext#registerCommand`.
  The root dispatcher (`UniversalAdminCommand`) knows no command by name;
  `/admin help` and tab completion are generated from the registry and
  filtered by permission.
- A command is a frontend: it parses arguments and runs an action through
  `CommandInvocation#run`, which also renders failures uniformly. The
  action re-checks permissions; the command's permission only controls
  visibility.
- `AdminCommand#shortcuts()` declares top-level labels. They are registered
  through Paper's `COMMANDS` lifecycle event as `BasicCommand`s delegating
  to the same dispatcher, controlled by `commands.shortcuts.enabled` and
  `commands.shortcuts.disabled`. Player-facing commands (`/report`,
  `/staffchat`) set `alwaysRegisterShortcuts`.
- Usage synopses are plain text passed as (escaped) message arguments, so
  `<player>` is never mistaken for a MiniMessage tag.

## Consequences

- Extensions get first-class commands, help entries and shortcuts with no
  extra code in the core.
- Shortcut clashes with other plugins are solvable by configuration;
  `/universaladmin:<label>` always reaches ours.
- `plugin.yml` only declares `/admin`; everything else is dynamic.

## Alternatives

- **Brigadier trees for every command.** Richer client-side validation, but
  far more code per command and an API that changes with Minecraft
  versions; the string-based `AdminCommand` keeps extension code simple.
- **Static shortcut list in `plugin.yml`.** Cannot be switched off or extended
  by extensions.
