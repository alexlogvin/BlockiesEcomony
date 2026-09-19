// Minecraft <version> / Quilt — runs for every versions/<mc>-quilt node.
//
// Quilt retired QSL, Quilt Kotlin Libraries and Quilted Fabric API in Dec 2025, so
// there is no Quilt-native API left to target. A Quilt build is therefore a Fabric
// build that additionally ships quilt.mod.json and runs on Quilt Loader. It reuses
// src/fabric verbatim.

plugins {
    id("java")
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val mcVersion = stonecutter.current.version
val loader = "quilt"
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

// Compiling src/fabric against Quilt Loader emits deprecation warnings for
// net.fabricmc.api.ModInitializer and FabricLoader. That is expected and correct to
// ignore: Quilt marks its Fabric compatibility layer deprecated in favour of its own
// API, but Quilt Standard Libraries and Quilted Fabric API were retired in Dec 2025,
// so there is no Quilt-native target left. The compatibility layer is the supported
// path for a Quilt build now. Do not "fix" these by switching to org.quiltmc APIs.

sourceSets.main {
    // Shared loader code.
    java.srcDir(rootProject.file("src/fabric/java"))
    // Quilt reads fabric.mod.json through its compatibility layer. Shipping
    // quilt.mod.json instead would require a Quilt-native ModInitializer from QSL,
    // which no longer exists.
    resources.srcDir(rootProject.file("src/fabric/resources"))

    // Per-Minecraft-version loader code, for the few classes that genuinely fork
    // between versions (a Mixin whose target signature changed, say). Preferred over
    // Stonecutter comment gates when a whole class differs rather than a line.
    // Shared code that forks by Minecraft version, e.g. the recipe adapter.
    val sharedVersioned = rootProject.file("src/main-${mcVersion}/java")
    if (sharedVersioned.isDirectory) {
        java.srcDir(sharedVersioned)
    }

    val versioned = rootProject.file("src/fabric-${mcVersion}/java")
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
    maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
    // Mod Menu, for the Config button in its mod list. compileOnly only (below): the
    // mod never requires it, and does nothing differently when it is absent.
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("deps.parchment_mc")}:${property("deps.parchment")}@zip")
    })

    // Compiles against Fabric Loader; Quilt Loader runs it through its compatibility
    // layer. quilt.mod.json declares the Quilt Loader requirement instead. Quilt has
    // published no Mixin artifact of its own since QSL was retired.
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
