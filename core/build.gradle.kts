// blockies-economy :core
//
// Pure Java. No Minecraft, no loader, no third-party runtime dependencies.
// Compiled at Java 8 bytecode so the same classes can be bundled into every
// loader jar from 1.12.2 through 26.x without recompilation.

plugins {
    `java-library`
}

group = "${property("mod_group")}.core"
version = property("mod_version") as String

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    // --release also checks against the Java 8 *API*, not just the bytecode level,
    // so a stray Java 9+ method call fails here instead of at runtime on 1.12.2.
    options.release = 8
    options.encoding = "UTF-8"
}

repositories { mavenCentral() }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
