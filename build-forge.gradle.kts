// Minecraft <version> / Forge — runs for every versions/<mc>-forge node.
//
// Uses NeoForged's ModDevGradle "legacyforge" variant rather than ForgeGradle 6.
// Both `net.neoforged.moddev` and `net.neoforged.moddev.legacyforge` resolve to the
// SAME artifact (net.neoforged:moddev-gradle), so Forge and NeoForge share one
// toolchain dependency instead of adding a third, Gradle-version-fussy plugin.

plugins {
    id("java")
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
    // Publishing to Modrinth and CurseForge. The version and all of the configuration
    // live in stonecutter.gradle.kts, which is the one script every node shares.
    id("me.modmuss50.mod-publish-plugin")
}

apply(from = rootProject.file("gradle/node-identity.gradle.kts"))

val mcVersion = stonecutter.current.version

// What the jar is called and which source era it compiles. A node can cover several
// Minecraft versions, so neither follows from mcVersion alone; see gradle/node-identity.
val versionsLabel = extra["versionsLabel"] as String
val srcEra = extra["srcEra"] as String
val packSupportedFormats = extra["packSupportedFormats"] as String
val loader = "forge"
val javaLevel = if (stonecutter.current.parsed >= "1.20.5") 21 else 17

version = property("mod_version") as String
base.archivesName = "${property("mod_archive_name")}-$versionsLabel-$loader"

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
    // Parchment adds Mojmap the one thing it lacks, parameter names, and nothing else -
    // so a node without it compiles identically and only reads worse in an IDE. It is
    // optional because ParchmentMC has no stable release for several of the versions this
    // repo targets, and pinning a nightly snapshot would make those builds non-reproducible.
    if (project.findProperty("deps.parchment") != null) {
        parchment {
            minecraftVersion = property("deps.parchment_mc") as String
            mappingsVersion = property("deps.parchment") as String
        }
    }

    // Without this the mod is invisible in dev runs: ModDevGradle only puts a source
    // set on the mod path when it is declared as a mod here.
    mods {
        create(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    // ModDevGradle does not create run tasks on its own, unlike Loom.
    runs {
        create("client") {
            client()
            gameDirectory = file("run")
            // -PquickPlay=<world folder> drops straight into a save instead of the title
            // screen. Worth having: the client half of this mod — the HUD, the shop screen,
            // the price sync — only runs once a world is loaded, so testing it by hand
            // otherwise means clicking through two menus every single time.
            if (project.hasProperty("quickPlay")) {
                programArguments.addAll("--quickPlaySingleplayer",
                        project.property("quickPlay") as String)
            }
        }
        create("server") {
            server()
            gameDirectory = file("run")
        }
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
    val sharedVersioned = rootProject.file("src/main-${srcEra}/java")
    if (sharedVersioned.isDirectory) {
        java.srcDir(sharedVersioned)
    }

    val versioned = rootProject.file("src/$loader-${srcEra}/java")
    if (versioned.isDirectory) {
        java.srcDir(versioned)
    }

}

repositories {
    mavenCentral()
    // Forge publishes the universal-srg artifact ModDevGradle needs on 1.21.1+.
    maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
}

dependencies {
    implementation(project(":core"))

    // Dev runs load the compiled classes directory rather than the jar, so :core is not
    // merged in yet at that point. Without this the mod loads and then dies on a
    // ClassNotFoundException the moment it reads its config.
    "additionalRuntimeClasspath"(project(":core"))
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
    "javafml_range" to property("meta.javafml_range"),
    "pack_format" to property("meta.pack_format"),
    "pack_supported_formats" to packSupportedFormats,
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