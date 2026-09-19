# Blockies Economy

A server-authoritative economy mod for Minecraft: Java Edition. Players earn **Blockies** from
advancements and from selling items, and spend them in a vanilla-styled shop. Prices are not
hand-written for every item — they are derived from the game's own recipe tree, so modded items
get sensible prices without anyone pricing them.

## Guides

- **[Server admin guide](admin-guide.md)** — configuration, tag rules, the one setting that can
  ruin your economy, and how to balance prices with a spreadsheet.
- **[Mod developer guide](mod-developers.md)** — price your own items with a datapack file, a
  service provider, or a direct call. No dependency on this mod required.
- **[Price packs](https://github.com/alexlogvin/blockies-economy/tree/main/price-packs)** —
  ready-made price files for popular mods. Not bundled with the jar: a server should not carry a
  thousand prices for mods it does not have.

## How pricing works, in one paragraph

A small set of items are priced by hand — the ones no recipe can reach, like raw ores, mob drops
and logs. Everything else is solved from recipes: an item costs the sum of its ingredients times a
multiplier for the recipe type, the cheapest recipe wins, and cycles such as ingot → nuggets →
ingot are resolved by relaxing to a fixed point. Anything that ends up with no price is simply
absent from the shop rather than given an invented one.

## The rule that keeps it honest

Selling pays less than buying costs, by a configurable margin. If a recipe's multiplier were ever
to exceed `1 / sell_multiplier`, a player could buy ingredients, craft, and sell the output for
more than they paid — forever. The default multiplier is 1.3 against a ceiling of 1.333, values
above the ceiling are clamped at startup, and every recipe is checked after each rebuild with any
profitable loop logged by name.

## Links

- [Source and issues](https://github.com/alexlogvin/blockies-economy)
- [MIT licence](https://github.com/alexlogvin/blockies-economy/blob/main/LICENSE)
