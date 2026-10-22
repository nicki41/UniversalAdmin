// What UniversalAdmin checks at a proxy - bans, network maintenance and the
// network whitelist - shared by the Velocity and the BungeeCord plugin, which
// shade it. Reads the database the servers share; never writes to it.
//
// Adventure (MiniMessage for the kick screens) and SnakeYAML are provided by
// Velocity; the BungeeCord plugin shades Adventure and gets SnakeYAML from
// BungeeCord.

plugins {
    `java-library`
}

group = "dev.universaladmin.proxy"
version = rootProject.version
base.archivesName.set("universaladmin-proxy-common")

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
    // The same driver and pool as the plugin; each proxy plugin shades and relocates them.
    api("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    api("com.zaxxer:HikariCP:6.3.0") {
        // Both proxies already ship slf4j.
        exclude(group = "org.slf4j")
    }
    compileOnly("net.kyori:adventure-api:$adventureVersion")
    compileOnly("net.kyori:adventure-text-minimessage:$adventureVersion")
    compileOnly("org.yaml:snakeyaml:2.2")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.kyori:adventure-api:$adventureVersion")
    testImplementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    testImplementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}
