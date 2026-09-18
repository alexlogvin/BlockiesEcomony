// Minecraft <version> / Fabric — runs for every versions/<mc>-fabric node.
//
// Uses `fabric-loom-remap`: the remapping Loom variant, required for obfuscated
// Minecraft (everything up to and including 1.21.11). Unobfuscated 26.x nodes will
// use `fabric-loom-no-remap` instead, which is why this lives in its own script.

plugins {
    id("java")
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val mcVersion = stonecutter.current.version
val loader = "fabric"
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

sourceSets.main {
    java.srcDir(rootProject.file("src/$loader/java"))
    resources.srcDir(rootProject.file("src/$loader/resources"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
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
    filesMatching(listOf("fabric.mod.json", "quilt.mod.json", "pack.mcmeta")) {
        expand(metadataProps)
    }
}
