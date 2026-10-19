# 0014 - One Jar for Every Supported Minecraft Version

## Status

Accepted (1.1.0)

## Context

UniversalAdmin was built for a single Paper version with Java 25 bytecode.
Many servers stay on an older Minecraft version for a long time, and a new
Paper version appears every few months. Server owners download from
Modrinth, which only offers a release for the Minecraft versions it lists.

## Decision

- **One jar per release** for every supported version: Java 21 bytecode
  (`options.release.set(21)`, built with the Java 25 toolchain) and
  `api-version: '1.21.7'` in every `plugin.yml`.
- **The floor is Paper 1.21.7**, the first version with the dialog API the
  GUI text input uses. Code may only use API that exists on the floor; an
  API that changed later is accessed through a small reflective helper
  (e.g. `WorldBorderWarnings`).
- **`supported-versions.txt` is the single list** of supported Minecraft
  versions, in Modrinth's naming. The release workflow publishes every
  release for exactly those versions.
- **CI checks both ends**: every build compiles against the oldest and the
  newest Paper API (`-PpaperApiVersion`). A weekly compatibility workflow
  compiles against and boots (`scripts/paper-smoke-test.sh`) every Paper
  version not listed yet; if it passes, the version is added to the newest
  Modrinth release at once and a pull request adds it to the list.
  Failures open an issue.

## Consequences

- A new Minecraft version is usually available on Modrinth within a week,
  without a new UniversalAdmin release.
- Raising the floor is a deliberate change (new API wanted) and is noted in
  the changelog.
- Java 21 language features only, also in the API module.

## Alternatives

- **One jar per Minecraft version.** More builds, more downloads to choose
  from, no benefit while the API stays compatible.
- **Always only the newest version.** Simplest, but locks out most servers
  for months after each Minecraft release.
