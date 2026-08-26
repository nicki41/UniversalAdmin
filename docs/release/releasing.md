# Release Process

How a UniversalAdmin release comes to be. Short version: **a version tag
triggers everything else.** There is no manual upload step and no "release
button" - `.github/workflows/release.yml` builds, tests, and publishes.

## Automatic releases

`.github/workflows/auto-release.yml` runs on every push to `main` and does
the "Steps" section below by itself, whenever both are true:

- the push touched at least one file outside `docs/`, `.github/`, and the
  small set of root Markdown/meta files (`README.md`, `CHANGELOG.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `ROADMAP.md`, `ARCHITECTURE.md`,
  `RELEASE_READINESS.md`, `LICENSE`, `.gitignore`) - a documentation-only or
  CI-only push is skipped, nothing else is;
- the current version in `build.gradle.kts` is a plain `X.Y.Z-alpha` or
  `X.Y.Z-alpha.N` prerelease. A beta/rc/stable bump is a bigger decision
  than this workflow makes on its own - it logs a notice and does nothing,
  leaving the manual process below as the only way to publish one.

When it runs: it builds and tests the tree exactly as pushed, bumps the
`-alpha.N` counter (`-alpha` with no number counts as `-alpha.0`, so the
first automatic release becomes `-alpha.1`), moves the `CHANGELOG.md`
`[Unreleased]` section into a new dated one (leaving a fresh, empty
`[Unreleased]` above it), commits that as `chore: release <version>`, tags
it `v<version>`, and pushes both to `main` - using the default
`GITHUB_TOKEN`, which deliberately does **not** trigger another workflow run
from that push (GitHub's own recursion guard). Because of that,
`auto-release.yml` calls `release.yml` directly via `workflow_call` instead
of relying on the tag push to fire it - same build/test/publish steps
either way, see that workflow's comments.

`chore: release <version>` pushes are recognized and skipped by
`auto-release.yml` itself (a second, independent guard against a release
commit triggering another release), so this never loops.

The rest of this document describes the **manual** path - still the only
way to cut a beta/rc/stable release, and always available as a fallback if
the automatic one is ever skipped or needs a different version number than
a plain counter bump.

## Version Scheme

[Semantic Versioning](https://semver.org/), with pre-release suffixes:

| Version | Tag | GitHub release type |
|---|---|---|
| `0.1.0-alpha.1` | `v0.1.0-alpha.1` | Prerelease |
| `0.2.0-beta.1` | `v0.2.0-beta.1` | Prerelease |
| `1.0.0-rc.1` | `v1.0.0-rc.1` | Prerelease |
| `1.0.0` | `v1.0.0` | normal release |

The workflow detects `-alpha`, `-beta`, and `-rc` in the version string and
marks the release as a prerelease automatically. Anything else is a normal
release.

The version lives in exactly **one** place: `version = "..."` in
`build.gradle.kts`. `plugin.yml` gets it substituted in at build time
(`processResources`), and `./gradlew -q printVersion` prints it - which is
also what CI reads.

## Steps

### 1. Set the version

In `build.gradle.kts`:

```kotlin
version = "0.1.0-alpha.1"
```

### 2. Update the changelog

Move the content out of `## [Unreleased]` into a versioned section:

```markdown
## [0.1.0-alpha.1] - 2026-08-25
```

If a section with exactly this heading (`## [<version>]`) exists, the release
workflow additionally pulls its content into the release notes. If there
isn't one, only GitHub's generated commit/PR notes are used - not an error,
just less context.

### 3. Build and check locally

```bash
./gradlew clean build
```

Must be green - including tests and `verifyShadedJarDrivers` (opens a real
database connection through the finished jar). A red local build will be red
in CI the same way; no release is produced then.

Before a real release, also do what no build can check: start the jar on an
actual Paper server and click through the affected GUI paths. See
[RELEASE_READINESS.md](../../RELEASE_READINESS.md) for the known limits of
automated checking.

### 4. Commit

```bash
git add -A
git commit -m "chore: release 0.1.0-alpha.1"
```

### 5. Tag

```bash
git tag v0.1.0-alpha.1
```

The tag must exactly match the version in `build.gradle.kts` with a leading
`v`. If it doesn't, **the release workflow fails** before anything is
published - that's intentional, so `v0.2.0` can never ship a `0.1.0` jar.

### 6. Push

```bash
git push origin main
git push origin v0.1.0-alpha.1
```

## What Happens Automatically After That

`.github/workflows/release.yml` runs and:

1. checks out the tag,
2. sets up Java (Gradle runs on 21, compiles with the Java 25 toolchain),
3. **checks the tag against the project version** - on mismatch: abort, no
   release,
4. runs `./gradlew clean build` including every test - if anything fails,
   **no** release is created,
5. locates `build/libs/universaladmin-core-<version>.jar` (the installable,
   shaded jar - no sources, javadoc, or unshaded jar),
6. generates `<jar>.sha256` next to it,
7. creates the GitHub release: title `UniversalAdmin <version>`, notes
   generated by GitHub (plus the changelog section, if present), prerelease
   flag based on the version suffix,
8. attaches the jar and the SHA-256 file.

Only the automatic `GITHUB_TOKEN` with `contents: write` is used. There are
no additional secrets.

## If Something Goes Wrong

- **Tag and version don't match:** the workflow fails, nothing was
  published. Fix the version or the tag. Delete an already-pushed wrong tag
  with `git push origin :refs/tags/v0.2.0` and set it again - commit history
  is untouched.
- **Build/tests fail:** fix the cause, commit and push normally, then set the
  tag again. No force push to `main`.
- **A release went out with the wrong content:** push a follow-up patch
  release (`0.1.0-alpha.2`) rather than overwriting a published release.

## Modrinth

**No** automatic Modrinth publishing yet: there's neither a Modrinth project
nor an API token for it. The prepared project page and checklist are in
[modrinth.md](modrinth.md).

The release workflow is structured so this can later be an additional step at
the end - version, jar path, and prerelease flag are already available as
step outputs; only an upload step and a repository secret with the Modrinth
token would need to be added.

## What This Process Deliberately Doesn't Do

- **No automatic version bump past a plain `-alpha` counter.** A beta/rc/
  stable release is a deliberate decision made by hand - see "Automatic
  releases" above.
- **No publishing from `build.yml`.** The build workflow only uploads the
  jar as an Actions artifact (traceable, temporary), never publishes.
- **No automated dependency updates.** No Dependabot; dependencies are
  updated manually and deliberately (see
  [SECURITY.md](../../SECURITY.md#dependencies)).
