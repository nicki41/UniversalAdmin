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
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    compileOnly(project(":universaladmin-api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.115-stable")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":universaladmin-api"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.115-stable")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
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
