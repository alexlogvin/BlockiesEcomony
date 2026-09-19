# Price packs

Ready-made price files for popular mods, kept **out of the mod jar** on purpose: nobody should
ship a thousand prices for mods a server does not have, and these change far more often than the
mod itself does.

## Using one

Download the `.toml` you want and drop it in:

```
config/blockies_economy/prices.d/
```

Then `/shop rebuild`, or restart. Files here rank below your own `prices.toml`, so anything you
have set yourself still wins.

Several files can coexist. Within `prices.d/` they are read in filename order; if two of them
price the same item, the first one read wins, so a `00-overrides.toml` is a convenient way to
patch a pack you otherwise want unchanged.

## Writing one

Same format as `prices.toml`:

```toml
# create.toml — prices for Create 0.5.x
#
# Only roots are declared. Everything craftable from these is derived by the solver.

[create.materials]
"create:raw_zinc"      = 55
"create:zinc_ingot"    = 80

[create.blacklist]
# Creative-only items should never be purchasable.
"create:creative_motor" = ""
```

Rules that keep a pack maintainable:

- **Declare roots only.** Price the raw ore, not the ingot, the block and the nugget. Pricing a
  craftable item overrides the solver for everything above it in the chain.
- **Prefer tags.** `"#c:ingots/zinc" = 80` survives the mod renaming its item; a hard-coded id
  does not.
- **Use tables for grouping.** Table names carry no meaning to the parser — they exist to keep a
  long file navigable.
- **Say which mod version it targets** in a comment at the top. Ids change between major versions.

## Contributing

Open a pull request adding your file here, with the mod name and version in the filename and a
comment header. Please check the server log after a rebuild: if your prices appear in the
"recipes that can be run at a profit" warning, one of them is below what its own ingredients cost.

See [`../docs/mod-developers.md`](../docs/mod-developers.md) if you are the mod's author — you can
ship prices inside your own jar instead, and they will be there for everyone automatically.
