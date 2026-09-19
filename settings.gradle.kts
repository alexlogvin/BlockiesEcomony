// Blockies Economy — Stonecutter tree.
//
// Layout: versions/<mc>-<loader>/ , one node per (Minecraft version x loader).
// Each loader gets its OWN build script (build-<loader>.gradle.kts) rather than a
// shared one. This is Stonecutter's recommended "split buildscript" approach: every
// loader declares its own toolchain plugin in its own `plugins {}` block, so Fabric
// Loom and ModDevGradle never share a buildscript classpath.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "blockies-economy"

// Pure-Java core: the price solver, ledger, TOML and formatting. No Minecraft
// dependency, so it is a plain subproject outside the Stonecutter tree.
include("core")

stonecutter {
    kotlinController = true

    create(rootProject) {
        // Pick the build script from the loader suffix of the node name.
        mapBuilds { _, node -> "build-${node.project.substringAfterLast('-')}.gradle.kts" }

        // Node name -> logical version used by //? if >=1.21 conditions.
        // The mapping is required: "1.20.1-forge" would otherwise parse as a
        // pre-release of 1.20.1 and sort BELOW it.
        // 1.21.1-forge is deliberately absent: ModDevGradle 2.0.147 cannot build
        // post-1.20.1 Forge. Its NeoFormRuntime does not know maven.minecraftforge.net
        // and does not declare forge:universal-srg / mcp_config, which Forge 52.x needs.
        // Tracked as M16.3. NeoForge covers the 1.21.1 Forge-side audience meanwhile.
        // No Quilt node. The Quilt jar was built from the same sources AND the same
        // fabric.mod.json as the Fabric one, so the two jars came out byte-identical
        // apart from a manifest line naming the node - Quilt Loader runs Fabric mods
        // through its compatibility layer, and QSL, which would have justified a real
        // Quilt build, was retired in Dec 2025. The Fabric jar is published listing
        // Quilt as a supported loader instead of shipping the same bytes twice.
        versions(
            "1.20.1-fabric"   to "1.20.1",
            "1.20.1-forge"    to "1.20.1",
            "1.20.4-fabric"   to "1.20.4",
            "1.20.6-fabric"   to "1.20.6",
            "1.20.6-neoforge" to "1.20.6",
            "1.21.1-fabric"   to "1.21.1",
            "1.21.1-neoforge" to "1.21.1",
            "1.21.4-fabric"   to "1.21.4",
            "1.21.4-neoforge" to "1.21.4",
            "1.21.5-fabric"   to "1.21.5",
            "1.21.5-neoforge" to "1.21.5",
            "1.21.8-fabric"   to "1.21.8",
            "1.21.8-neoforge" to "1.21.8",
            "1.21.10-fabric"  to "1.21.10",
            "1.21.10-neoforge" to "1.21.10",
            "1.21.11-fabric"  to "1.21.11",
            "1.21.11-neoforge" to "1.21.11",
        )
    }
}
