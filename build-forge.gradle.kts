// Minecraft <version> / Forge — runs for every versions/<mc>-forge node.
//
// Uses NeoForged's ModDevGradle "legacyforge" variant rather than ForgeGradle 6.
// Both `net.neoforged.moddev` and `net.neoforged.moddev.legacyforge` resolve to the
// SAME artifact (net.neoforged:moddev-gradle), so Forge and NeoForge share one
// toolchain dependency instead of adding a third, Gradle-version-fussy plugin.

plugins {
    id("java")
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
}

val mcVersion = stonecutter.current.version
val loader = "forge"
val javaLevel = if (stonecutter.current.parsed >= "1.20.5") 21 else 17

version = property("mod_version") as String
base.archivesName = "${property("mod_id")}-$mcVersion-$loader"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaLevel)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaLevel
    options.encoding = "UTF-8"
}

legacyForge {
    version = property("deps.forge") as String
    parchment {
        minecraftVersion = property("deps.parchment_mc") as String
        mappingsVersion = property("deps.parchment") as String
    }
}

sourceSets.main {
    java.srcDir(rootProject.file("src/$loader/java"))
    resources.srcDir(rootProject.file("src/$loader/resources"))
}

repositories {
    mavenCentral()
    // Forge publishes the universal-srg artifact ModDevGradle needs on 1.21.1+.
    maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
}

dependencies {
    implementation(project(":core"))
}

// :core is plain Java with no Minecraft dependency, so its classes are merged into
// the mod jar rather than shipped as a separate artifact users must install.
//
// Resolved through a dedicated configuration rather than by reaching into
// project(":core").sourceSets: that only works if :core happens to be configured
// first, which is not guaranteed once several nodes exist.
val coreBundle = configurations.create("coreBundle") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    coreBundle(project(":core"))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(coreBundle.elements.map { files -> files.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
}


// Mod metadata is templated at build time so the modid, version and supported
// Minecraft range live in exactly one place (gradle.properties + the node's file).
val metadataProps = mapOf(
    "mod_id" to property("mod_id"),
    "mod_name" to property("mod_name"),
    "mod_version" to property("mod_version"),
    "mod_group" to property("mod_group"),
    "mod_author" to property("mod_author"),
    "mod_license" to property("mod_license"),
    "mod_description" to property("mod_description"),
    "mod_homepage" to property("mod_homepage"),
    "mod_sources" to property("mod_sources"),
    "mod_issues" to property("mod_issues"),
    "mc_version_range" to property("meta.mc_range_maven"),
    "forge_loader_range" to property("meta.loader_range"),
    "neoforge_loader_range" to property("meta.loader_range"),
    "pack_format" to property("meta.pack_format"),
    "java_level" to javaLevel,
)

tasks.processResources {
    // Registering the inputs keeps Gradle's up-to-date check honest; without it a
    // version bump silently reuses the previous run's output.
    inputs.properties(metadataProps)
    filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(metadataProps)
    }
}
