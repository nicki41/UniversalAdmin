// UniversalAdmin Placeholders - an official extension: PlaceholderAPI
// placeholders (%universaladmin_...%) for scoreboards, tab lists and chat
// formats. A separate plugin jar, built only against the public API
// (:universaladmin-api) like any third-party extension would be.

plugins {
    java
}

group = "dev.universaladmin.placeholders"
version = rootProject.version
base.archivesName.set("universaladmin-placeholderapi")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // Java 21 bytecode like every UniversalAdmin jar - see gradle.properties.
    disableAutoTargetJvm()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://repo.extendedclip.com/releases/") { name = "placeholderapi" }
}

dependencies {
    compileOnly(project(":universaladmin-api"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":universaladmin-api"))
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
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
