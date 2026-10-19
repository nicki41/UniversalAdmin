// UniversalAdmin Discord - the first official extension: mirrors selected
// audit events (bans, mutes, reports, restarts, ...) into a Discord channel
// through a webhook. A separate plugin jar, built only against the public
// API (:universaladmin-api) like any third-party extension would be.

plugins {
    java
}

group = "dev.universaladmin.discord"
version = rootProject.version
base.archivesName.set("universaladmin-discord")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // The Paper 26 API is published for Java 25 (its servers run 25), but the
    // jars are Java 21 bytecode so Paper 1.21.x servers on Java 21 load them
    // too - see gradle.properties. Without this, Gradle would refuse the
    // Java 25 dependency for a Java 21 target.
    disableAutoTargetJvm()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    compileOnly(project(":universaladmin-api"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":universaladmin-api"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Java 21 bytecode: Paper 1.21.x servers commonly run Java 21. The
    // toolchain stays 25; see gradle.properties for the supported versions.
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
