// Minecraft <version> / Fabric — runs for every versions/<mc>-fabric node.
//
// Uses `fabric-loom-remap`: the remapping Loom variant, required for obfuscated
// Minecraft (everything up to and including 1.21.11). Unobfuscated 26.x nodes will
// use `fabric-loom-no-remap` instead, which is why this lives in its own script.

plugins {
    id("java")
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
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
val loader = "fabric"
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


// -PquickPlay=<world folder> drops straight into a save instead of the title screen.
// Worth having: the client half of this mod — the HUD, the shop screen, the price sync —
// only runs once a world is loaded, so testing it by hand otherwise means clicking through
// two menus every single time. The NeoForge and Forge scripts carry the same option, so
// one command tests any node.
if (project.hasProperty("quickPlay")) {
    loom.runs.named("client") {
        programArgs("--quickPlaySingleplayer", project.property("quickPlay") as String)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
    // Mod Menu, for the Config button in its mod list. compileOnly only (below): the
    // mod never requires it, and does nothing differently when it is absent.
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    // Mojmap keeps the mapping namespace identical to the NeoForge/Forge nodes, so
    // the shared source in src/main compiles unchanged across every loader.
    // Parchment layers parameter names on top, which Mojmap lacks.
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("deps.parchment_mc")}:${property("deps.parchment")}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Fabric and Quilt have no mod-list config hook of their own; Mod Menu is what
    // players install for one, and it asks for the screen through an entrypoint whose
    // interface has to be on the compile classpath. compileOnly keeps it out of the jar
    // and out of the dependency list, so a player without Mod Menu loses the button and
    // nothing else; the Fabric entrypoint class is simply never loaded.
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

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
    "mc_version_range" to property("meta.mc_range_semver"),
    "pack_format" to property("meta.pack_format"),
    "java_level" to javaLevel,
)

tasks.processResources {
    inputs.properties(metadataProps)
    filesMatching(listOf("fabric.mod.json", "pack.mcmeta",
            "blockies_economy.mixins.json")) {
        expand(metadataProps)
    }
}