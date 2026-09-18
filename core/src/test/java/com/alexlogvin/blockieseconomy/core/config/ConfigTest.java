package com.alexlogvin.blockieseconomy.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexlogvin.blockieseconomy.core.price.MultiplierTable;
import com.alexlogvin.blockieseconomy.core.toml.TomlDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    @DisplayName("a default server config round-trips through TOML unchanged")
    void serverConfigRoundTrip() {
        ServerConfig original = new ServerConfig();
        String text = original.toToml().write();
        ServerConfig parsed = ServerConfig.fromToml(TomlDocument.parse(text));

        assertEquals(0.75d, parsed.sellMultiplier(), 1e-9);
        assertEquals(1.3d, parsed.defaultRecipeMultiplier(), 1e-9);
        assertEquals(0L, parsed.startingBalance());
        assertEquals(0L, parsed.deathPenalty());
        assertEquals(100L, parsed.advancementBase());
        assertEquals(1.5d, parsed.advancementExponent(), 1e-9);
        assertEquals(120, parsed.tradesPerMinutePerPlayer());
        assertEquals(2_000, parsed.tradesPerMinuteGlobal());
        assertFalse(parsed.transactionLog());
        assertEquals(2, parsed.adminPermissionLevel());
        assertTrue(parsed.leaderboardPublic());
    }

    @Test
    @DisplayName("the generated file explains the arbitrage ceiling")
    void serverConfigDocumentsTheCeiling() {
        String text = new ServerConfig().toToml().write();

        assertTrue(text.contains("GOOD RULE FORMULA"), text);
        assertTrue(text.contains("1 / sell_multiplier"), text);
        assertTrue(text.contains("1.333"), text);
    }

    @Test
    @DisplayName("free recipe types are written as real entries at 1.0")
    void freeRecipeTypesArePinned() {
        String text = new ServerConfig().toToml().write();

        assertTrue(text.contains("\"minecraft:crafting\" = 1.0"), text);
        assertTrue(text.contains("\"minecraft:stonecutting\" = 1.0"), text);
        assertTrue(text.contains("default_recipe_multiplier = 1.3"), text);
    }

    @Test
    @DisplayName("overridable types appear commented out, as documentation only")
    void overridableTypesAreCommentedOut() {
        String text = new ServerConfig().toToml().write();

        // Present as guidance...
        assertTrue(text.contains("#   \"minecraft:smelting\" = 1.3"), text);
        assertTrue(text.contains("#   \"minecraft:brewing\" = 1.3"), text);

        // ...but not as live entries, so they inherit the default.
        ServerConfig parsed = ServerConfig.fromToml(TomlDocument.parse(text));
        assertFalse(parsed.recipeMultipliers().containsKey("minecraft:smelting"));
        assertEquals(1.3d, parsed.multiplierTable().forType("minecraft:smelting"), 1e-9);
    }

    @Test
    @DisplayName("an exploitable multiplier in the file is clamped and reported")
    void clampsExploitableConfig() {
        String text = "[economy]\n"
                + "sell_multiplier = 0.75\n"
                + "\n"
                + "[recipe_multipliers]\n"
                + "\"minecraft:crafting\" = 1.0\n"
                + "\"minecraft:smelting\" = 1.5\n"
                + "default_recipe_multiplier = 1.3\n";

        ServerConfig config = ServerConfig.fromToml(TomlDocument.parse(text));
        MultiplierTable table = config.multiplierTable();

        assertEquals(MultiplierTable.ceilingFor(0.75d), table.forType("minecraft:smelting"), 1e-9);
        assertTrue(table.clampedEntries().containsKey("minecraft:smelting"));
    }

    @Test
    @DisplayName("hand-edited values are read back")
    void readsEditedValues() {
        String text = "[economy]\n"
                + "sell_multiplier = 0.5\n"
                + "starting_balance = 500\n"
                + "death_penalty = 100\n"
                + "\n"
                + "[advancements]\n"
                + "base_prize = 250\n"
                + "depth_exponent = 2.0\n"
                + "\n"
                + "[limits]\n"
                + "trades_per_minute_per_player = 0\n"
                + "transaction_log = true\n"
                + "\n"
                + "[permissions]\n"
                + "admin_level = 4\n"
                + "leaderboard_public = false\n";

        ServerConfig c = ServerConfig.fromToml(TomlDocument.parse(text));

        assertEquals(0.5d, c.sellMultiplier(), 1e-9);
        assertEquals(500L, c.startingBalance());
        assertEquals(100L, c.deathPenalty());
        assertEquals(250L, c.advancementBase());
        assertEquals(2.0d, c.advancementExponent(), 1e-9);
        assertEquals(0, c.tradesPerMinutePerPlayer());
        assertTrue(c.transactionLog());
        assertEquals(4, c.adminPermissionLevel());
        assertFalse(c.leaderboardPublic());

        // A lower sell multiplier raises the arbitrage ceiling.
        assertEquals(2.0d, c.multiplierTable().ceiling(), 1e-9);
    }

    @Test
    @DisplayName("a client config round-trips, defaulting to the bottom right")
    void clientConfigRoundTrip() {
        ClientConfig original = new ClientConfig();
        original.favorites().add("minecraft:diamond");
        original.favorites().add("minecraft:oak_log");

        ClientConfig parsed = ClientConfig.fromToml(TomlDocument.parse(original.toToml().write()));

        assertEquals(HudAnchor.BOTTOM_RIGHT, parsed.anchor());
        assertEquals(0, parsed.offsetX());
        assertEquals(0, parsed.offsetY());
        assertTrue(parsed.hudVisible());
        assertTrue(parsed.showCurrencyIcon());
        assertEquals(2, parsed.favorites().size());
        assertEquals("minecraft:diamond", parsed.favorites().get(0));
    }

    @Test
    @DisplayName("the client file lists every anchor so nobody has to guess")
    void clientConfigDocumentsAnchors() {
        String text = new ClientConfig().toToml().write();
        for (HudAnchor anchor : HudAnchor.values()) {
            assertTrue(text.contains(anchor.name()), "missing " + anchor + " in:\n" + text);
        }
    }

    @Test
    @DisplayName("an unrecognised anchor falls back instead of breaking the HUD")
    void unknownAnchorFallsBack() {
        ClientConfig c = ClientConfig.fromToml(
                TomlDocument.parse("[hud]\nanchor = \"SOMEWHERE_ELSE\"\n"));
        assertEquals(HudAnchor.BOTTOM_RIGHT, c.anchor());

        // Case and dashes are tolerated, since people type these by hand.
        assertEquals(HudAnchor.TOP_LEFT, HudAnchor.parse("top-left", HudAnchor.BOTTOM_RIGHT));
        assertEquals(HudAnchor.TOP_LEFT, HudAnchor.parse("Top_Left", HudAnchor.BOTTOM_RIGHT));
    }

    @Test
    @DisplayName("anchors resolve to the right screen position")
    void anchorPositioning() {
        int screenW = 854;
        int screenH = 480;
        int textW = 60;
        int textH = 10;
        int margin = 4;

        assertEquals(854 - 60 - 4, HudAnchor.BOTTOM_RIGHT.x(screenW, textW, margin));
        assertEquals(480 - 10 - 4, HudAnchor.BOTTOM_RIGHT.y(screenH, textH, margin));

        assertEquals(4, HudAnchor.TOP_LEFT.x(screenW, textW, margin));
        assertEquals(4, HudAnchor.TOP_LEFT.y(screenH, textH, margin));

        assertEquals((854 - 60) / 2, HudAnchor.TOP_CENTER.x(screenW, textW, margin));
        assertEquals((480 - 10) / 2, HudAnchor.MIDDLE_LEFT.y(screenH, textH, margin));

        assertTrue(HudAnchor.BOTTOM_RIGHT.isRightAligned());
        assertFalse(HudAnchor.BOTTOM_LEFT.isRightAligned());
        assertTrue(HudAnchor.TOP_CENTER.isCentered());
    }

    @Test
    @DisplayName("a missing or empty file yields working defaults rather than an error")
    void emptyFileYieldsDefaults() {
        ServerConfig server = ServerConfig.fromToml(TomlDocument.parse(""));
        assertEquals(0.75d, server.sellMultiplier(), 1e-9);

        ClientConfig client = ClientConfig.fromToml(TomlDocument.parse(""));
        assertEquals(HudAnchor.BOTTOM_RIGHT, client.anchor());
        assertTrue(client.favorites().isEmpty());
    }
}
