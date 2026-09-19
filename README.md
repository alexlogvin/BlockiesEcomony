# Blockies Economy

A server-authoritative economy mod for Minecraft: Java Edition. Players earn **Blockies** from
advancements and from selling items, and spend them in a vanilla-styled shop. Prices are not
hand-written for every item — they are **derived from the game's own recipe tree**, so modded
items get sensible prices automatically.

> **Status: pre-release (`0.1.0`), under active development.** Config formats may still change.

## Highlights

- **No runtime dependency on other mods.** No Architectury, no config library, no shim mod.
  (The Fabric/Quilt builds use Fabric API, which virtually every Fabric pack already ships.)
- **Automatic pricing.** Base prices are authored only for raw, non-craftable items; everything
  craftable is derived from recipes — including items from mods nobody has priced by hand.
- **Tag rules.** One line like `"#c:ingots" = 90` prices hundreds of modded items at once.
- **Server-authoritative.** Every transaction is validated server-side. The client caches prices
  for a responsive UI but is never trusted with a balance.
- **Client optional.** Without the mod installed client-side you keep every command and lose only
  the GUI and HUD. Vanilla clients can still join.
- **Prices in every tooltip.** Hovering an item shows its buy and sell price — in your inventory,
  and in JEI, REI or EMI, all of which draw the vanilla tooltip. No dependency on any of them.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/shop` | everyone | Mod version and command list |
| `/shop ui` | everyone | Open the shop GUI (also bound to `.` by default) |
| `/shop balance` | everyone | Your balance, short form with the full number in parentheses |
| `/shop buy <item> [amount]` | everyone | Buy an item; amount defaults to 1 |
| `/shop sell [item] [count]` | everyone | Sell an item; defaults to the held item, count 1 |
| `/shop price <item>` | everyone | Show an item's buy and sell price |
| `/shop top` | everyone\* | Balance leaderboard (\*can be restricted to operators) |
| `/shop balance <player>` | OP 2 | View another player's balance, including offline |
| `/shop balance <player> set\|add\|remove <amount>` | OP 2 | Adjust a balance |
| `/shop price <item> <price>` | OP 2 | Set a price; written through to `prices.toml` |
| `/shop rebuild` | OP 2 | Recompute all derived prices |
| `/shop reload` | OP 2 | Re-read config files without restarting |
| `/shop debug price <item>` | OP 2 | Explain how a price was derived |
| `/shop debug export` | OP 2 | Write the whole price table to `generated/prices.csv` |

`reload` re-reads files; `rebuild` recomputes derived data.

## Configuration

Config lives in `config/blockies_economy/`:

```
server.toml            multipliers, balances, rate limits, permissions
client.toml            HUD position and appearance
prices.toml            hand-authored prices (admin has the final say)
advancements.toml      advancement prize overrides
whitelist.toml         optional; empty means no whitelist applies
prices.d/              drop-in price add-ons, e.g. create.toml
generated/prices.toml  machine-written output — do not edit
generated/prices.csv   the same table as a spreadsheet, from /shop debug export
```

Every file ships with explanatory comments. Setting a price to `""` blacklists that item.

**Price sources, highest priority first:** `prices.toml` → `prices.d/*.toml` → datapack
`prices.json` → Java API → tag rules → derived from recipes. Items that end up with no price are
simply absent from the shop.

### The one rule that matters

Craft multipliers must stay below `1 / sell_multiplier`. At the default `sell_multiplier = 0.75`
that ceiling is **1.333**. Above it, a player can buy ingredients, craft, and sell the result at a
profit — forever. Values over the ceiling are clamped at startup with a warning.

## For mod and pack authors

You can price your own items **without depending on this mod**: ship a datapack file at
`data/<your_namespace>/blockies_economy/prices.json`.

- [Mod developer guide](docs/mod-developers.md) — datapack schema, the Java API, precedence
- [Server admin guide](docs/admin-guide.md) — configuration, tag rules, balancing
- [Price packs](price-packs/) — ready-made price files for popular mods, not bundled with the jar

## Supported versions

| Minecraft | Fabric | NeoForge | Forge | Quilt |
|---|---|---|---|---|
| 1.21, 1.21.1 | yes | yes | deferred | use the Fabric jar |
| 1.20.1 | yes | n/a | yes | use the Fabric jar |

Forge on 1.21.x is deferred rather than abandoned: the build plugin cannot produce it yet. See
[PLAN.md](PLAN.md) M16.3.

**Quilt runs the Fabric jar.** There is no separate Quilt download, because there would be
nothing different in it: Quilt's own API stack was retired in 2025, so a Quilt build has no
Quilt-native API to target, and the jar this repo used to ship for it came out byte-identical to
the Fabric one. Quilt Loader runs Fabric mods through its compatibility layer, and Fabric API
has a Quilt-compatible release.

### Why the version ranges look the way they do

A jar covers a span only where the span is **proven**, never where the versions merely look
close. The 1.21 jar declares `>=1.21 <1.21.2` because building it against 1.21 and against
1.21.1 produces remapped classes that are byte-for-byte identical — there is nothing a second
jar could contain. The NeoForge jar spans the same pair, and drops its loader floor to
`[21.0,)` to match.

The upper bound is equally deliberate. It is not `~1.21.1`, which Fabric Loader reads as the
whole 1.21 line: the price engine reads the recipe manager directly, that API was rewritten in
1.21.2, and a jar claiming the versions after it would install happily and then fail to price
anything.

Where the breaks fall, measured by compiling the mod against every release and resolving the
built bytecode against each one:

| Span | What changes at the lower edge |
|---|---|
| 1.20.1 | `Advancement` still carries its own id; recipes are not yet `RecipeHolder` |
| 1.20.2 – 1.20.4 | identity moves to `AdvancementHolder`; `RecipeHolder` arrives |
| 1.20.5 – 1.20.6 | `SavedData.Factory`; the component and payload rewrites |
| 1.21 – 1.21.1 | Fabric API’s `HudRenderCallback` takes a `DeltaTracker` |
| 1.21.2 – 1.21.4 | recipes become a datapack registry; `Ingredient.getItems` removed |
| 1.21.5 | `SavedData.Factory` removed; `CompoundTag` getters return `Optional` |
| 1.21.6 – 1.21.8 | `GuiGraphics` moves to `RenderPipeline`; pose becomes `Matrix3x2f` |
| 1.21.9 – 1.21.10 | `Screen` input signatures change; keybind categories become a type |
| 1.21.11 | widest break of the line |

Spans are narrow because Minecraft changes, not because of how this mod is built: only three
classes fork by version at all. One jar per **loader** is unavoidable for a different reason —
Fabric resolves intermediary names at runtime while NeoForge and Forge use Mojmap and SRG, so
the same bytes cannot satisfy two of them.

Newer versions (1.21.11, 26.x) and older ones (1.12.2–1.19.4) are on the roadmap — see
[PLAN.md](PLAN.md).

## Building

The Gradle daemon is pinned via `gradle/gradle-daemon-jvm.properties`, and each Minecraft version's
compile toolchain (17 / 21 / 25) is provisioned automatically. `JAVA_HOME` must point at a JDK that
still exists — the wrapper launcher checks it before Gradle gets a say, so a JDK that was
uninstalled or upgraded in place stops the build with a confusing error.

```bash
./gradlew build                       # every jar
./gradlew :core:test                  # the pure-Java tests, no Minecraft needed
scripts/build-release.sh --mc 1.20.1  # one version, collected in build/release/
```

## License

MIT — see [LICENSE](LICENSE).
