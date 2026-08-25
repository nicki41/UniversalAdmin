# Contributing

Thanks for your interest in UniversalAdmin. This document describes what you
need to contribute and what a pull request is measured against.

Short version: read
[docs/development/architecture-rules.md](docs/development/architecture-rules.md)
and [ARCHITECTURE.md](ARCHITECTURE.md) before you build anything. The
architecture rules are binding, not a suggestion - they're the reason the
same logic can later be reused by the GUI, a command, and a web API.

## Setup

You need:

- **Java 25.** The Gradle wrapper auto-provisions a matching toolchain via
  `foojay-resolver-convention` if none is installed locally - so a different
  local JDK is enough to get the build started.
- **No Gradle install required** - use the bundled wrapper (`./gradlew`, on
  Windows `gradlew.bat`).
- An IDE with Gradle import (IntelliJ IDEA, Eclipse, VS Code) - optional.

```bash
git clone https://github.com/nicki41/UniversalAdmin.git
cd UniversalAdmin
./gradlew build
```

`build` compiles, runs every test, builds the shaded jar under `build/libs/`,
and additionally checks via `verifyShadedJarDrivers` that the bundled
database drivers actually work in the final jar. More detail:
[docs/development/setup.md](docs/development/setup.md).

## Architecture

Everything binding is in
[docs/development/architecture-rules.md](docs/development/architecture-rules.md).
The points pull requests most often trip on:

- **Business logic lives in services and actions**, not in GUI click handlers
  or command executors. Frontends call, they don't decide.
- **No SQL outside a `*Repository` or `Migration` implementation.** Services
  only know the `Repository` interface, never `Connection` or `DataSource`.
- **Mutating operations run through `ActionExecutor`**, never a direct
  `Action.execute(...)` call - otherwise permission checks and the audit
  entry are skipped.
- **No blocking database calls on the Paper main thread.** All IO goes
  through `TaskScheduler.supplyAsync`/`runAsync`, all Bukkit API through
  `runOnMainThread`. See
  [docs/architecture/threading.md](docs/architecture/threading.md).
- **No visible text in code.** Every user-facing string is a `MessageKey`,
  resolved through `MessageService` from `lang/<locale>.yml` - and added to
  **both** shipped languages.
- **No `config.getString(...)`.** Every configuration value is a typed,
  validated `SettingDefinition` - see
  [docs/development/settings.md](docs/development/settings.md).
- **No new dependency without a reason** stated in the PR text. In
  particular, no mandatory dependency on Vault, LuckPerms, PlaceholderAPI, or
  ProtocolLib.

Reworking the architecture is a separate conversation and gets its own ADR
under [docs/architecture/decisions/](docs/architecture/decisions/) - never a
side effect of a feature PR.

## Adding a Module

Step-by-step guide using the `players` module as a template:
[docs/development/adding-module.md](docs/development/adding-module.md).

## Testing

- Every new piece of business logic (service, action, migration with
  non-trivial logic) needs a unit test that runs **without** a live Paper
  server.
- Repositories are tested against an in-memory fake of the interface, not
  mocked, wherever a fake is simpler.
- Migrations are tested against a real, temporary SQLite database.
- No tests that make real network requests.
- No getter tests.

```bash
./gradlew test
```

Full conventions: [docs/development/testing.md](docs/development/testing.md).

## Code Style

- Java 25, 4-space indentation, UTF-8, no tabs.
- Interfaces without a prefix/suffix (`Repository`, `Module`, `Action`),
  concrete implementations with a descriptive prefix
  (`JdbcPlayerProfileRepository`).
- Domain models are `record`s, not classes with setters.
- Typed IDs instead of raw strings (`ModuleId`, `ActionId`, `GuiPageId`).
- Comments explain *why*, not *what* - especially where a more obvious
  solution was deliberately rejected.

Full detail: [docs/development/conventions.md](docs/development/conventions.md).

## Documentation

If your PR changes an architecture decision, module behavior, a permission,
or a configuration option, the relevant file under `docs/` (or
`README.md`/`ROADMAP.md`/`CHANGELOG.md`) gets updated **in the same PR** -
not "later".

If a change touches telemetry, it additionally belongs in
[docs/user/telemetry.md](docs/user/telemetry.md). Nothing is collected that
isn't documented there.

## Pull Request Flow

1. Fork the repo, branch off `main`.
2. Implement the change, add tests, carry documentation along in the same
   set of commits.
3. `./gradlew build` must be green locally.
4. Open a PR against `main` and work through the checklist in the PR
   template.
5. CI (build + tests) must be green before merging.

**Commit messages** are short, to the point, and explain *why*, not just
*what* - the diff already shows *what*. A prefix in the style of
`feat:`/`fix:`/`docs:`/`chore:` is welcome but not required.

## License of Contributions

By opening a pull request you submit your contribution under the
[Apache License 2.0](LICENSE) (§5 of the license text). There is no
additional CLA.
