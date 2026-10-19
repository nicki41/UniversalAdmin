// universaladmin-api - the public extension API. See docs/extensions/ and
// docs/architecture/decisions/0010-extension-api.md. Versioned together with
// the plugin (the root project's version); from 1.0.0 on, the API follows
// semantic versioning: no breaking change without a major version bump.

plugins {
    `java-library`
    `maven-publish`
}

group = "dev.universaladmin"
version = rootProject.version
description = "UniversalAdmin extension API"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // The Paper 26 API is published for Java 25 (its servers run 25), but the
    // jars are Java 21 bytecode so Paper 1.21.x servers on Java 21 load them
    // too - see gradle.properties. Without this, Gradle would refuse the
    // Java 25 dependency for a Java 21 target.
    disableAutoTargetJvm()
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    // Provided by the server at runtime; part of the API's surface (Player,
    // Component, ...), so consumers get it on their compile classpath too.
    compileOnlyApi("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Java 21 bytecode: Paper 1.21.x servers commonly run Java 21. The
    // toolchain stays 25; see gradle.properties for the supported versions.
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        // The API docs link to a few core/module classes for context that
        // are not part of this jar - don't fail the build on those links.
        addStringOption("Xdoclint:all,-reference,-missing", "-quiet")
        links("https://jd.papermc.io/paper/26.2/")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "UniversalAdmin API",
            "Implementation-Version" to project.version,
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "universaladmin-api"
            from(components["java"])
            pom {
                name.set("UniversalAdmin API")
                description.set("Extension API for UniversalAdmin, the admin platform for Paper servers.")
                url.set("https://github.com/nicki41/UniversalAdmin")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
