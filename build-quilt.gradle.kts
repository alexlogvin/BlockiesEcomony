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

sourceSets.main {
    // Reuses the Fabric platform implementation wholesale.
    java.srcDir(rootProject.file("src/fabric/java"))
    resources.srcDir(rootProject.file("src/quilt/resources"))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" }
    maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("deps.parchment_mc")}:${property("deps.parchment")}@zip")
    })

    modImplementation("org.quiltmc:quilt-loader:${property("deps.quilt_loader")}")
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
