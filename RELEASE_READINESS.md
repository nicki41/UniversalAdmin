# Release Readiness

Project status with respect to publication. Updated as part of making the
repository public (Apache-2.0, CI, automated releases, anonymous usage
statistics).

## Overview

| Item | Status |
|---|---|
| **Current version** | `0.1.0-alpha` (`build.gradle.kts`, single source of truth; `./gradlew -q printVersion`) |
| **Build** | green - `./gradlew clean build` including `verifyShadedJarDrivers` (opens a real SQLite connection through the finished jar) |
| **Tests** | green - 245 tests across 51 test classes, 0 failures, 0 errors, 0 skipped |
| **GitHub** | public repository, default branch `main`, issues enabled, issue/PR templates present |
| **License** | Apache-2.0, `LICENSE` with the unmodified official text; rationale in [docs/release/licensing.md](docs/release/licensing.md) |
| **CI** | `.github/workflows/build.yml` - build and tests on every push/PR against `main`, jar as an Actions artifact, `contents: read` |
| **Automatic releases** | `.github/workflows/release.yml` - a `v*` tag (pushed manually, or by `auto-release.yml` after a notable push to `main`) triggers a tag/version check, build, tests, a GitHub release with the jar and its SHA-256, `contents: write`, only `GITHUB_TOKEN`. Never flagged "Pre-release" - see [docs/release/releasing.md](docs/release/releasing.md#version-scheme). |
| **Telemetry implementation** | fully implemented and tested (`dev.universaladmin.telemetry`), documented in [docs/user/telemetry.md](docs/user/telemetry.md) |
| **Telemetry endpoint** | `telemetry.endpoint` defaults to `https://telemetry.0nicki.de/v1/telemetry` (the official `nicki41-telemetry` instance) - a fresh install reports by default; `telemetry.enabled: false` or an empty `telemetry.endpoint` turns it off. |
| **Modrinth** | not uploaded; project page and checklist prepared in [docs/release/modrinth.md](docs/release/modrinth.md) |
| **Extension API** | not implemented - next major milestone ([ROADMAP.md](ROADMAP.md) Phase 4) |
| **Dependabot** | deliberately **not** set up; dependencies are updated manually |

## What Was Added in This Step

- **Apache-2.0 licensing.** `LICENSE` with the unmodified official license
  text (no invented copyright holder in the appendix placeholder), the
  decision and its rationale, and its implications for the extension API,
  community extensions, and a possible marketplace backend in
  [docs/release/licensing.md](docs/release/licensing.md).
- **Anonymous usage statistics.** Installation id (128 bits from
  `SecureRandom`, derived from nothing), a payload with exactly six fields,
  an HTTP client with short timeouts, a scheduler with a delayed start and
  jitter, full opt-out without a restart. No field is collected that isn't
  in [docs/user/telemetry.md](docs/user/telemetry.md) - a test enforces
  that.
- **Automated releases.** Tag-driven, with a check that the tag matches the
  project version before anything is built or published.
- **Public developer documentation.** The binding project rules now live as
  regular documentation under
  [docs/development/architecture-rules.md](docs/development/architecture-rules.md);
  all references in source code and docs point there.
- **README/CONTRIBUTING/SECURITY** brought to a public-facing state,
  including private vulnerability reporting and the telemetry disclosure.

## Telemetry in Detail

| Question | Answer |
|---|---|
| Is anything sent? | No. No endpoint configured, no built-in fallback. |
| Is an id generated? | Only if telemetry is active **and** an endpoint is configured. Otherwise not even a file is created. |
| Which fields? | `installationId`, `universalAdminVersion`, `minecraftVersion`, `javaMajorVersion`, `onlinePlayers`, `maxPlayers`. Nothing more. |
| Player-related data? | None. Just two numbers. No names, UUIDs, IPs. |
| Hardware fingerprint? | No. Pure randomness. |
| Opt-out? | `telemetry.enabled: false`, effective after `/admin reload`, no restart. |
| Impact of a backend outage? | None. No retry, no queue, a single warning per server run. |
| Main thread? | Never. Player counts are read on the main thread, the request runs in the background. |
| Open | No endpoint, no privacy policy, no retention decision, opt-in-vs-opt-out to be revisited before going live. |

## Known Limitations

Still-valid limitations from the previous readiness pass, plus the new ones:

- **No test against a running Paper server in this pass.** The build is
  green and `verifyShadedJarDrivers` checks the finished jar against a real
  database, but GUI navigation (back/close/pagination/empty-state/
  confirmations) and the moderation edge cases (expired ban/mute, restart,
  disconnect during freeze, staff-mode/vanish reconnect, inventory
  restoration) are still only code-reviewed. Prior experience in this
  project: a green build was once **not** sufficient - an actual server
  start uncovered two shading bugs no test suite could see (which is why
  `verifyShadedJarDrivers` exists).
- **Telemetry also hasn't been tested against a real endpoint** - there
  isn't one. The unit tests cover enable/disable, the payload, the failure
  path, jitter, and cleanup without any network; a real HTTP round trip is
  unverified.
- **Confirmation dialog history:** `ConfirmationDialog.open(...)` doesn't
  push its own entry onto the navigation stack, so `confirmCtx.back()`
  returns to whatever page's redraw callback is on top, not necessarily the
  page the dialog was opened from. A pre-existing, repo-wide pattern; the
  Performance dialogs work around it with `this.open(viewer)`. Not fixed, to
  avoid a wide, behavior-changing edit without live testing.
- **Two low-severity security items tracked as backlog:** no path-traversal
  validator on `database.file` (admin-only input, same trust level as the
  rest of `config.yml`), and no click-debounce on GUI confirmation buttons.
- **Architecture nitpick:** `ModerationPlayerLink` (a cross-module extension
  point) lives in the moderation module's own package instead of a neutral
  location; the lookup itself correctly goes through `ServiceRegistry`.
- **Known, unrelated bug:** `InGameNotificationService` renders a
  `Notification`'s message as literal text instead of parsing MiniMessage,
  so tags like `<yellow>` in a few lang keys show up unrendered in chat.
- **Supported Minecraft/Paper version range is not explicitly stated** -
  only "whatever API version `build.gradle.kts` currently pins". Needs an
  answer before a Modrinth upload.
- **No screenshots** for the README/Modrinth.
- **Settings GUI/commands are missing** (the service exists), as are the
  command frontends for Players/Moderation/Worlds/Whitelist/Performance.

## Recommended Next Steps

1. **Run a real Paper server** against the built jar and walk the GUI/
   moderation checklist above by hand.
2. **Capture screenshots** (list in
   [docs/release/modrinth.md](docs/release/modrinth.md)).
3. **Decide the supported version range** (which Paper/Minecraft versions
   are actually supported).
4. **Tag the first alpha release** following
   [docs/release/releasing.md](docs/release/releasing.md) - the workflow
   handles build, tests, release, jar, and SHA-256.
5. After that: the **public extension API** (ROADMAP.md Phase 4). The core
   has enough feature depth now; more built-in features increasingly compete
   with "make the existing surface extensible" for priority.
6. Independently: a **telemetry backend** with a privacy policy, retention,
   and a deliberate opt-in-vs-opt-out decision before any endpoint goes
   live.
