# Release Readiness - 1.0.0

Status of the 1.0.0 release candidate. What has to happen before tagging is
in the [test plan](docs/release/test-plan.md) and in "Before tagging" below.

## Overview

| Item | Status |
|---|---|
| **Version** | `1.0.0` (`build.gradle.kts`; `./gradlew -q printVersion`) |
| **Build** | `./gradlew clean build` green - all projects, including `verifyShadedJarDrivers` (real SQLite connection through the finished jar) |
| **Tests** | unit and integration tests for services, actions, migrations (real SQLite), settings, localization, commands' building blocks, the Discord extension |
| **Supported server** | Paper 26.2, Java 25 - see [installation.md](docs/user/installation.md#supported-versions) |
| **Modules** | Players, Moderation (+ staff tools, notes), Reports, Chat, Server, Worlds, Whitelist, Performance, Audit Log, Settings |
| **Commands** | every module, `/admin help`, shortcuts - [commands.md](docs/user/commands.md) |
| **Extension API** | `universaladmin-api` 1.0, semantically versioned - [ADR 0010](docs/architecture/decisions/0010-extension-api.md) |
| **Official extensions** | UniversalAdmin Discord |
| **Release pipeline** | tag → GitHub release (plugin, Discord extension, API jars, checksums) + Modrinth version; pre-releases flagged from 1.0 on - [releasing.md](docs/release/releasing.md) |
| **Telemetry** | on by default, fully documented, single switch `telemetry.enabled` - [telemetry.md](docs/user/telemetry.md) |
| **Upgrade path** | 0.1.0-alpha → 1.0.0 automatic: config migration v2, new tables, layered language files - see the changelog |

## Verified by automated tests

- Message escaping (no MiniMessage injection through arguments), layered
  language files, broken language files do not stop startup, de_DE has every
  en_US key, no `MessageFormat` apostrophe traps, every key used in code
  exists.
- Settings: lazy resolution of module settings, in-game set/reset with
  validation, restart-required handling, config migration v2.
- Every migration together against a fresh and an upgraded SQLite database.
- Reports rules (self-report, cooldown, open limit, claim/close lifecycle),
  chat rules (lock, slow mode, staff chat toggle), SemVer ordering for the
  update check, click throttle, Discord payload escaping/mentions/URL check.

## Not verifiable without a running server

These need the manual pass in the [test plan](docs/release/test-plan.md):

- Every GUI page and dialog, especially navigation back/forward through
  confirmation dialogs and text prompts (Paper dialog API).
- Shortcut registration through Paper's command lifecycle - including
  clashes with other plugins (EssentialsX) and tab completion of shortcuts.
- Extension loading/unloading with the Discord and example extensions;
  permission sync into LuckPerms.
- Chat interplay with chat-formatting plugins.
- MySQL/MariaDB (all tests use SQLite).
- Real Discord webhook delivery and rate limiting.

## Known limitations

- Shortcuts are registered once at startup; `commands.shortcuts.*` needs a
  restart.
- Chat lock and slow mode are not persisted across restarts (by design).
- Report cooldowns are in memory and reset on restart.
- Existing tables from the alpha (`punishments`, `player_profiles`, ...) keep
  their unprefixed names; only new tables use `ua_`.
- `/admin whitelist add` resolves unknown names through Mojang; offline-mode
  servers should whitelist players after their first join.
- No import from other ban plugins yet ([roadmap](ROADMAP.md)).

## Before tagging v1.0.0

1. Run the [test plan](docs/release/test-plan.md) on a real Paper 26.2 server.
2. Update the telemetry text on the live Modrinth page (it still says
   "nothing is sent" from an early alpha) - new text in
   [modrinth.md](docs/release/modrinth.md#description).
3. Capture the screenshots listed in [modrinth.md](docs/release/modrinth.md#gallery).
4. Set the release date in the `## [1.0.0]` heading of `CHANGELOG.md`.
5. Merge, tag `v1.0.0`, push the tag - [releasing.md](docs/release/releasing.md).
