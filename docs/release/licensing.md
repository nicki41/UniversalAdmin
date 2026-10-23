# Licensing

**Not legal advice.** This document records which license applies to which
part of UniversalAdmin, and why. It is not legal counsel. For binding
statements - especially on compatibility with the Paper API, on trademark/
naming questions, and on anything meant to be sold commercially later - talk
to a lawyer before building on it.

## Decided: GPL-3.0-or-later, the Extension API under Apache-2.0

From **1.5.0** on, UniversalAdmin is licensed under the **GNU General Public
License, version 3 or (at your option) any later version**
(GPL-3.0-or-later). The complete, unmodified license text is in
[LICENSE](../../LICENSE) in the repository root.

Two parts stay under the **Apache License 2.0**, each with its own `LICENSE`
file next to its build file:

- the extension API, [`api/`](../../api/LICENSE) (`universaladmin-api`)
- the example extension, [`examples/example-extension/`](../../examples/example-extension/LICENSE)

Versions up to and including **1.4.0** were released under Apache-2.0 and
stay available under it.

Reasons for the change:

- **Improvements stay open.** Whoever distributes a modified UniversalAdmin
  has to do so under the same license, with the source - a fork can't be
  turned into a closed product.
- **The extension ecosystem keeps every license.** The main reason Apache-2.0
  was chosen in the first place still holds: extensions are built against
  `universaladmin-api`, and that stays Apache-2.0, so extension authors pick
  their own license, proprietary ones included.
- **Compatible with everything UniversalAdmin ships or builds on.** The
  bundled libraries are under Apache-2.0 (sqlite-jdbc, HikariCP, snakeyaml),
  MIT (adventure, in the BungeeCord plugin) and LGPL-2.1-or-later (MariaDB
  Connector/J); PlaceholderAPI, which the Placeholders extension builds on,
  is GPL-3.0 itself.
- **"Or later"**, as the FSF recommends: a future GPL version can be applied
  without having to ask everyone who ever contributed.

Deliberately accepted trade-off: nobody can ship a proprietary fork of the
core any more - that is the point of the change.

## What This Means for Each Part

| Part | License |
|---|---|
| **Core plugin** (`src/`), **proxy plugins** (`proxy/`), **official extensions** (`extensions/`) | GPL-3.0-or-later |
| **Extension API** (`api/`, `universaladmin-api`) | Apache-2.0 |
| **Example extension** (`examples/example-extension/`) | Apache-2.0 |
| **Community extensions** | free choice - up to each extension's own author |
| **Future official premium extensions** | may be separately proprietary - none exist |
| **Marketplace/web backend** | may be separately licensed - no implementation, see [web-future.md](../architecture/web-future.md) |

In detail:

- **Community extensions** that use `universaladmin-api` may use any license
  compatible with Apache-2.0 - including proprietary licenses. An extension
  that copies code out of the core (instead of calling the API) takes on the
  core's GPL for that code.
- **Official premium extensions** aren't planned today and don't exist. Built
  on the Apache-2.0 API, one could still be proprietary.
- **A marketplace or web backend** would be its own project with its own
  license.
- **Every jar carries its license** as `META-INF/LICENSE` - the API jar as
  `META-INF/LICENSE-api`, because the API classes are shaded into the core
  jar and keep their Apache-2.0 text there next to the core's GPL.
- **Contributions** to this repository are made under the license of the part
  they change - GPL-3.0-or-later, or Apache-2.0 under `api/` and
  `examples/`. There is **no** additional CLA.

## Open Items

- **Copyright line.** The `LICENSE` files contain the unmodified official
  texts; source files carry no license headers - the `LICENSE` files and
  this document state the terms. Who is named as the rights holder (an
  individual, later possibly an organization) is the project owner's
  decision, not a technical one.
- **NOTICE file.** Neither license requires one here, and there's currently
  nothing for it to contain. If third-party Apache-2.0 sources are ever
  copied into the project, a `NOTICE` file should be reconsidered at that
  point.
- **Paper API compatibility.** `compileOnly("io.papermc.paper:paper-api:...")`
  isn't bundled but supplied by the server at runtime - the usual Bukkit
  plugin mechanism. Not assessed authoritatively here.
- **Bundled dependencies.** The shaded jars include the libraries listed
  above (see each `build.gradle.kts`). Their own license terms apply to the
  classes they contribute regardless of UniversalAdmin's license.
