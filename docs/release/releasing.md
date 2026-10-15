# Release Process

Short version: **a `v*` tag triggers everything.** `.github/workflows/release.yml`
checks that the tag matches the project version, builds, runs every test,
and publishes:

- a GitHub release with `universaladmin-core-<v>.jar` (+ `.sha256`),
  `universaladmin-discord-<v>.jar` (+ `.sha256`) and
  `universaladmin-api-<v>.jar` with `-sources.jar` and `-javadoc.jar`,
  release notes from the matching `CHANGELOG.md` section;
- the plugin jar as a new version on the Modrinth project (id `wGP5uSse`,
  game version `26.2`, loader `paper`) - see [Modrinth](#modrinth).

Nothing is published if a single test fails.

## Version scheme

[Semantic Versioning](https://semver.org/). The version lives in exactly one
place, `version = "..."` in `build.gradle.kts`; `plugin.yml`, the API and the
extension projects take it from there (`./gradlew -q printVersion`).

| Kind | Example | GitHub | Modrinth | Offered by the update check |
|---|---|---|---|---|
| Stable | `1.0.0`, `1.1.0`, `1.0.1` | release | release | yes |
| Pre-release | `1.1.0-beta.1`, `1.1.0-rc.1`, `1.1.0-alpha.3` | **pre-release** | beta/alpha | no |

From 1.0.0 on, pre-releases are flagged "Pre-release" on GitHub, so
`/releases/latest` - which the in-plugin update check and `/admin update`
read - only ever returns a stable version. (Before 1.0 every alpha was a
normal release on purpose; with stable users that would push alphas to them.)

What a version number promises from 1.0 on:

- **patch** (`1.0.1`): fixes only;
- **minor** (`1.1.0`): new features, fully backwards compatible - including
  the extension API (`universaladmin-api`) and `config.yml` (old files are
  migrated automatically);
- **major** (`2.0.0`): may contain breaking changes, documented under
  "Upgrading" in the changelog.

## Releasing 1.0.0 (and any stable version)

1. Make sure `CHANGELOG.md` has the section `## [<version>] - <date>` with
   today's date, and `[Unreleased]` above it is empty.
2. `version = "<version>"` in `build.gradle.kts`.
3. `./gradlew clean build` locally - green.
4. Merge to `main`. (`auto-release.yml` builds and tests again but does not
   release a stable version on its own.)
5. Tag and push:

   ```bash
   git checkout main && git pull
   git tag v1.0.0
   git push origin v1.0.0
   ```

6. Watch the "Release" workflow; afterwards check the GitHub release assets
   and the new version on Modrinth.
7. Start the next cycle: bump to the next version with a pre-release suffix
   (e.g. `1.1.0-alpha`) in a normal commit, and add a fresh `## [Unreleased]`.

## Automatic pre-releases

`auto-release.yml` runs on every push to `main` that touches more than docs
and CI metadata. If the version in `build.gradle.kts` is a plain
`X.Y.Z-alpha` or `X.Y.Z-alpha.N`, it bumps the counter (`-alpha.N+1`), moves
`[Unreleased]` into a dated section, commits `chore: release <version>`,
tags it and calls `release.yml`. Any other version (beta, rc, stable) is
left for the manual steps above. Release commits are recognized and never
trigger another release.

## Modrinth

Every release is mirrored by `Kira-NT/mc-publish` using the `MODRINTH_TOKEN`
repository secret (a Modrinth personal access token with version-create
rights for the project). `fail-mode: warn` - a Modrinth hiccup never fails
the GitHub release. The version type (release/beta/alpha) is derived from
the version string. To claim another game version after testing it, extend
`game-versions` in `release.yml` (e.g. `"26.2\n26.3"`) and the supported
versions in [docs/user/installation.md](../user/installation.md#supported-versions).

Project page content (description, gallery, links) is maintained by hand -
see [modrinth.md](modrinth.md).

## When something goes wrong

- **Tag/version mismatch:** the workflow stops before building. Delete the
  tag (`git push --delete origin v1.0.0`), fix the version, tag again.
- **A test fails:** nothing is published. Fix, merge, tag again.
- **A broken release went out:** never move or reuse a tag. Publish a fixed
  patch version (`1.0.1`) and mention it in the changelog.
