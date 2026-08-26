import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.universaladmin"
// Semantic versioning; "-alpha" reflects actual status (see ROADMAP.md) -
// not a 1.0 or a stable-implying bare version number.
version = "0.1.0-alpha.4"
description = "UniversalAdmin - a universal admin platform for Paper servers"

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
    // Paper API - provided by the server at runtime, never shaded.
    compileOnly("io.papermc.paper:paper-api:26.2.build.115-stable")

    // Storage: SQLite is the default database, bundled and shaded into the
    // plugin jar because Paper does not provide a JDBC driver.
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    // MySQL/MariaDB is optional at runtime (see storage.md), but the driver
    // still has to ship in the jar so the option works out of the box.
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    // Connection pooling for both database backends.
    implementation("com.zaxxer:HikariCP:6.3.0")

    // Testing - business logic is written to be testable without a running
    // Paper server. See docs/development/testing.md.
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper API types (e.g. org.bukkit.plugin.Plugin) are mocked, never run
    // against a real server, in tests that need them - see
    // docs/development/testing.md.
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

// Shade our runtime dependencies (JDBC drivers, HikariCP) under our own
// package so they never collide with another plugin's copy of the same
// libraries on the same server classpath.
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    val shadePrefix = "dev.universaladmin.libs"
    // sqlite-jdbc is deliberately NOT relocated: it bundles a native (JNI)
    // library whose compiled binary has the class name
    // "org/sqlite/core/NativeDB" baked in for native-method linking.
    // Relocating the Java class breaks that binding at the JNI layer
    // (confirmed: fails with "ClassNotFoundException: org/sqlite/core/NativeDB"
    // the moment a real connection is opened, even though the relocated jar
    // builds and every other check passes) - renaming the Java side doesn't
    // rename the native library's hardcoded symbol. It still ships shaded
    // (bundled) into the jar, just under its original package name; a
    // classpath collision with another plugin's own sqlite-jdbc copy is the
    // accepted tradeoff, same as every other Bukkit plugin bundling this
    // driver. MariaDB's driver is pure Java (no JNI) and HikariCP has no
    // native code either, so both relocate safely.
    relocate("org.mariadb", "$shadePrefix.mariadb")
    relocate("com.zaxxer.hikari", "$shadePrefix.hikari")
    // Both JDBC drivers are loaded reflectively by HikariCP
    // (HikariConfig#setDriverClassName -> Class.forName(...)), which
    // minimize()'s static-reachability analysis cannot see - left
    // unexcluded, it strips the entire driver out of the jar as
    // "unused", which only fails at runtime (HikariConfig: "Failed to
    // load driver class") once a real server actually tries to connect,
    // never during compile or the test suite (which runs the drivers
    // unshaded, off the test classpath). Both drivers are excluded from
    // minimization entirely so this can't happen again.
    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Runs ShadedJarDriverSmokeTestMain against ONLY the built shadowJar plus
// the compiled test classes - no other dependency on the classpath. This
// is the one check in the whole build that would have caught both real
// bugs found by an actual server startup (a driver class silently
// stripped by minimize(), and sqlite-jdbc's JNI native library broken by
// relocation): the ordinary test suite runs against unshaded,
// unminimized dependency jars, so it can't see either failure mode -
// they only ever showed up once a real Paper server opened a real
// connection through the final jar. See ShadedJarDriverSmokeTestMain's
// javadoc for the full story.
val verifyShadedJarDrivers = tasks.register<JavaExec>("verifyShadedJarDrivers") {
    // shadowJar and the plain jar task both write to the same filename
    // (archiveClassifier is empty) - Gradle's task-output validation flags
    // that ambiguity unless both are named explicitly, even though
    // shadowJar already depends on jar and runs after it.
    dependsOn(tasks.jar, tasks.named("shadowJar"), tasks.compileTestJava)
    classpath = files(tasks.named("shadowJar")) + sourceSets.test.get().output
    mainClass.set("dev.universaladmin.storage.jdbc.ShadedJarDriverSmokeTestMain")
}

tasks.check {
    dependsOn(verifyShadedJarDrivers)
}

// Single source of truth for "what version is this?" in CI. The release
// workflow compares a pushed tag (v0.1.0-alpha) against this before building,
// so a tag can never publish a jar built from a different version, and the
// build workflow uses it to name its artifact. Read at configuration time so
// the task stays configuration-cache compatible.
tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version (used by the CI release tag check)."
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}
