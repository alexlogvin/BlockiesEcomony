# Server admin guide

Everything lives in `config/blockies_economy/`. Every file is written with comments on first
start, so the fastest way to learn a setting is usually to read the file it lives in.

```
server.toml            multipliers, balances, rate limits, permissions
client.toml            HUD position and appearance (client-side only)
prices.toml            your prices — the final word
advancements.toml      advancement prize overrides
whitelist.toml         optional; empty means no whitelist applies
prices.d/              drop-in price add-ons, e.g. create.toml
generated/prices.toml  what the solver worked out — read-only, rewritten every rebuild
generated/prices.csv   the same table as a spreadsheet, from /shop debug export
```

## How prices are decided

Sources are consulted in order, and the first one that names an item wins:

1. `prices.toml`
2. `prices.d/*.toml`
3. datapack `prices.json`, from mods and datapacks
4. the Java API, used by mods that compute their prices
5. tag rules such as `"#c:ingots" = 90`, from any of the above
6. derived from recipes

An item that reaches the end with no price is **absent from the shop**. That is intentional: the
shop should not invent a number for something nobody priced and no recipe reaches.

Setting a price to `""` blacklists the item — it is not the same as pricing it at zero.

## The one setting that can ruin your economy

```toml
default_recipe_multiplier = 1.3
```

A recipe's output costs the sum of its ingredients times a multiplier. If that multiplier ever
exceeds `1 / sell_multiplier`, a player can buy ingredients, craft, and sell the result for more
than they paid — forever, in a loop, with no limit.

At the default `sell_multiplier = 0.75` the ceiling is **1.333**. Anything above it is clamped at
startup with a warning in the log. Recipe types that consume nothing extra (crafting, stonecutting)
are pinned at `1.0`.

Hand-written prices in `prices.toml` bypass the solver and can reintroduce the same loop. The
server checks every recipe after each rebuild and logs any that can be run at a profit. **Read
those warnings** — they almost always mean a price you set is below what its own ingredients cost.

## Tag rules

The most efficient thing you can do for a modded server:

```toml
"#c:ingots"        = 90
"#c:raw_materials" = 60
"#c:gems"          = 220
```

Modded metals are near-universally tagged, so three lines can price hundreds of items across every
mod in a pack. The log reports how many items each rule matched, so you can see the blast radius.

Tag rules rank below every explicit item id, including ones from mods — so a tag fills in the gaps
rather than overwriting anything specific.

## Balancing

1. Boot the server once so the table is built.
2. Run `/shop debug export` and open `generated/prices.csv` in a spreadsheet.
3. Sort by price. Anything absurd is nearly always a root price that needs adjusting, not a solver
   bug — the solver only multiplies what you gave it.
4. Put corrections in `prices.toml`, then `/shop rebuild`.

`/shop debug price <item>` explains where a single price came from, which is faster than reading
the CSV when you already know what looks wrong.

## Commands

`reload` re-reads config files. `rebuild` recomputes derived prices. They are different things and
an admin usually wants `rebuild` after editing `prices.toml` — though `rebuild` re-reads the files
too, so it is the safe one to reach for.

| Command | Permission |
|---|---|
| `/shop`, `/shop ui`, `/shop balance`, `/shop buy`, `/shop sell`, `/shop price <item>`, `/shop top` | everyone |
| `/shop balance <player> [set\|add\|remove <amount>]` | OP 2 |
| `/shop price <item> <price>` | OP 2 |
| `/shop rebuild`, `/shop reload`, `/shop debug price`, `/shop debug export` | OP 2 |

The permission level is configurable in `server.toml`, as is whether `/shop top` is public.

Balance edits above a configurable threshold need confirming, so a mistyped `/shop balance Steve
set 1000000` does not land silently.

## Players without the mod

The server is authoritative and the client jar is optional. A player on a vanilla client, or on a
client without Blockies Economy, keeps every command and loses only the shop screen and the HUD.
They are never refused at login.

## Balances and worlds

Balances are stored in the world save, keyed by player UUID, and survive death and dimension
changes. Delete the world and the balances go with it — which is usually what a fresh start is
meant to mean.
