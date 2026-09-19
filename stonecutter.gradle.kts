import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType

plugins {
    id("dev.kikugie.stonecutter")

    // Declared here and applied by each loader script, so the version is written once and
    // the four of them say `id("me.modmuss50.mod-publish-plugin")` with nothing after it.
    id("me.modmuss50.mod-publish-plugin") version "2.2.0" apply false
}

stonecutter active "1.20.1-forge" /* [SC] DO NOT EDIT */

// ---- publishing ----------------------------------------------------------------------
//
// Modrinth and CurseForge, configured once for every node.
//
// This belongs here rather than in the four loader scripts because it is the one thing
// they do identically — and rather than in a script applied with `apply(from = ...)`,
// which gets its own classpath and so cannot see the plugin's types at all. Only the root
// script can both hold the plugin on its classpath and reach every node.
//
// Every node publishes its own version: five jars become five versions on each site, one
// per (Minecraft version x loader). That is what a player browsing the files list expects,
// and it is the only arrangement where one loader can be re-uploaded without touching the
// others.
//
// Nothing here runs without configuration. A platform whose id is blank is skipped
// entirely, so a fork can tag releases and get jars without holding any credentials.

/** Modrinth accepts the slug or the id; the slug is stable and readable. */
val modrinthProjectId = (findProperty("publish.modrinth_id") as String? ?: "").trim()

/**
 * CurseForge needs the NUMERIC project id, not the slug: uploads go to
 * {@code /api/projects/<id>/upload-file}, which will not take a name.
 */
val curseforgeProjectId = (findProperty("publish.curseforge_id") as String? ?: "").trim()
val curseforgeProjectSlug = (findProperty("publish.curseforge_slug") as String? ?: "").trim()

subprojects {
    plugins.withId("me.modmuss50.mod-publish-plugin") {
        // Deferred until the node script has finished, because this reads its version and
        // archive name, and the plugin is applied in its `plugins {}` block — before
        // either is set. Providers would also work; afterEvaluate says what is going on.
        afterEvaluate { configurePublishing() }
    }
}

fun Project.configurePublishing() {
    // "1.20.1-fabric" -> "1.20.1" and "fabric". Taken from the node name rather than
    // passed in, because the name is already the authority on both.
    val mcVersion = name.substringBeforeLast('-')
    val loader = name.substringAfterLast('-')

    // The Minecraft versions this jar actually runs on, which is not always the one the
    // node is named after. A node may cover a span: the 1.21.1 jar also runs on 1.21,
    // because built against either the remapped classes come out byte-identical.
    val supportedVersions = (findProperty("meta.mc_versions") as String?)
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: listOf(mcVersion)

    val versionsLabel = when (supportedVersions.size) {
        1 -> supportedVersions.first()
        else -> supportedVersions.first() + "-" + supportedVersions.last()
    }

    val modVersion = version.toString()
    val modName = property("mod_name") as String
    val archiveName = the<BasePluginExtension>().archivesName.get()

    val loaderDisplayName = when (loader) {
        "neoforge" -> "NeoForge"
        else -> loader.replaceFirstChar { it.uppercase() }
    }

    /** A pre-release version publishes as one, rather than quietly as stable. */
    val releaseType = when {
        modVersion.contains("alpha", ignoreCase = true) -> ReleaseType.ALPHA
        modVersion.contains("beta", ignoreCase = true) -> ReleaseType.BETA
        modVersion.contains("rc", ignoreCase = true) -> ReleaseType.BETA
        else -> ReleaseType.STABLE
    }

    // Fabric API is a real requirement of this jar — fabric.mod.json lists it under
    // `depends` — and that covers Quilt too, which runs the very same jar. Forge and
    // NeoForge require nothing but their loader.
    val needsFabricApi = loader == "fabric"

    configure<ModPublishExtension> {
        // -Ppublish.dry_run validates everything and uploads nothing.
        dryRun.set(providers.gradleProperty("publish.dry_run").map { true }.orElse(false))

        // The jar by path, rather than through the task that produces it. The three
        // toolchains disagree about that task — Loom ends at `remapJar`, ModDevGradle's
        // legacy Forge support at `reobfJar`, plain NeoForge at `jar` — and only two of
        // those are archive tasks with an `archiveFile` to read. All three land here.
        //
        // Deliberately NOT a task dependency. In CI the jars come from the build jobs that
        // already tested them, so this uploads the exact bytes that passed rather than
        // rebuilding something almost identical. Locally that means `./gradlew build
        // publishMods`; the plugin says plainly when the file is not there.
        file.set(layout.buildDirectory.file("libs/$archiveName-$modVersion.jar"))

        // Unique per node, because both sites key versions by this string and five jars go
        // up under one project.
        version.set("$modVersion+$mcVersion-$loader")
        displayName.set("$modName $modVersion for Minecraft $versionsLabel ($loaderDisplayName)")
        type.set(releaseType)
        modLoaders.add(loader)

        if (loader == "fabric") {
            // One jar, listed under both loaders. There is no Quilt build: Quilt Loader
            // runs Fabric mods through its compatibility layer, and this jar carries
            // nothing Quilt-specific, so a separate one published identical bytes under
            // another name. See settings.gradle.kts.
            modLoaders.add("quilt")
        }

        // Set by the release workflow from the commits since the previous tag. The
        // fallback is a link rather than nothing: a version with an empty changelog reads
        // as a mistake, and the GitHub release always carries the full notes.
        changelog.set(
            providers.environmentVariable("RELEASE_CHANGELOG").orElse(
                "See ${property("mod_sources")}/releases/tag/v$modVersion"
            )
        )

        if (modrinthProjectId.isNotEmpty()) {
            modrinth {
                projectId.set(modrinthProjectId)
                accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
                minecraftVersions.addAll(supportedVersions)
                if (needsFabricApi) {
                    requires("fabric-api")
                }
            }
        }

        if (curseforgeProjectId.isNotEmpty()) {
            curseforge {
                projectId.set(curseforgeProjectId)
                accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
                minecraftVersions.addAll(supportedVersions)

                // CurseForge refuses a file that claims to be for neither side. This mod
                // runs on both: the server is authoritative and required, the client is
                // optional and adds the shop screen and the HUD.
                client.set(true)
                server.set(true)

                if (curseforgeProjectSlug.isNotEmpty()) {
                    // Only used to build links in announcements; the upload uses the id.
                    projectSlug.set(curseforgeProjectSlug)
                }
                if (needsFabricApi) {
                    requires("fabric-api")
                }
            }
        }
    }

    // Says which platforms a publish will actually touch. Without this a missing id looks
    // exactly like a successful upload: the task runs, does nothing, and reports success.
    tasks.named("publishMods") {
        doFirst {
            val targets = buildList {
                if (modrinthProjectId.isNotEmpty()) add("Modrinth")
                if (curseforgeProjectId.isNotEmpty()) add("CurseForge")
            }
            if (targets.isEmpty()) {
                logger.warn(
                    "publishMods: nothing to publish for $mcVersion-$loader. Neither "
                        + "publish.modrinth_id nor publish.curseforge_id is set in "
                        + "gradle.properties."
                )
            } else {
                logger.lifecycle(
                    "publishMods: $mcVersion-$loader -> " + targets.joinToString(" and ")
                )
            }
        }
    }
}
