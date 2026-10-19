// A complete, minimal UniversalAdmin extension - the template to copy.
// Built in CI to prove the public API is usable from outside the core; it
// is never shipped inside the UniversalAdmin jar.
//
// In your own project, depend on the published API instead of the project:
//   repositories { maven("https://jitpack.io") }
//   compileOnly("com.github.nicki41.UniversalAdmin:universaladmin-api:1.0.0")
// or on the plugin jar itself from Modrinth's Maven:
//   repositories { maven("https://api.modrinth.com/maven") }
//   compileOnly("maven.modrinth:universaladmin:1.0.0")
// See docs/extensions/getting-started.md.

plugins {
    java
}

group = "dev.universaladmin.example"
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    compileOnly(project(":universaladmin-api"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
