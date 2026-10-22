// UniversalAdmin for BungeeCord (and its forks) - the same optional proxy
// plugin as UniversalAdmin Velocity: turns banned players away at the proxy,
// keeps it closed during network maintenance and to players not on the
// network whitelist, and shows the maintenance in the server list. The checks
// are proxy/common; this is the BungeeCord adapter. Reads the same
// MySQL/MariaDB database as the servers; it never writes to it.
//
// BungeeCord has no Adventure: MiniMessage and Adventure are shaded and
// relocated here, and every text becomes a BungeeCord component.

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

group = "dev.universaladmin.bungee"
version = rootProject.version
base.archivesName.set("universaladmin-bungee")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

val adventureVersion = "4.26.1"

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4") {
        // Brigadier only comes from Mojang's own repository, and the plugin needs none of it.
        exclude(group = "com.mojang")
    }
    implementation(project(":universaladmin-proxy-common"))
    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
    // proxy/common reads its config.yml with SnakeYAML; shaded so the plugin
    // does not depend on the copy inside the proxy.
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("bungee.yml") {
        expand(props)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    val libs = "dev.universaladmin.bungee.libs"
    relocate("org.mariadb", "$libs.mariadb")
    relocate("com.zaxxer.hikari", "$libs.hikari")
    relocate("net.kyori", "$libs.kyori")
    relocate("org.yaml.snakeyaml", "$libs.snakeyaml")
    mergeServiceFiles()
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
