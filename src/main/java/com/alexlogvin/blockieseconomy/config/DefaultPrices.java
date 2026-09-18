package com.alexlogvin.blockieseconomy.config;

import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import com.alexlogvin.blockieseconomy.core.toml.TomlTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlValue;
import java.util.Arrays;

/**
 * The shipped {@code prices.toml}: hand-authored prices for vanilla's <em>root</em> items.
 *
 * <p>Only items that cannot be crafted are listed — raw ores, mob drops, logs, plants and
 * loot-only curiosities. Everything craftable is derived from recipes, which is what makes
 * the mod work for modded items nobody has priced.
 *
 * <p>The scale spans roughly 1:500, anchored so that:
 *
 * <pre>
 *   oak log 40  ->  4 planks at 10 each  ->  4 sticks at 5 each
 * </pre>
 *
 * <p>5 is the floor. Nothing in the game is worth less than a stick, and pricing anything
 * lower would run into integer rounding.
 */
public final class DefaultPrices {

    private DefaultPrices() {
    }

    public static TomlDocument document() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().addAll(Arrays.asList(
                "Blockies Economy - item prices.",
                "",
                "This file has the final say. Anything you set here beats a price pack, a",
                "datapack, a mod's own declaration, a tag rule, or the recipe solver.",
                "",
                "HOW PRICING WORKS",
                "  Only items that cannot be crafted need a price here. Everything else is",
                "  derived from the game's recipes: a craft costs the sum of its ingredients,",
                "  divided by how many it yields, times the multiplier for its recipe type.",
                "  That is why modded items get sensible prices without anyone listing them.",
                "",
                "  Prices below are BUY prices. The sell price is derived from",
                "  sell_multiplier in server.toml.",
                "",
                "SYNTAX",
                "  \"minecraft:dirt\" = 5        a price, in whole Blockies",
                "  \"minecraft:bedrock\" = \"\"     an empty price BLACKLISTS the item entirely",
                "  \"#c:ingots\" = 90            a tag rule: prices every item in that tag",
                "",
                "  An absent item is not the same as a blacklisted one. Absent means",
                "  \"work it out from recipes\"; empty means \"never buy or sell this\".",
                "",
                "  Tag rules are the easy way to support a mod: most modded ores, ingots,",
                "  gems and woods are tagged, so one line can price hundreds of items.",
                "",
                "THE SCALE",
                "  5 is the floor - a stick. Diamond sits near 2500, so the game spans",
                "  roughly 1:500 from the cheapest block to a diamond, and further for",
                "  the handful of endgame items below that.",
                "",
                "  The anchor everything else hangs off:",
                "    oak log 40  ->  4 planks at 10  ->  4 sticks at 5",
                "",
                "  Table names below are for your convenience only - they carry no meaning,",
                "  and you can regroup or rename them freely.",
                "",
                "After editing, run /shop reload then /shop rebuild.",
                "Run /shop debug price <item> to see how any price was arrived at."));

        stone(doc);
        wood(doc);
        ores(doc);
        plants(doc);
        mobDrops(doc);
        food(doc);
        nether(doc);
        end(doc);
        ocean(doc);
        lootOnly(doc);
        blocked(doc);

        return doc;
    }

    private static void stone(TomlDocument doc) {
        TomlTable t = table(doc, "earth_and_stone",
                "Dug by the thousand. These set the floor of the economy.");

        price(t, 5, "dirt", "coarse_dirt", "rooted_dirt", "sand", "red_sand", "gravel",
                "cobblestone", "cobbled_deepslate", "grass_block", "podzol",
                "mycelium", "mud", "clay", "snow_block", "ice", "magma_block",
                "tuff", "calcite", "dripstone_block", "moss_block", "sandstone",
                "red_sandstone", "andesite", "diorite", "granite", "stone",
                "deepslate", "obsidian");

        price(t, 10, "clay_ball", "flint", "snowball", "pointed_dripstone", "glowstone",
                "crying_obsidian", "gilded_blackstone", "packed_mud", "sculk", "sculk_vein");

        price(t, 25, "amethyst_shard", "sculk_catalyst", "sculk_shrieker", "sculk_sensor");
    }

    private static void wood(TomlDocument doc) {
        TomlTable t = table(doc, "wood",
                "Logs are the anchor of the whole scale: one log makes four planks,",
                "and two planks make four sticks, putting a stick at 5.");

        price(t, 40, "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log",
                "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log",
                "crimson_stem", "warped_stem");

        price(t, 35, "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood",
                "dark_oak_wood", "mangrove_wood", "cherry_wood", "pale_oak_wood");

        price(t, 15, "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves",
                "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "cherry_leaves",
                "azalea_leaves", "flowering_azalea_leaves", "pale_oak_leaves");

        price(t, 20, "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling",
                "acacia_sapling", "dark_oak_sapling", "cherry_sapling", "mangrove_propagule",
                "pale_oak_sapling", "azalea", "flowering_azalea");

        price(t, 25, "bamboo", "vine", "cactus", "sugar_cane", "dead_bush", "nether_wart_block",
                "warped_wart_block", "shroomlight", "crimson_fungus", "warped_fungus",
                "crimson_roots", "warped_roots", "weeping_vines", "twisting_vines");
    }

    private static void ores(TomlDocument doc) {
        TomlTable t = table(doc, "ores_and_minerals",
                "Raw drops only. Ingots are smelted, so the solver prices them:",
                "raw iron 60 smelts into an ingot at 60 x 1.3 = 78.",
                "",
                "The ore BLOCKS are priced too, since a silk-touch player can sell them.",
                "They sit above their drop so mining the ore is never the worse option.");

        price(t, 30, "coal", "raw_copper", "charcoal");
        price(t, 25, "redstone", "lapis_lazuli");
        price(t, 60, "raw_iron");
        price(t, 90, "raw_gold");
        price(t, 30, "quartz");
        price(t, 600, "emerald");
        price(t, 2500, "diamond");
        price(t, 8000, "ancient_debris");

        price(t, 45, "coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore");
        price(t, 40, "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore");
        price(t, 90, "iron_ore", "deepslate_iron_ore");
        price(t, 135, "gold_ore", "deepslate_gold_ore", "nether_gold_ore");
        price(t, 50, "nether_quartz_ore");
        price(t, 900, "emerald_ore", "deepslate_emerald_ore");
        price(t, 3750, "diamond_ore", "deepslate_diamond_ore");
    }

    private static void plants(TomlDocument doc) {
        TomlTable t = table(doc, "plants_and_farming",
                "Renewable, so priced low enough that a farm is a slow earner rather",
                "than a money printer.");

        price(t, 10, "wheat_seeds", "melon_seeds", "pumpkin_seeds", "beetroot_seeds",
                "torchflower_seeds", "pitcher_pod");
        price(t, 15, "wheat", "beetroot", "carrot", "potato", "melon_slice", "sweet_berries",
                "glow_berries", "kelp", "seagrass", "sea_pickle", "lily_pad", "moss_carpet",
                "big_dripleaf", "small_dripleaf", "hanging_roots", "spore_blossom");
        price(t, 25, "pumpkin", "melon", "cocoa_beans", "brown_mushroom", "red_mushroom",
                "crimson_nylium", "warped_nylium", "chorus_fruit");
        price(t, 60, "nether_wart", "poisonous_potato");

        price(t, 12, "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
                "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac",
                "rose_bush", "peony", "pink_petals", "torchflower", "pitcher_plant",
                "short_grass", "grass", "tall_grass", "fern", "large_fern", "open_eyeblossom",
                "closed_eyeblossom");
    }

    private static void mobDrops(TomlDocument doc) {
        TomlTable t = table(doc, "mob_drops",
                "Things you can only get by fighting or farming mobs. Priced above",
                "plants, since they cost risk or a build to obtain.");

        price(t, 15, "string", "rotten_flesh", "bone", "spider_eye");
        price(t, 20, "feather", "gunpowder", "ink_sac", "glow_ink_sac", "rabbit_hide",
                "slime_ball");
        price(t, 40, "leather", "honeycomb", "phantom_membrane", "armadillo_scute",
                "turtle_scute");
        price(t, 90, "blaze_rod", "ender_pearl", "magma_cream");
        price(t, 150, "ghast_tear", "rabbit_foot", "nautilus_shell", "breeze_rod");
        price(t, 400, "shulker_shell", "prismarine_shard", "prismarine_crystals");

        price(t, 25, "white_wool", "orange_wool", "magenta_wool", "light_blue_wool",
                "yellow_wool", "lime_wool", "pink_wool", "gray_wool", "light_gray_wool",
                "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool",
                "red_wool", "black_wool");

        price(t, 20, "egg", "brown_egg", "blue_egg");
    }

    private static void food(TomlDocument doc) {
        TomlTable t = table(doc, "raw_food",
                "Raw meat and fish. Cooked versions are smelted, so the solver prices them.");

        price(t, 25, "porkchop", "beef", "chicken", "mutton", "rabbit", "cod", "salmon");
        price(t, 60, "tropical_fish", "pufferfish");
        price(t, 120, "honey_bottle");
    }

    private static void nether(TomlDocument doc) {
        TomlTable t = table(doc, "nether",
                "Nether-only materials beyond the ores above.");

        price(t, 20, "netherrack", "nether_bricks", "soul_sand", "soul_soil");
        price(t, 30, "basalt", "smooth_basalt", "blackstone", "warped_hyphae", "crimson_hyphae");
        price(t, 12000, "netherite_scrap");
    }

    private static void end(TomlDocument doc) {
        TomlTable t = table(doc, "the_end",
                "End materials. Expensive because getting there is the point of the game.");

        price(t, 15, "end_stone");
        price(t, 60, "chorus_flower");
        price(t, 800, "purpur_block");
        price(t, 2000, "dragon_breath");
        price(t, 25000, "elytra");
        price(t, 50000, "nether_star");
        price(t, 100000, "dragon_egg");
        price(t, 8000, "end_crystal");
    }

    private static void ocean(TomlDocument doc) {
        TomlTable t = table(doc, "ocean",
                "Underwater structures and their drops.");

        price(t, 60, "prismarine", "prismarine_bricks", "dark_prismarine", "sponge",
                "wet_sponge", "tube_coral", "brain_coral", "bubble_coral", "fire_coral",
                "horn_coral", "tube_coral_block", "brain_coral_block", "bubble_coral_block",
                "fire_coral_block", "horn_coral_block");
        price(t, 4000, "heart_of_the_sea");
        price(t, 6000, "conduit");
    }

    private static void lootOnly(TomlDocument doc) {
        TomlTable t = table(doc, "loot_only",
                "Items with no recipe, found only in chests, trades or structures.",
                "Without a price here they would be absent from the shop entirely,",
                "since the solver has no recipe to work from.",
                "",
                "Priced high on purpose: these bypass progression, so they should cost",
                "more than the effort they save.");

        price(t, 800, "saddle", "name_tag");
        price(t, 1200, "lead");
        price(t, 2000, "trident");
        price(t, 3000, "totem_of_undying");
        price(t, 1500, "enchanted_golden_apple");
        price(t, 500, "experience_bottle");
        price(t, 2500, "heavy_core");
        price(t, 600, "ominous_bottle", "trial_key", "ominous_trial_key");
        price(t, 400, "echo_shard", "disc_fragment_5");
        price(t, 1000, "recovery_compass");
        price(t, 300, "sniffer_egg", "turtle_egg");

        price(t, 250, "music_disc_13", "music_disc_cat", "music_disc_blocks",
                "music_disc_chirp", "music_disc_far", "music_disc_mall", "music_disc_mellohi",
                "music_disc_stal", "music_disc_strad", "music_disc_ward", "music_disc_11",
                "music_disc_wait", "music_disc_otherside", "music_disc_5", "music_disc_relic",
                "music_disc_pigstep", "music_disc_creator", "music_disc_creator_music_box",
                "music_disc_precipice", "music_disc_tears", "music_disc_lava_chicken");
    }

    private static void blocked(TomlDocument doc) {
        TomlTable t = table(doc, "blacklisted",
                "An empty price removes an item from the shop entirely - it can be",
                "neither bought nor sold.",
                "",
                "These are creative-only or technical blocks. Selling them would be",
                "harmless, but buying a stack of bedrock or a command block would not,",
                "and the solver can otherwise reach some of them through odd recipes.");

        blacklist(t, "bedrock", "barrier", "command_block", "chain_command_block",
                "repeating_command_block", "command_block_minecart", "structure_block",
                "structure_void", "jigsaw", "debug_stick", "light", "spawner",
                "trial_spawner", "vault", "end_portal_frame", "budding_amethyst",
                "reinforced_deepslate", "petrified_oak_slab", "farmland", "dirt_path",
                "infested_stone", "infested_cobblestone", "infested_stone_bricks",
                "infested_mossy_stone_bricks", "infested_cracked_stone_bricks",
                "infested_chiseled_stone_bricks", "infested_deepslate");
    }

    // ---- helpers -------------------------------------------------------------------

    private static TomlTable table(TomlDocument doc, String name, String... comments) {
        TomlTable t = doc.table(name);
        for (int i = 0; i < comments.length; i++) {
            t.comments().add(comments[i]);
        }
        return t;
    }

    private static void price(TomlTable table, long value, String... itemPaths) {
        for (int i = 0; i < itemPaths.length; i++) {
            table.put("minecraft:" + itemPaths[i], TomlValue.of(value));
        }
    }

    private static void blacklist(TomlTable table, String... itemPaths) {
        for (int i = 0; i < itemPaths.length; i++) {
            table.put("minecraft:" + itemPaths[i], TomlValue.of(""));
        }
    }
}
