// Minecraft <version> / NeoForge — runs for every versions/<mc>-neoforge node.
// NeoForge does not exist before 1.20.2, so there is no 1.20.1-neoforge node.

plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.147"
}

val mcVersion = stonecutter.current.version
val loader = "neoforge"
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

neoForge {
    version = property("deps.neoforge") as String
    parchment {
        minecraftVersion = property("deps.parchment_mc") as String
        mappingsVersion = property("deps.parchment") as String
    }
}

sourceSets.main {
    // Shared loader code.
    java.srcDir(rootProject.file("src/$loader/java"))
    resources.srcDir(rootProject.file("src/$loader/resources"))

    // Per-Minecraft-version loader code, for the few classes that genuinely fork
    // between versions (a Mixin whose target signature changed, say). Preferred over
    // Stonecutter comment gates when a whole class differs rather than a line.
    // Shared code that forks by Minecraft version, e.g. the recipe adapter.
    val sharedVersioned = rootProject.file("src/main-${mcVersion}/java")
    if (sharedVersioned.isDirectory) {
        java.srcDir(sharedVersioned)
    }

    val versioned = rootProject.file("src/$loader-${mcVersion}/java")
    if (versioned.isDirectory) {
        java.srcDir(versioned)
    }
}

repositories {
    mavenCentral()
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
