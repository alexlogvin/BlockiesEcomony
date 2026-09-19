// Who a node is: which Minecraft versions its jar covers, what the file is called, and
// which source era it compiles.
//
// Applied by all three loader scripts and read back by the publishing config, so the four
// places that need these answers cannot drift apart. A node may cover several Minecraft
// versions — see `meta.mc_versions` in versions/<node>/gradle.properties — and then the
// node's own name is no longer the whole truth about it.
//
// Deliberately touches nothing but plain properties. A script applied with
// `apply(from = ...)` gets its own classpath and cannot see types the applying script's
// plugins bring in, which is why the publishing config had to move out of one of these.

// "1.21.1-fabric" -> "1.21.1". Taken from the node name rather than from the Stonecutter
// extension, which this script's classpath cannot see.
val nodeVersion = name.substringBeforeLast('-')

/** Every Minecraft version this node's jar runs on, lowest first. */
val supportedVersions: List<String> = (findProperty("meta.mc_versions") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf(nodeVersion)

/**
 * What the jar calls itself: "1.21.1" for one version, "1.21-1.21.1" for a span.
 *
 * <p>A span has to say so. A file named after a single version is what a player reads as
 * the list of versions it supports, and they are not going to open the jar to find out
 * otherwise.
 */
val versionsLabel: String = when (supportedVersions.size) {
    1 -> supportedVersions.first()
    else -> "${supportedVersions.first()}-${supportedVersions.last()}"
}

/**
 * Which `src/main-<era>` and `src/<loader>-<era>` directories this node compiles.
 *
 * <p>Keyed by API era rather than by node, because two nodes can need exactly the same
 * source: 1.20.5 and 1.21 differ in Fabric API's HUD callback, which is a lambda whose
 * type is inferred, so one source file compiles correctly against both and only the
 * emitted bytecode differs. Named after the era's earliest Minecraft version.
 */
val srcEra: String = (findProperty("meta.src_era") as String?) ?: nodeVersion

extra["supportedVersions"] = supportedVersions
extra["versionsLabel"] = versionsLabel
extra["srcEra"] = srcEra

/**
 * The `supported_formats` line for pack.mcmeta, or nothing.
 *
 * <p>A span can cross a resource-pack format bump — 1.20.2 is 18 and 1.20.4 is 22 — and a
 * pack declaring one format on a game expecting the other is reported to the player as
 * out of date. `supported_formats` is the vanilla way to say "both", and has been
 * understood since 1.20.2. Nodes covering one format leave it out entirely.
 */
val packSupportedFormats: String = (findProperty("meta.pack_formats") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?.let { ",\n    \"supported_formats\": [${it.joinToString(", ")}]" }
    ?: ""

extra["packSupportedFormats"] = packSupportedFormats
