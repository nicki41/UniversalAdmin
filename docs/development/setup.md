# Development Setup

## Prerequisites

- JDK 25 (auto-provisioned via the `foojay-resolver-convention` Gradle
  plugin if no local Java 25 installation is found - no manual JDK
  management needed).
- No locally installed Gradle needed, the wrapper (`gradlew`/`gradlew.bat`)
  is enough.

## Building

```bash
./gradlew build
```

Compiles, tests, and produces `build/libs/universaladmin-core-<version>.jar`
- already shaded with `sqlite-jdbc`, `mariadb-java-client`, and `HikariCP`
(relocated under `dev.universaladmin.libs.*`, see `build.gradle.kts`).

Compile only, no tests/jar:

```bash
./gradlew compileJava
```

Tests only:

```bash
./gradlew test
```

## Testing Locally Against a Paper Server

There is currently no automated dev-server setup in this repository.
Manually:

1. `./gradlew build`
2. Set up a current Paper server jar (matching the Paper API version
   referenced in `build.gradle.kts`) in a separate folder.
3. Copy `build/libs/universaladmin-core-<version>.jar` to `plugins/`.
4. Start the server, accept `eula.txt`, check `config.yml` under
   `plugins/UniversalAdmin/` (see
   [docs/user/configuration.md](../user/configuration.md)).

A `run/` folder for a local test server is anticipated in `.gitignore`, in
case this gets automated later (e.g. via the
`xyz.jpenilla.run-paper` Gradle plugin) - not set up yet.

## IDE

Any IDE with Gradle support (IntelliJ IDEA, VS Code with Java extensions)
works - import `settings.gradle.kts`/`build.gradle.kts`, Java toolchain
detection handles the rest.
