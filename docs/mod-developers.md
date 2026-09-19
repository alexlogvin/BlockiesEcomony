# Pricing your mod's items

Blockies Economy prices items by working backwards through recipes from a set of hand-authored
root prices. That covers most modded items automatically: if your ingot is craftable from your
raw ore, and something else is craftable from your ingot, the whole chain is priced as soon as
one root in it has a number.

What it cannot do is invent a price for something with no recipe and no declaration — a raw ore
you dig out of the ground, a mob drop, a loot-only item. Those need a number from somewhere, and
this page is about the three places it can come from.

## 1. A datapack file (recommended)

Ship this inside your own jar. No dependency on Blockies Economy, no code, and nothing breaks if
a player does not have this mod installed — the file is simply never read.

```
src/main/resources/data/<your_namespace>/blockies_economy/prices.json
```

```json
{
  "prices": {
    "mymod:raw_tin": 55,
    "mymod:tin_ingot": 80,
    "#c:ingots/tin": 80,
    "mymod:creative_energy_cell": ""
  }
}
```

- A **number** is a price in whole Blockies.
- A key starting with **`#`** is an item tag, and prices every member at once.
- **`""`** blacklists the item: it never appears in the shop and cannot be bought or sold.

The outer `prices` object may be omitted if the file contains nothing else.

Any namespace works, so a **pack author** can override a mod's prices from their own datapack
without touching the mod's jar.

### What to declare

Declare roots, not everything. Pricing `mymod:tin_ingot` when it is craftable from
`mymod:raw_tin` is allowed, but it overrides the derived value and takes the whole chain above it
with it. Prefer:

- raw ores, gems and other block drops
- mob drops
- loot-only items
- anything whose only recipe needs a machine the solver cannot cost

and let recipes do the rest.

### Tags are the big lever

If your metals are tagged `c:ingots/*`, `c:raw_materials` and so on, a **server admin** can price
every modded metal in the game with three lines. Tagging your items properly does more for your
mod's shop support than a long price file.

## 2. A service provider

Use this when your prices are computed rather than written down.

```java
package mymod;

import com.alexlogvin.blockieseconomy.api.BlockiesEconomyAPI;
import com.alexlogvin.blockieseconomy.api.PriceProvider;

public final class MyModPrices implements PriceProvider {
    @Override
    public void declarePrices(BlockiesEconomyAPI.PriceRegistry registry) {
        for (MyMetal metal : MyMetal.values()) {
            registry.price(metal.ingotId(), metal.tier() * 40L);
        }
        registry.blacklist("mymod:creative_generator");
    }
}
```

Declare it in:

```
src/main/resources/META-INF/services/com.alexlogvin.blockieseconomy.api.PriceProvider
```
```
mymod.MyModPrices
```

This route needs Blockies Economy on the compile classpath (`compileOnly`), and your mod must
tolerate its absence at runtime — the class is only loaded when the service is scanned, so simply
never referencing it elsewhere is enough.

## 3. Direct registration

The fallback when service discovery does not reach you, which can happen on loaders that isolate
mods into separate module layers. Call it from your own initialiser, guarded so your mod still
loads without this one:

```java
if (isBlockiesEconomyLoaded()) {
    BlockiesEconomyAPI.register(new MyModPrices());
}
```

## Precedence

Highest authority first:

1. `config/blockies_economy/prices.toml` — the server admin
2. `config/blockies_economy/prices.d/*.toml` — drop-in price packs
3. datapack `prices.json` — you, and pack authors
4. the Java API
5. tag rules, from any of the above
6. derived from recipes

An admin always outranks you. That is deliberate: they know their server, and a price you picked
for your mod in isolation may be wrong in their pack.

## Sanity check: the arbitrage rule

If a player can buy your item's ingredients, craft it, and sell the result for more than they
paid, they can mint Blockies forever. The solver's own multipliers are kept below
`1 / sell_multiplier` to prevent that, but a **hand-declared price** bypasses the solver and can
reintroduce it.

The server logs every recipe that can be run at a profit on startup. If your mod appears there,
one of your declared prices is below what its own ingredients cost.
