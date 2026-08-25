// UniversalAdmin is a single Gradle project for now (the "core").
//
// Why not a multi-project build yet: `universaladmin-api`, `universaladmin-sdk`
// and `universaladmin-web` don't exist as real, independently-consumed
// artifacts yet - there are no external extensions and no web app to build
// against. Introducing extra Gradle subprojects today would just be structure
// with nothing inside it. The seam is prepared in code instead: everything an
// external extension or the web app would need lives behind the
// `dev.universaladmin.contracts` package (see docs/architecture/decisions/0006-optional-web-architecture.md
// and 0005-extension-ready-design.md). When `universaladmin-api` is actually
// extracted, that package becomes its content with minimal churn.
//
// See docs/architecture/overview.md for the full reasoning.

plugins {
    // Lets Gradle auto-provision a Java 25 toolchain when the host JDK is a
    // different version, so `./gradlew build` works the same on every machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "universaladmin-core"
