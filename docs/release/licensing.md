# Licensing

**Not legal advice.** This document records which license applies to which
part of UniversalAdmin, and why. It is not legal counsel. For binding
statements - especially on compatibility with the Paper API, on trademark/
naming questions, and on anything meant to be sold commercially later - talk
to a lawyer before building on it.

## Decided: Core under Apache-2.0

The core (this repository) is licensed under the **Apache License 2.0**. The
complete, unmodified license text is in [LICENSE](../../LICENSE) in the
repository root.

Reasons for Apache-2.0 over the alternatives:

- **Permissive, like MIT** - maximum adoption, low barrier to entry for
  server operators and contributors. Commercial use is explicitly permitted.
- **Explicit patent grant**, which MIT lacks. Once third parties contribute
  extensions and integrations, that's the practically relevant difference.
- **No copyleft question in the extension ecosystem.** Under GPL/AGPL,
  extension authors would first have to figure out whether their extension
  is a "derivative work" of the core - a question that's regularly disputed
  for plugin architectures and discourages commercial extension authors.
- **The default in the Java/server-software ecosystem**, so contributors are
  already familiar with it.

Deliberately accepted trade-off: a permissive license lets third parties fork
the core and reuse it proprietarily. That's the price of letting an open-core
model with possible future proprietary extensions work cleanly at all.

## What This Means for Each Part

| Part | License | Status |
|---|---|---|
| **Core** (this repository) | Apache-2.0 | applies now |
| **Public extension API** (`universaladmin-api`, [ROADMAP.md](../../ROADMAP.md) Phase 4) | Apache-2.0 planned | not yet implemented |
| **SDK / example extensions** | Apache-2.0 planned | not yet implemented |
| **Community extensions** | free choice | up to each extension's own author |
| **Future official premium extensions** | may be separately proprietary | none exist |
| **Marketplace/web backend** | may be separately licensed | no implementation, see [web-future.md](../architecture/web-future.md) |

In detail:

- **Community extensions** may use any license compatible with Apache-2.0 -
  including proprietary licenses. Apache-2.0 doesn't require a particular
  license from code built on top of the core.
- **Official premium extensions** aren't planned today and don't exist. The
  license choice keeps that door open: a separately developed, separately
  distributed extension could be proprietary without that forcing the core
  to close. The core stays Apache-2.0.
- **A marketplace or web backend** would be its own project with its own
  license. Nothing about the core's license forces a hosted backend to be
  open.
- **Contributions** to this repository are made under Apache-2.0 (§5 of the
  license text: contributions are under the same terms absent a separate
  agreement). There is currently **no** additional CLA.

## Open Items

- **Copyright line.** `LICENSE` contains the unmodified official Apache-2.0
  text, including the `Copyright [yyyy] [name of copyright owner]` appendix
  placeholder. Who is listed as the rights holder (an individual, later
  possibly an organization) is deliberately not filled in - that's the
  project owner's decision, not a technical one.
- **NOTICE file.** Apache-2.0 doesn't require one, and there's currently
  nothing for it to contain. If third-party Apache-2.0 sources are ever
  copied into the core, a `NOTICE` file should be reconsidered at that point.
- **Paper API compatibility.** `compileOnly("io.papermc.paper:paper-api:...")`
  isn't bundled but supplied by the server at runtime - the usual Bukkit
  plugin mechanism. Less of a concern with a permissive license than with
  copyleft, but not something assessed authoritatively here either.
- **Bundled dependencies.** The shaded jar includes `sqlite-jdbc`,
  `mariadb-java-client`, and `HikariCP` (see `build.gradle.kts`). Their own
  license terms apply to the classes they contribute regardless of the
  core's license; before the first distribution channel (Modrinth), check
  whether their license notices need to be shipped alongside.
