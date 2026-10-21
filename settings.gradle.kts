// Two projects:
//
// - universaladmin-api (api/): the public, versioned extension API - the
//   interfaces and framework classes an extension compiles against (modules,
//   actions, commands, GUI framework, settings, storage, audit, ...). Package
//   names are the same as in the core; the split is a jar boundary, not a
//   rename. See docs/architecture/decisions/0010-extension-api.md.
// - universaladmin-core (the repository root): the plugin itself - bootstrap,
//   implementations (JDBC, YAML, Paper adapters) and the built-in modules.
//   Its shaded jar contains the API classes unrelocated, so extensions find
//   them at runtime.
//
// The example extension in examples/ is built in CI to prove the API is
// usable from outside, but is never shipped inside the plugin jar.

plugins {
    // Lets Gradle auto-provision a Java 25 toolchain when the host JDK is a
    // different version, so `./gradlew build` works the same on every machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "universaladmin-core"

include("universaladmin-api")
project(":universaladmin-api").projectDir = file("api")

include("example-extension")
project(":example-extension").projectDir = file("examples/example-extension")

include("universaladmin-discord")
project(":universaladmin-discord").projectDir = file("extensions/discord")
include("universaladmin-placeholderapi")
project(":universaladmin-placeholderapi").projectDir = file("extensions/placeholderapi")

// UniversalAdmin for Velocity - an optional proxy plugin for network mode
// (ban check at the proxy). Not a Paper plugin; Java 21 bytecode so it runs
// on Velocity 3.4+ and 4.x.
include("universaladmin-velocity")
project(":universaladmin-velocity").projectDir = file("proxy/velocity")
