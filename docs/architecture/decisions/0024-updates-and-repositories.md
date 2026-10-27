# 0024 - Automatic Updates and One Organization for Core and Extensions

## Status

Accepted (1.6.0)

## Context

UniversalAdmin checked GitHub every few hours and told the staff online at
that moment; `/admin update` downloaded the plugin jar. Three gaps: staff who
joined later never heard of an update, nothing was ever downloaded on its
own, and the extensions and proxy plugins were not covered at all. A fourth
turned up while looking: `/admin update` took the *first* `.jar` of a
release - since releases carry the API, its sources and javadoc and the
extensions, that was the API's javadoc jar, and the checksum check (of the
wrong file, too) refused it every time.

At the same time the extensions should live on their own - own repository,
own releases, own changelog - grouped like the Modrinth collection.

## Decision

- **Update targets.** The check covers a list of targets: UniversalAdmin
  itself and every extension that registered an update source with
  `ExtensionManager#updatesFrom(plugin, "owner/name", assetPrefix)` (API
  1.5). Each target is checked against its own repository's latest release.
- **The jar by name.** A release's plugin jar is the asset named
  `assetPrefix + version + ".jar"` (`universaladmin-core-1.6.0.jar`), its
  checksum `<jar>.sha256` - never "the first jar".
- **Automatic downloads, within a major version.** With
  `update.auto-download` (on by default) a newer release is downloaded into
  `plugins/update/` right away and applied by the server at the next
  restart - only when the major version stays the same; a new major version
  waits for `/admin update`, which takes it too. Every check repeats the
  notice while an update is pending (console and staff), and staff with
  `universaladmin.update.notify` get it when they join.
- **Proxies only hear about it.** The Velocity and BungeeCord plugins log a
  newer release at startup and every 6 hours (`update-check.enabled` in
  their `config.yml`). A proxy is not swapped while it runs, and neither
  proxy has an "apply at the next start" folder.
- **One organization.** Everything moves to the GitHub organization
  `universaladmin-mc`: the core (with the API and the proxy plugins, which
  are released together with it) as `universaladmin-mc/UniversalAdmin`, and
  every official extension in a repository of its own
  (`universaladmin-mc/universaladmin-discord`, ...), built against the
  published API and released with its own version, changelog and Modrinth
  upload. GitHub redirects the old addresses; the update check follows
  redirects from now on.
- **Logos.** A logo family in `assets/logo/` (SVG sources, rendered to the
  512 px PNGs in `assets/`): the core's shield with a "U", and the same tile
  with its own colour and symbol for each extension and the proxy plugins.

## Consequences

- Installations up to 1.5.0 do not follow redirects and look for releases at
  the old address - they stop hearing about updates after the move and need
  one manual update.
- An extension released on its own can need a newer UniversalAdmin; it says
  so in its `plugin.yml` check (`API_VERSION`) and its Modrinth dependency.
- The documentation stays in the core repository; each extension's own
  repository links to its page.

## Alternatives

- **Downloading every update, majors included.** A major version may need
  manual steps (see the changelog's "Upgrading" notes) - rejected.
- **Hot-swapping the plugin.** Far more fragile than Paper's update folder.
- **Keeping the extensions in the core repository.** Simpler releases, but
  every extension would stay tied to the core's version and changelog.
