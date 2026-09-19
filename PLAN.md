# Blockies Economy — Implementation Plan

## Context

Build a cross-platform Minecraft economy mod (`blockies_economy`) from an empty repo: virtual currency "Blockies", automatic price derivation from recipe trees, a vanilla-styled Shop GUI, a balance HUD, advancement payouts, commands, and recipe-viewer integration — across multiple Minecraft versions and all four mod loaders.

Research during planning overturned three premises of the original brief, and the plan below reflects the corrections:

1. **Minecraft moved to year-versioning.** Latest is **26.3** (2026-09-15); 26.1/26.2 precede it. All 26.x are **unobfuscated** (no `remapJar` step) and require **Java 25**.
2. **Architectury is dropped.** The user requires *no runtime dependency on other mods*, and Architectury API is itself an installed mod. Dropping it also removes Architectury's own walls: it ships **no Forge artifact past 1.20.4** and **no Quilt past 1.20.1**. A hand-written `Platform` SPI resolved via `ServiceLoader` replaces it and makes Forge-on-1.21+ and native Quilt reachable.
3. **Quilt's API stack (QSL/QKL/Quilted Fabric API) was retired** in Dec 2025 / 26.1. Quilt support means a `quilt.mod.json` on an otherwise Fabric-shaped jar.

Verified toolchain state: JDKs 8, 17, 21, 25, 26 (Temurin) are installed; network access to Mojang, Fabric, NeoForge, Forge and Gradle Maven repos works.

### Confirmed decisions

| Area | Decision |
|---|---|
| Platform abstraction | Own `Platform` SPI + `ServiceLoader`. **No Architectury.** Fabric API *is* an accepted dependency for the Fabric/Quilt jars. |
| Multi-version | **Stonecutter 0.9.8** (build-time Gradle plugin, not a runtime dep), `versions/<mc>/<loader>/` layout |
| v1 matrix | **1.20.1** (fabric, forge, quilt) and **1.21.1** (fabric, neoforge, forge) |
| Later | 1.21.11, 26.x forward; then legacy 1.12.2–1.19.4 as a separate era tree |
| Currency | `long`, whole Blockies. Solver works in **high-precision micro-units**; rounding to whole Blockies happens only at transaction/display time |
| Config format | TOML with comments, via a **hand-rolled minimal parser in `core`** (no Night Config, no shading) |
| Price precedence | `prices.toml` (admin) > `prices.d/*.toml` drop-ins > datapack `prices.json` > Java API > **tag rules** > recipe-derived > unpriced (absent from shop) |
| Tag rules | Price by item tag, e.g. `"#c:ingots" = 90`. One rule prices hundreds of modded items because modded ores/ingots/gems/woods are near-universally tagged — the main lever on modded support |
| Special recipes | Recipes with no resolvable ingredients or an empty result (armor dyeing, firework crafting, map/book cloning, tipped arrows, shulker colouring, repair) are **skipped and logged**. Items reachable only that way need a hand-authored root price or are absent |
| Solver | Cheapest recipe wins; cheapest ingredient alternative wins; multi-output divides by count; cycles resolved by iterative relaxation to a fixed point |
| Seed data | Hand-author only **non-craftable root items**; derive the rest and review the output. Popular-mod price packs live in `price-packs/`, published on GitHub, **not bundled**. |
| Price scale | Wide spread, ~1:500. Floor 5 (dirt/sand/cobble/stick), log 40 → plank 10 → stick 5, diamond ~2500, deep endgame beyond |
| Advancements | prize = `tree_base × 1.5^depth`, base and exponent configurable globally and per-tree, per-advancement override supported |
| Balance | starts at 0 (configurable), survives death, configurable death penalty (default 0) |
| Sell spread | `sell = buy × 0.75` (configurable) |
| Recipe multipliers | Recipe types needing no extra resources (crafting, stonecutting) pinned at **1.0**. Everything else — fuel/extra-resource types *and* all modded types — falls through to `default_recipe_multiplier` = **1.3**. Common overridables (smelting, blasting, smoking, campfire, smithing, brewing) are written into the config **commented out**, as documentation of what admins can set. **Hard invariant: every multiplier must be < `1 / sell_multiplier`** or buy→craft→sell becomes an infinite-money loop |
| Client sync | Full table on join, compressed + content-hashed, disk-cached, delta/no-op on reconnect |
| Recipe viewers | Capability ladder per viewer: buttons → else open Shop UI at that item → else show price only → else skip |
| Languages | `en_us`, `uk_ua` |
| Admin | OP level 2; `/shop price <item> <price>` writes through to `prices.toml` with a provenance comment |
| Sides | **Server required and authoritative, client optional.** Without the client jar players keep every command and lose only the GUI/HUD; vanilla clients can still join |
| Item identity | Priced by item id only. Any stack with **non-default components** — enchantments, potion contents, custom names, container contents — is refused for sell and never listed. Closes the filled-shulker exploit |
| Identity | Package/group `com.alexlogvin.blockieseconomy`, **MIT**, version **0.1.0** semver, published to Modrinth + CurseForge from CI |
| Price calc | Runs **async** off the server thread; trades rejected by a "prices not ready" guard until it completes. `generated/prices.toml` carries a **schema version** so solver changes invalidate stale caches |

### Economy invariants (must hold, and must be tested)

The mod must never permit a closed loop that generates money. Two leaks exist:

1. **Craft arbitrage.** Buy ingredients for `C`, craft, sell the output for `C × M × sell_multiplier`. Profitable whenever `M > 1 / sell_multiplier`. With the default `sell_multiplier = 0.75` the ceiling is **1.333**, so `default_recipe_multiplier = 1.3` is safe (0.975 per cycle) — but **the 1.5 for furnace/brewing in the original brief is exploitable** (raw iron 60 → ingot 90 → sells 67.50). Furnace and brewing therefore take the 1.3 default rather than a raised value of their own.
   - Config emits the rule as a comment: `max_safe_multiplier = 1 / sell_multiplier`
   - Startup validation **rejects and clamps** any configured multiplier violating it, with a loud log warning.
2. **Rounding arbitrage.** With whole-Blockies currency, round-up plus divide-by-output can inflate cheap items: a true value of 0.2 ceiled to 1 makes `buy 1 input → craft 9 outputs → sell 6.75` profitable. Defences: solve in micro-units and round only at the edge, plus an explicit post-solve validation pass asserting `sum(output_sell) ≤ sum(input_buy)` for **every** recipe, logging violations and optionally auto-correcting.

---

## Target layout

```
/                          gitignore, gitattributes, LICENSE, README, PLAN.md, CONTRIBUTING
/gradle/                   wrapper + libs.versions.toml
/buildSrc/                 convention plugins: loader-fabric, loader-forge, loader-neoforge, loader-quilt
/core/                     PURE JAVA, zero Minecraft deps, Java 8 bytecode
/src/main/java/            shared MC code, Stonecutter-gated (//? if >=1.21)
/src/main/resources/       lang, textures, templated metadata
/versions/1.20.1/{fabric,forge,quilt}/
/versions/1.21.1/{fabric,neoforge,forge}/
/price-packs/              per-mod prices.d/ drop-ins, published on GitHub, not bundled
/scripts/                  build-release.ps1 / .sh
/docs/                     GitHub Pages: admin guide, mod-dev price API docs
/.github/workflows/        matrix CI
```

**Why `core` matters:** roughly 80% of this mod touches no Minecraft API. Putting the solver, ledger, TOML, config schema and formatting in a dependency-free Java 8 library makes the eventual 1.12.2 port an adapter-writing exercise rather than a rewrite, and makes all of it unit-testable without a Minecraft runtime.

---

## Milestones

### M0 — Repo scaffolding
- [x] 0.1 `PLAN.md` at repo root (execution-tracked copy of these milestones)
- [x] 0.2 `.gitignore` (Gradle, IDEA/Eclipse/VSCode, `run/`, `.stonecutter`, loader caches, `*.log`), **MIT `LICENSE`**, `README.md`; group/package `com.alexlogvin.blockieseconomy`, version `0.1.0`
- [x] 0.3 Gradle wrapper; `gradle.properties` with `org.gradle.java.installations` pointing at the installed JDKs
- [x] 0.4 `gradle/libs.versions.toml` version catalogue — pin every loader/plugin version after checking Maven live

### M1 — Build system (no Architectury) — **riskiest milestone, ordered worst-first**

Stonecutter runs **one central build script** across every (version × loader) node, but we need **three different Gradle toolchains** — Loom, ModDevGradle, and a legacy Forge plugin — whose extensions (`loom { }` vs `neoForge { }`) exist only on some nodes. Mitigation: all toolchain config lives in **buildSrc convention plugins**, one per loader, and the central script is a thin dispatcher (`apply(plugin = "blockies.loader-$branch")`). This is the configuration Friends&Foes runs in production with Stonecutter.

Forge 1.20.1 is the unproven third toolchain, so **it is built first** — if it cannot coexist with Loom in one tree we learn on day one, not at the end of M1.

- [x] **1.0 Toolchain spike — timeboxed, throwaway, before any real work.** In the scratchpad, stand up a minimal Stonecutter tree (one node, three branches) applying legacyforge + ModDevGradle + Loom, and confirm each branch builds a trivial jar. **Escalation ladder if it fails, in order:**
  1. Forge 1.20.1 moves to an **isolated included build** with its own plugin classpath
  2. Stonecutter handles only the *version* dimension; loaders become **plain Gradle subprojects** (standard MultiLoader-Template, zero novelty)
  3. Forge 1.20.1 **drops to M16**; v1 ships Fabric + NeoForge + Quilt on the proven two-toolchain setup

  Report which rung we landed on before continuing.
- [x] 1.1 `settings.gradle.kts`: Stonecutter 0.9.8 + foojay-resolver 1.0.0; nodes `1.20.1`, `1.21.1`; branches `forge`, `neoforge`, `fabric`, `quilt`
- [x] 1.2 `buildSrc` skeleton + thin dispatcher in the central script
- [x] 1.3 **Forge 1.20.1 first** — `blockies.loader-forge` via ModDevGradle's `legacyforge` plugin (preferred over ForgeGradle 6 for Gradle compatibility; fall back to ForgeGradle, or to an isolated included build, if it conflicts with Loom on the classpath). **Gate: a Forge jar builds before anything else starts.**
- [x] 1.4 `blockies.loader-neoforge` (ModDevGradle), then `blockies.loader-fabric` (Loom), then `blockies.loader-quilt` (Loom + `quilt.mod.json`)
- [x] 1.5 Java level per node: `>=26.1 → 25`, `>=1.20.5 → 21`, else `17`, via `options.release`
- [x] 1.6 `core` as a plain `java-library` at `release = 8`, classes merged into every loader jar (no shading, no runtime dep)
- [x] 1.7 Shared-source wiring: each loader project compiles `/src/main/java` plus its own — the MultiLoader-Template `commonJava`/`commonResources` artifact pattern, not shadow
- [x] 1.8 `processResources` templating for `fabric.mod.json`, `quilt.mod.json`, `META-INF/mods.toml`, `META-INF/neoforge.mods.toml`, `pack.mcmeta` (`pack_format` varies per MC version). Register `inputs.properties` to avoid stale-output bugs; JSON-escape the JSON variants
- [x] 1.9 Verify: `./gradlew build` produces all 6 v1 jars
- **Checkpoint: `./gradlew build` green before any feature work.**


#### M1 outcome (recorded after execution)

**Landed on rung 0 — no fallback needed, but the architecture changed for the better.**

Stonecutter's own recommended *split buildscript* approach replaced the planned buildSrc
dispatcher. Each loader gets its own `build-<loader>.gradle.kts`, selected by `mapBuilds`
from the node-name suffix, so every loader declares its toolchain in its own `plugins {}`
block. The shared-buildscript-classpath risk that M1.0 existed to test **does not arise at all**.

Two supporting findings:
- `net.neoforged.moddev` and `net.neoforged.moddev.legacyforge` resolve to the **same artifact**
  (`net.neoforged:moddev-gradle:2.0.147`), so Forge and NeoForge are one toolchain, not two.
- Layout is flat — `versions/<mc>-<loader>/` — which is what was asked for and what Stonecutter
  natively produces. Loader-specific sources live in `src/<loader>/`, shared code in `src/main/`.

**Daemon JVM is Java 25, not 21.** Fabric Loom 1.18.2 refuses to load on anything lower. This is
forward-compatible: 26.x nodes will require Java 25 regardless, and per-node compile levels are
still 17/21 via toolchains. Pinned in `gradle/gradle-daemon-jvm.properties`, so a wrong or stale
`JAVA_HOME` cannot change it.

**`versions/1.21.1-forge` was dropped from v1.** ModDevGradle 2.0.147 cannot build post-1.20.1
Forge: its NeoFormRuntime resolves from its own repository list (NeoForged Maven + local `.m2`
only), never learns `maven.minecraftforge.net`, and does not declare `forge:universal-srg` or
`mcp_config` — both of which Forge 52.x requires. Injecting them into the NFRT configurations
breaks manifest evaluation instead. This is an upstream limitation, not a defect in this build.
Deferred to **M16.3**, where the plan already placed Forge-on-1.21.x. NeoForge covers that
audience in the meantime.

**Shipping matrix: 4 jars** — 1.20.1 fabric/forge, 1.21.1 fabric/neoforge. The Fabric jar
is published listing Quilt as a supported loader; there is no separate Quilt build, because
it came out byte-identical to the Fabric one.

### M2 — `core`: pure-Java foundation (no Minecraft)
- [x] 2.1 Minimal TOML reader/writer with **comment preservation** — tables, string/int/bool/float, arrays, inline comments. Round-trip tested.
- [x] 2.2 Config schema objects + defaults + a commented-emit path so generated files are self-documenting
- [x] 2.3 `Money` — `long` arithmetic, overflow-safe add/subtract, **micro-unit internal precision with rounding only at the edge**, smart formatting (`3.0K B`, `1.2M B`) and full form. **Suffixes and decimal separator come from translation keys**, not hardcoded, so `uk_ua` can render `тис.`/`млн`
- [x] 2.4 `PriceGraph` / `PriceSolver` over abstract `RecipeView` + `IngredientView` interfaces (no MC types): cheapest-recipe, cheapest-alternative, divide-by-output, iterative cycle relaxation with a pass cap and convergence logging. Solves in micro-units so divide-by-output does not accumulate round-up error
- [x] 2.5 `Ledger` — balances by UUID, transaction validation, rate limiting, result types
- [x] 2.6 `AdvancementPrizeCalculator` — `base × exponent^depth`, per-tree and per-advancement overrides
- [x] 2.7 JUnit tests for solver (incl. the ingot↔nugget cycle), money formatting, TOML round-trip
- **Checkpoint: `./gradlew :core:test` green. This milestone is fully testable without Minecraft.**


#### M2 outcome (recorded after execution)

**91 tests, all passing, with no Minecraft runtime involved.** `:core` compiles at Java 8
bytecode and holds the solver, ledger, TOML, config schema and money formatting.

Two design points worth recording, both found by tests rather than by review:

- **Per-output division rounds up.** Truncating it let a cycle shave micro-units each lap,
  so `ingot -> 9 nuggets -> ingot` came out fractionally cheaper than smelting and won the
  `min`. The displayed price was identical, but a round trip must never be able to reduce a
  price at all. `ceilDiv` closes it.
- **A recipe whose only input is its own output is skipped**, otherwise a repair-style
  recipe pins its own value.

Rounding rule, enforced and tested end to end: **buying rounds up, selling rounds down.**
Together with the 1.3 default multiplier this makes every closed loop lossy — verified
against a full vanilla-shaped chain including the iron block/unblock dupe loop.

### M3 — Platform SPI
- [x] 3.1 Define `Platform` interfaces in `/src/main/java`: config dir, mod-loaded query, environment, keybind registration, HUD render hook, command registration, advancement-earned hook, reload listener, networking, player inventory ops
- [x] 3.2 `Services.load(...)` ServiceLoader resolver + `META-INF/services` entries per loader
- [x] 3.3 Fabric impl (Fabric API: `CommandRegistrationCallback`, `KeyBindingHelper`, `HudRenderCallback`, `ServerLifecycleEvents`, networking) + **Mixin on `PlayerAdvancements#award`** — Fabric API has no advancement event
- [x] 3.4 NeoForge impl (`RegisterCommandsEvent`, `RegisterKeyMappingsEvent`, `RenderGuiLayerEvent`, `AdvancementEvent.AdvancementEarnedEvent`, `AddReloadListenerEvent`)
- [x] 3.5 Forge 1.20.1 impl (same shapes, `AdvancementEvent.AdvancementEarnEvent`)
- [x] 3.6 Quilt impl — Fabric impl reused, `quilt.mod.json` only
- [x] 3.7 Version gates for the known 1.20.1↔1.21.1 deltas, isolated to single lines where possible:
  - HUD: `renderHud(GuiGraphics, float)` vs `Gui#render(GuiGraphics, DeltaTracker)`
  - Recipes: `Recipe#getId()` vs `RecipeHolder<T>` record; `getResultItem(RegistryAccess)` vs `(HolderLookup.Provider)`
  - SavedData: `save(CompoundTag)` vs `save(CompoundTag, HolderLookup.Provider)`; `SavedData.Factory` exists only 1.20.5+
  - Networking: raw `FriendlyByteBuf` + `ResourceLocation` vs `CustomPacketPayload` + `StreamCodec`


#### M3 outcome (recorded after execution)

Server-side SPI complete and compiling against every loader's real API:
`Platform` (config dir, mod queries, environment, version) and `ServerEvents`
(lifecycle, datapack reload, join/leave/death, advancements, commands), resolved
via `ServiceLoader` with one provider per loader jar.

**Advancements needed a Mixin on Fabric** — Fabric API has no event for them. Its target
signature changed between versions (`award(Advancement, String)` on 1.20.1,
`award(AdvancementHolder, String)` on 1.21.1), so rather than gate it with Stonecutter
comments the build gained **per-Minecraft-version loader source dirs**: `src/<loader>-<mc>/java`,
added to the source set only when present. Verified in the jars — 1.20.1 ships the
`Advancement` variant at `JAVA_17`, 1.21.1 the `AdvancementHolder` variant at `JAVA_21`.
Stonecutter comment gates remain the tool for line-level differences; this is for whole
classes that fork.

**Quilt needs no build of its own.** QSL/QFAPI were retired, so a Quilt build had no
Quilt-native API to target and was simply the Fabric sources compiled against Fabric Loader
with the same `fabric.mod.json` - the two jars came out identical but for a manifest line
naming the Stonecutter node. The node was removed and the Fabric jar is published listing
Quilt among its loaders. Reinstate it only if Quilt ships an API worth targeting.

**Deferred by design:** the client-side SPI — keybinds, HUD render hook, networking and
inventory operations — lands with M8/M9/M10, where it can be written against real usage
rather than guessed at in the abstract.

### M4 — Config files

Layout under `config/blockies_economy/` — the folder already names the mod, so filenames stay short; machine-written output is quarantined in `generated/` so admins cannot lose edits to a regeneration:

```
server.toml            client.toml            prices.toml
advancements.toml      whitelist.toml
prices.d/              drop-in add-ons, e.g. create.toml
generated/prices.toml  machine-written, header says DO NOT EDIT
```

- [x] 4.1 Directory layout + first-run generation
- [x] 4.2 `server.toml`: sell multiplier, recipe-type multiplier table, starting balance, death penalty, advancement base/exponent, rate limits, transaction-log flag, admin permission level. Clamps invariant violations at startup with a loud warning. The multiplier block is shaped as:

```toml
# Recipe-type multipliers, applied to the sum of ingredient prices.
#
# GOOD RULE FORMULA: every multiplier must stay below 1 / sell_multiplier.
#   At the default sell_multiplier = 0.75 the ceiling is 1.333.
#   Above it, players can buy ingredients, craft, and sell at a profit
#   forever. Values over the ceiling are clamped at startup.

# Recipe types needing no extra resources -> no markup.
"minecraft:crafting"     = 1.0
"minecraft:stonecutting" = 1.0

# Types consuming fuel or extra resources use default_recipe_multiplier.
# Uncomment any line to override it individually.
#"minecraft:smelting"         = 1.3
#"minecraft:blasting"         = 1.3
#"minecraft:smoking"          = 1.3
#"minecraft:campfire_cooking" = 1.3
#"minecraft:smithing"         = 1.3
#"minecraft:brewing"          = 1.3

# Applies to every recipe type not listed above, including modded ones.
default_recipe_multiplier = 1.3
```
- [x] 4.3 `client.toml`: HUD anchor (9 positions), x/y offset, shown/hidden, cobblestone-icon toggle, favorites
- [x] 4.4 `prices.toml` — **roots only.** Hand-author the ~150–250 *non-craftable* vanilla items (raw ores, mob drops, logs, loot-only). Everything craftable is derived. Wide ~1:500 spread anchored at floor 5 / log 40 / diamond ~2500. Empty value `""` = blacklisted
- [x] 4.5 `advancements.toml` — separate from item prices; different editors, different cadence
- [x] 4.6 `whitelist.toml` — empty means no whitelist applies
- [x] 4.7 Migration-safe reload; validation errors go to the server log, never a crash

### M5 — Price engine (Minecraft side)
- [x] 5.1 `RecipeView`/`IngredientView` adapters over the real `RecipeManager`, version-gated
- [x] 5.2 Datapack price source: read from the server resource manager at build time (not a registered reload listener — the rebuild already runs after a reload, and listener ordering against the recipe manager is exactly the kind of thing that fails silently) reading `data/<ns>/blockies_economy/prices.json` from every pack and mod jar
- [x] 5.3 Java API surface (`BlockiesEconomyAPI`) + ServiceLoader entrypoint for mod-dev registrations
- [x] 5.4 Full build on server start and datapack reload, writing generated/prices.toml with a schema version; /shop rebuild recomputes. Runs async with a "prices not ready" guard on `SERVER_STARTED` and on `/reload`, writing `generated/prices.toml` with a **schema version**; `/shop rebuild` recomputes. Runs **async** off the server thread, with trades rejected by a "prices not ready" guard until it finishes — a 10k-item modpack must not stall world load
- [x] 5.5 Precedence merge, blacklist/whitelist application, unpriced-item exclusion
- [x] 5.5b **Tag rules** — resolve `"#c:ingots" = 90` style entries against the item tag registry, ranked below the Java API and above recipe derivation. Log which tags matched how many items so admins can see the blast radius
- [x] 5.5c **Skip special/dynamic recipes** — any recipe with no resolvable ingredients or an empty result. Log the skip list so missing shop items are explainable
- [x] 5.6 **Arbitrage validation pass** — after solving, assert `sum(output_sell) ≤ sum(input_buy)` for every recipe; log every violation with the offending recipe id, and optionally auto-correct
- [x] 5.7 **Authoring tools (pulled forward from the backlog — needed to do M4.4 at all):** CSV export of the full derived table, and `/shop debug price <item>` dumping the derivation path that produced a price
- **Risk flagged:** reload-listener ordering relative to vanilla's `RecipeManager` is a classic silent-empty-graph bug. Explicitly assert the recipe manager is populated before solving, and log the priced-item count.

### M6 — Balances & transactions
- [x] 6.1 `SavedData` on `server.overworld()` keyed by player UUID (never per-dimension), version-gated for the 1.20.1 factory difference
- [x] 6.2 Buy: validate server-side, debit, deliver — **overflow items drop at the player's feet**
- [x] 6.3 Sell: validate ownership, durability-prorated pricing, and **refuse any stack with non-default components** — enchantments, potion contents, custom names, written books, and above all **containers with contents** (shulker boxes, bundles), which would otherwise sell at empty-container price
- [x] 6.4 Rate limiting (per-player + global), rotating transaction log (off by default)
- [x] 6.5 Death penalty hook (default 0)
- **All transactions are server-authoritative. The client never computes a balance it is trusted on.**

### M7 — Commands

| Command | Perm | Notes |
|---|---|---|
| `/shop` | all | version + command list |
| `/shop ui` | all | opens the GUI (also the `.` keybind) |
| `/shop balance` | all | own balance, short form + full in parentheses |
| `/shop buy <item> [amount]` | all | amount defaults to 1 |
| `/shop sell [item] [count]` | all | defaults to held item, count 1 |
| `/shop price <item>` | all | current buy/sell price |
| `/shop top` | all | leaderboard; OP-gateable via `server.toml` |
| `/shop balance <player>` | OP 2 | view another player, **offline lookup by name** |
| `/shop balance <player> set\|add\|remove <amount>` | OP 2 | logged; **confirmation required above a configurable threshold** |
| `/shop price <item> <price>` | OP 2 | write-through to `prices.toml` with a provenance comment |
| `/shop rebuild` | OP 2 | recompute the derived price table (alias `recalculate`) |
| `/shop reload` | OP 2 | re-read config files without restarting |
| `/shop debug price <item>` | OP 2 | dump the derivation path that produced a price |

- [x] 7.1 Player commands (`/shop`, `ui`, `balance`, `buy`, `sell`, `price`, `top`)
- [x] 7.2 Admin commands (`balance <player> [set|add|remove]`, `price <item> <price>`, `rebuild`, `reload`, `debug price`)
- [x] 7.3 Offline player resolution via the server usercache; confirmation flow for large balance edits
- [x] 7.4 Item argument via `ResourceArgument`/`ResourceLocationArgument` with a suggestion provider limited to **priced** items — avoids the 1.20.1↔1.21 `ItemInput`/`DataComponentPatch` divergence entirely
- [x] 7.5 All output via translation keys

**Naming rationale:** `reload` re-reads files, `rebuild` recomputes derived data. The original `reset` was dropped because it reads as "wipe everyone's balances".


#### M6/M7 outcome — verified at runtime, not just compiled

Dev servers were booted on **Forge 1.20.1** and **Fabric 1.21.1**, exercising both
`GameAdapter` variants and both platform implementations against real Minecraft:

| | Forge 1.20.1 | Fabric 1.21.1 |
|---|---|---|
| Declarations loaded | 311 | 339 |
| Advancements priced | 1271 | 1399 |
| Items priced | 946 from 1163 recipes | 975 from 1279 recipes |
| Solve time | 9 ms, 4 passes | 13 ms, 6 passes |
| Arbitrage violations | 0 | 0 |
| Anchor chain | log 40 → plank 10 → stick 5 | identical |

The declaration and advancement counts differing per version is correct: the shipped
price file spans several Minecraft versions, and 1.20.1 genuinely has fewer of both.

**The arbitrage validator paid for itself immediately.** The first boot reported 8
profitable recipes, every one a mistake in the hand-authored price table — and every one
the same mistake: pricing an item that is actually *craftable*, violating the file's own
"roots only" rule. `end_crystal` declared at 8000 could be crafted for 334 and sold for
6000. Three more surfaced the same way during verification, including two that silently
broke the anchor:

- `oak_wood` declared at 35, below a log — the planks recipe takes the whole `#logs` tag
  and picks its cheapest member, dragging planks to 9 and sticks to 4
- `crimson_hyphae` declared at 30, below its stem — same mechanism, crimson planks to 8

Both derive correctly now (hyphae 54, above the stem, since four stems make three).
That is the "cheapest ingredient alternative wins" rule working exactly as designed and
exposing bad input, rather than a solver bug.

**Three build fixes came out of running rather than compiling:** ModDevGradle creates no
run tasks of its own, only puts a source set on the dev mod path when it is declared in
`mods { }`, and needs `:core` on `additionalRuntimeClasspath` because dev runs load the
classes directory rather than the merged jar. None of these is visible to `./gradlew build`.

### M8 — Networking & client cache
- [x] 8.1 Packet abstraction over the `FriendlyByteBuf` ↔ `CustomPacketPayload` split
- [x] 8.2 S2C price table: compressed, content-hashed, disk-cached client-side; C2S hash check on join returns unchanged/delta/full
- [x] 8.3 S2C balance sync; C2S buy/sell requests; S2C transaction results
- [x] 8.4 Netty-thread safety: every world/state mutation queued onto the server thread

### M9 — HUD
- [x] 9.1 Balance renderer: cobblestone icon (toggleable to `B`) + smart-formatted balance
- [x] 9.2 Nine anchors + pixel offset from client config
- [x] 9.3 Auto-hide when `Minecraft.getInstance().screen != null` or `options.hideGui` (F1); shown/hidden config mode

### M10 — Shop GUI
- [x] 10.1 Vanilla-styled `Screen` — **not** an `AbstractContainerMenu`; buy/sell go over packets, so no Fabric/NeoForge menu-registration divergence
- [x] 10.2 Item grid: icon + buy/sell price per cell, scissored scrolling, name tooltip on hover, dimmed when un-buyable/un-sellable
- [x] 10.3 Search bar (`EditBox`), mod filter, sort by **ID (natural/JEI order)** / name / price asc / price desc
- [x] 10.4 Selection detail panel — "no item selected" when empty; otherwise: icon | name + unit buy/sell price | amount field with `1x`, `64x`, `max buy`, `max sell`, `+`, `-` | Buy/Sell buttons, disabled when impossible, with green `+240` above Sell and red `-300` above Buy
- [x] 10.5 Scroll-to-adjust on the amount column/field; Shift+scroll = ±64
- [x] 10.6 Favorites (right-click to pin, stored client-side)
- [x] 10.7 Keybind: Period (`.`) via standard `KeyMapping` so it is fully remappable in Controls
- [x] 10.8 Performance: precomputed filtered/sorted index, no per-frame allocation in the grid loop

### M11 — Advancements
- [x] 11.1 Walk the advancement tree at startup — `Advancement#getParent/getChildren` on 1.20.1 vs `AdvancementTree`/`AdvancementNode` on 1.21.1 — and compute `base × 1.5^depth` per node
- [x] 11.2 Write computed prizes into `generated/prices.toml`; honour per-advancement overrides from `advancements.toml`
- [x] 11.3 Award on earn via the platform hook; one-shot guard so re-granting never double-pays

### M12 — Recipe-viewer integration
- [x] 12.1 **Tier 3 delivered universally, without any dependency.** A vanilla
  `ItemTooltipEvent`/`ItemTooltipCallback` hook adds a `Shop: buy N / sell M` line to every
  item tooltip. JEI, REI and EMI all render the vanilla tooltip, so one hook covers all
  three at once — and the inventory and creative menu besides. Nothing to break when one of
  them changes its API, and nothing to install. Toggleable with `tooltip_prices` in
  `client.toml`. Verified in-game on 1.20.1 Fabric.

  The higher tiers (a Buy button inside a recipe view, or a click that opens the shop at
  that item) still need each viewer's own API and are **not** done. They are deliberately
  left rather than guessed: they need `compileOnly` artifacts for three mods across two
  Minecraft versions, EMI's Yarn-mapped API needs its own remap configuration on this
  Mojmap project, and none of it can be verified without installing the three mods. Better
  a floor that cannot fall out than three integrations written blind.
- [ ] 12.2 EMI — `addRecipeDecorator`, stable on both 1.20.1 and 1.21.x → tier 1. Needs its own remap config (EMI's API is Yarn-mapped)
- [ ] 12.3 JEI — `addRecipeButtonFactory` (19.27+) → tier 1 on 1.21.1; `addRecipeCategoryDecorator` → tier 3 on 1.20.1. Fabric needs the `jei_mod_plugin` entrypoint, not just the annotation
- [ ] 12.4 REI — experimental `registerExtension`/`getView` → tier 2 (open Shop UI); degrade to tier 3 if the experimental API is unusable

### M13 — Localisation
- [x] 13.1 Every user-facing string as a translation key
- [x] 13.2 `en_us.json`, `uk_ua.json`

### M14 — Release tooling
- [x] 14.1 `scripts/build-release.ps1` + `.sh` taking version and optional loader/MC filters, emitting `build/release/<modversion>/`
- [x] 14.2 Jar naming: `blockies_economy-<mc>-<loader>-<modversion>.jar`, fixed permanently
- [x] 14.3 GitHub Actions matrix over (loader × mc), `fail-fast: false`
- [x] 14.4 `mod-publish-plugin` wired to Modrinth + CurseForge, driven by repo secrets, so a tag push releases
      - Configured once in `stonecutter.gradle.kts` for every node. A script applied with
        `apply(from = ...)` cannot see the plugin's types, and the four loader scripts must
        not share a buildscript classpath, so the root script is the only place this fits.
      - Five jars publish as five versions per site, one per (Minecraft version x loader).
      - Ids live in `gradle.properties`. A blank id skips that platform entirely.
        **CurseForge needs the numeric project id, not the slug** - uploads go to
        `/api/projects/<id>/upload-file`.
      - The release job uploads the artifacts the build jobs already tested, rather than
        rebuilding: `publishMods` reads the jar by path and takes no task dependency.
- [ ] 14.5 Final `./gradlew build` across the whole matrix

### M15 — Docs
- [ ] 15.1 `README.md` — install, commands, config overview
- [x] 15.2 `docs/` GitHub Pages: admin config guide, **mod-dev price declaration guide** (datapack JSON schema + Java API), price-pack download index
- [x] 15.3 `price-packs/` folder with a template and 1–2 worked examples

### M16 — Expansion (post-v1)
- [ ] 16.1 Add node `1.21.11`
- [ ] 16.2 Add node `26.3` — Java 25, unobfuscated, **no `remapJar`**; NeoForge 26.3 is beta-only and Forge has no 26.3 build at all
- [ ] 16.3 **Forge on 1.21.x — deferred by decision, not blocked forever.** ModDevGradle 2.0.147 cannot
  build post-1.20.1 Forge: its NeoFormRuntime resolves only from NeoForged Maven and the local
  `.m2`, never learns `maven.minecraftforge.net`, and does not declare `forge:universal-srg` or
  `mcp_config`, both of which Forge 52.x requires. Injecting them into the NFRT configurations
  breaks manifest evaluation instead. Three routes when this is picked up:
  1. Wait for ModDevGradle to support it upstream (cheapest; check its changelog first)
  2. ForgeGradle 6 in an **isolated included build**, so its plugin classpath never meets Loom
  3. A hand-rolled Forge module consuming `:core` plus a native platform adapter
  NeoForge covers the 1.21.1 Forge-side audience meanwhile, and the ecosystem has largely moved
  there. The `build-forge.gradle.kts` script already handles 1.21+ Java levels, so only the
  toolchain gap stands in the way.
- [ ] 16.4 Legacy era tree 1.12.2–1.19.4: separate Gradle build, Java 8/17, ForgeGradle + Loom, consuming the same `core`
- **Warning:** 1.21.2 is a violent API break (recipes become a datapack registry, `getResultItem` removed, `Ingredient` becomes `HolderSet`-based, and only placeable recipes sync to clients). Budget real work for the 1.21.1 → 1.21.11 step.

---

## Execution protocol

Per the brief: work `PLAN.md` top to bottom, mark each task `[x]` on completion, and run a Gradle compile/check after each logical step. Fix any error before advancing. The gates are **M1.0** (toolchain spike — report which rung of the fallback ladder we land on), **M1.9** (all 6 jars build) and **M2.7** (`core` tests green). No feature work proceeds past a red build.

## Verification

- **Unit:** `./gradlew :core:test` — solver cycles, multi-output division, cheapest-path selection, money formatting, TOML comment round-trip
- **Compile:** `./gradlew build` produces all 6 v1 jars
- **Runtime, per loader:** `runClient` on each node (exact Stonecutter task path confirmed at M1.0) — verify first-start price generation, HUD anchoring and auto-hide, keybind opens the shop, buy/sell round-trip, `/reload` does not empty the price graph
- **Server-authority:** confirm a hand-crafted C2S buy packet with a bad amount is rejected server-side
- **Dedicated server:** run a headless server + separate client to validate the join-time price sync, hash cache and delta path
- **Integration:** drop EMI/JEI/REI into the run's mods folder individually and confirm the capability ladder degrades as designed

## Open risks

1. **Stonecutter driving three Gradle toolchains in one tree.** The proven configuration is Stonecutter + Loom, or Stonecutter + Loom + ModDevGradle (Friends&Foes). Adding a legacy Forge plugin is the unproven delta. Mitigated by buildSrc convention plugins and by building Forge **first** (M1.3) so failure surfaces on day one. Escape hatch: move Forge 1.20.1 into an isolated included build.
2. **ModDevGradle `legacyforge` for Forge 1.20.1** is the preferred path but unverified here; ForgeGradle 6 is the fallback and is fussier about Gradle versions.
3. **Reload-listener ordering** vs vanilla `RecipeManager` — must be asserted, not assumed, or the price graph silently comes out empty.
4. **REI's experimental API** may not support tier 2; tier 3 fallback is planned.
5. **Economy invariants** — the craft-arbitrage ceiling and the rounding leak are both easy to reintroduce with an innocent-looking config change. M5.6 exists to catch this automatically rather than relying on review.

## Ideas backlog (not in v1 — flag any to pull forward)

*CSV export and `/shop debug price` were pulled into v1 as M5.7; they are authoring tools the pricing work depends on.*

**Economy:** price drift from supply/demand (selling a lot depresses the price, recovering over time); per-item stock limits; daily deals; an admin-set inflation/deflation knob; bank interest; player-to-player transfers; shop-block entities for per-base shops; quests/bounties as an income source; villager-trade integration.

**UX:** price history sparkline in the detail panel; "what can I afford right now" filter; bulk-sell-inventory button; a recipe-cost breakdown tooltip showing *why* an item costs what it does; sound + particle feedback on transactions; keyboard navigation in the grid.

**Technical:** a benchmark harness for the solver on a 10k-item modpack; a headless `--calculate-prices` CLI mode; fuzz tests on the TOML parser; JMH on the HUD render path; a price-pack validation CI job; content-hash-based incremental price recomputation so `/reload` is cheap; an automated arbitrage-loop *search* (not just per-recipe validation) over the full graph.

---

## Execution log — M8, M9, M10, M5.2, M5.3, M14, M15

**Toolchain note.** `JAVA_HOME` on this machine pointed at a JDK 8 install that no longer
exists (`jdk-8.0.472.8`; the installed ones are `8.0.442.6` and `8.0.504.1`). The Gradle
wrapper launcher validates `JAVA_HOME` before Gradle can apply the daemon pin, so every
build failed until it was pointed at an existing JDK. Nothing in the repo caused it and
nothing in the repo can fix it — it needs setting in the environment.

**M8 — networking.** Payloads are opaque `byte[]`, encoded in `core/net` with no Minecraft
types. One Minecraft channel carries every logical channel with the name in the body.
NeoForge needed two payload types rather than one: it keeps a single registry for both
directions and refuses a duplicate id, which cost one dedicated-server boot to discover.
Price tables are deflated, SHA-256 hashed and chunked at 24 KB; 946 vanilla items compress
to 6.4 KB. A client offers its cached hash on join and gets unchanged, a delta or the full
table.

**M9/M10 — HUD and shop.** The shop draws its own background and widgets. `Screen#render`
gained a background call in 1.20.5 that would cover the panel, and `Screen.renderables` is
private on 1.20.1, so the screen keeps its own widget list and renders it. `mouseScrolled`
is declared in both the 3-argument (1.20.1) and 4-argument (1.20.5+) forms without
`@Override`; one overrides on each version and the other is dead code, which is cheaper
than forking the whole screen.

**Verified by running it.** Every loader boots a dedicated server and solves the same table,
with identical content hashes per Minecraft version across loaders (`d84dc2d25dea2620` on
1.20.1, `8d59f42ceacdcb82` on 1.21.1). Three distinct client paths were driven into a real
world with `-PquickPlay` and checked by screenshot:

| Client | Prices synced | HUD | Shop screen |
|---|---|---|---|
| 1.20.1 Fabric | 946 items, cached, "unchanged" on rejoin | yes | yes, bought and sold |
| 1.20.1 Forge | 946 items | yes | yes |
| 1.21.1 NeoForge | 974 items | yes | yes |

The 1.20.1 Quilt jar is byte-for-byte the Fabric jar with different metadata, so the Fabric
result covers it; its server was booted separately.

Testing found three defects that reading had not: Sell stayed enabled with an empty
inventory, the widget list doubled on every window resize, and the status line compared
against a fresh `Component.empty()` so it was never equal.

**Still open.** M12 (recipe-viewer integration), M15.1 (README is written but should be
re-read once M12 lands), M16 (expansion).

M14.4 is done and dry-run verified against all five nodes. CurseForge stays switched off
until `publish.curseforge_id` is filled in with the numeric id from the project page.
