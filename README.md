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
`data/<your_namespace>/blockies_economy/prices.json`. See `docs/` for the schema, and
`price-packs/` for ready-made price files for popular mods.

## Supported versions

| Minecraft | Fabric | NeoForge | Forge | Quilt |
|---|---|---|---|---|
| 1.21.1 | planned | planned | planned | via Fabric jar |
| 1.20.1 | planned | n/a | planned | planned |

Newer versions (1.21.11, 26.x) and older ones (1.12.2–1.19.4) are on the roadmap — see
[PLAN.md](PLAN.md).

## Building

The Gradle daemon is pinned to **Java 21** via `gradle/gradle-daemon-jvm.properties`, and each
Minecraft version's compile toolchain (17 / 21 / 25) is provisioned automatically. You only need
a working `JAVA_HOME` pointing at any JDK for the wrapper launcher itself.

```bash
./gradlew build
```

## License

MIT — see [LICENSE](LICENSE).
