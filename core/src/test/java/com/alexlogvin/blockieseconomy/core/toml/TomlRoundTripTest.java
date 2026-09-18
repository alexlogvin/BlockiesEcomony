package com.alexlogvin.blockieseconomy.core.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TomlRoundTripTest {

    @Test
    @DisplayName("comments above entries survive a parse/write round trip")
    void preservesEntryComments() {
        String source = "# Recipe-type multipliers.\n"
                + "#\n"
                + "# GOOD RULE FORMULA: stay below 1 / sell_multiplier.\n"
                + "\n"
                + "# Needs no extra resources, so no markup.\n"
                + "\"minecraft:crafting\" = 1.0\n"
                + "default_recipe_multiplier = 1.3\n";

        TomlDocument doc = TomlDocument.parse(source);

        assertEquals(Arrays.asList(
                "Recipe-type multipliers.",
                "",
                "GOOD RULE FORMULA: stay below 1 / sell_multiplier."),
                doc.headerComments());

        TomlEntry crafting = doc.root().get("minecraft:crafting");
        assertEquals(Arrays.asList("Needs no extra resources, so no markup."), crafting.comments());

        String written = doc.write();
        assertTrue(written.contains("# GOOD RULE FORMULA: stay below 1 / sell_multiplier."), written);
        assertTrue(written.contains("# Needs no extra resources, so no markup."), written);

        // Re-parsing the output must yield the same document.
        TomlDocument again = TomlDocument.parse(written);
        assertEquals(doc.headerComments(), again.headerComments());
        assertEquals(1.0d, again.getDouble("", "minecraft:crafting", -1), 0.0);
        assertEquals(1.3d, again.getDouble("", "default_recipe_multiplier", -1), 0.0);
    }

    @Test
    @DisplayName("quoted keys carrying a colon are read and written back quoted")
    void handlesQuotedKeys() {
        TomlDocument doc = TomlDocument.parse("\"minecraft:smelting\" = 1.3\n");
        assertEquals(1.3d, doc.getDouble("", "minecraft:smelting", -1), 0.0);
        assertTrue(doc.write().contains("\"minecraft:smelting\""));
    }

    @Test
    @DisplayName("an empty string marks an item blacklisted and is distinct from absent")
    void emptyStringMeansBlacklisted() {
        TomlDocument doc = TomlDocument.parse(
                "\"minecraft:bedrock\" = \"\"\n\"minecraft:dirt\" = 5\n");

        TomlValue bedrock = doc.value("", "minecraft:bedrock");
        assertTrue(bedrock.isEmptyString());

        TomlValue dirt = doc.value("", "minecraft:dirt");
        assertFalse(dirt.isEmptyString());
        assertEquals(5L, dirt.asLong());

        // Absent is not the same as blacklisted: one means "derive it", the other "never sell it".
        assertNull(doc.value("", "minecraft:stone"));
    }

    @Test
    @DisplayName("tables keep their own comments and entries")
    void handlesTables() {
        String source = "# file header\n"
                + "\n"
                + "# What the shop charges on top.\n"
                + "[multipliers]\n"
                + "sell = 0.75\n"
                + "\n"
                + "[limits]\n"
                + "trades_per_minute = 120\n";

        TomlDocument doc = TomlDocument.parse(source);

        assertEquals(Arrays.asList("file header"), doc.headerComments());
        assertEquals(Arrays.asList("What the shop charges on top."),
                doc.findTable("multipliers").comments());
        assertEquals(0.75d, doc.getDouble("multipliers", "sell", -1), 0.0);
        assertEquals(120L, doc.getLong("limits", "trades_per_minute", -1));

        TomlDocument again = TomlDocument.parse(doc.write());
        assertEquals(0.75d, again.getDouble("multipliers", "sell", -1), 0.0);
        assertEquals(120L, again.getLong("limits", "trades_per_minute", -1));
        assertEquals(Arrays.asList("What the shop charges on top."),
                again.findTable("multipliers").comments());
    }

    @Test
    @DisplayName("inline comments stay attached to their entry")
    void preservesInlineComments() {
        TomlDocument doc = TomlDocument.parse("starting_balance = 0  # new players begin broke\n");
        assertEquals("new players begin broke", doc.root().get("starting_balance").inlineComment());
        assertTrue(doc.write().contains("# new players begin broke"));
    }

    @Test
    @DisplayName("a hash inside a string is not treated as a comment")
    void hashInsideStringIsNotAComment() {
        TomlDocument doc = TomlDocument.parse("tag = \"#c:ingots\"\n");
        assertEquals("#c:ingots", doc.getString("", "tag", null));
        assertNull(doc.root().get("tag").inlineComment());
    }

    @Test
    @DisplayName("arrays of strings and numbers parse and re-render")
    void handlesArrays() {
        TomlDocument doc = TomlDocument.parse(
                "blocked = [\"minecraft:bedrock\", \"minecraft:barrier\"]\ntiers = [1, 2, 3]\n");

        assertEquals(2, doc.value("", "blocked").asArray().size());
        assertEquals("minecraft:barrier", doc.value("", "blocked").asArray().get(1).asString());
        assertEquals(3, doc.value("", "tiers").asArray().size());

        TomlDocument again = TomlDocument.parse(doc.write());
        assertEquals(2, again.value("", "blocked").asArray().size());
    }

    @Test
    @DisplayName("booleans, negative numbers and underscores in numbers all parse")
    void handlesScalarForms() {
        TomlDocument doc = TomlDocument.parse(
                "enabled = true\ndeath_penalty = -25\nbig = 1_000_000\nrate = 0.75\n");

        assertTrue(doc.getBoolean("", "enabled", false));
        assertEquals(-25L, doc.getLong("", "death_penalty", 0));
        assertEquals(1000000L, doc.getLong("", "big", 0));
        assertEquals(0.75d, doc.getDouble("", "rate", 0), 0.0);
    }

    @Test
    @DisplayName("values written from code render in valid TOML")
    void writesProgrammaticValues() {
        TomlDocument doc = new TomlDocument();
        doc.headerComments().add("Generated file. Do not edit.");
        TomlTable prices = doc.table("prices");
        prices.put("minecraft:dirt", TomlValue.of(5L));
        prices.put("minecraft:diamond", TomlValue.of(2500L));
        prices.put("multiplier", TomlValue.of(1.3d));
        prices.put("note", TomlValue.of("derived"));
        prices.put("blacklisted", TomlValue.of(""));

        TomlDocument again = TomlDocument.parse(doc.write());
        assertEquals(5L, again.getLong("prices", "minecraft:dirt", -1));
        assertEquals(2500L, again.getLong("prices", "minecraft:diamond", -1));
        // A whole-number double must not round-trip as an integer.
        assertEquals(TomlValue.Kind.FLOAT, again.value("prices", "multiplier").kind());
        assertEquals("derived", again.getString("prices", "note", null));
        assertTrue(again.value("prices", "blacklisted").isEmptyString());
    }

    @Test
    @DisplayName("malformed input reports the offending line")
    void reportsLineNumbers() {
        TomlException e = assertThrows(TomlException.class,
                () -> TomlDocument.parse("good = 1\nthis is not toml\n"));
        assertTrue(e.getMessage().startsWith("line 2:"), e.getMessage());
    }

    @Test
    @DisplayName("escape sequences survive a round trip")
    void handlesEscapes() {
        TomlDocument doc = TomlDocument.parse("msg = \"a\\\"b\\nc\"\n");
        assertEquals("a\"b\nc", doc.getString("", "msg", null));

        TomlDocument built = new TomlDocument();
        built.root().put("msg", TomlValue.of("a\"b\nc"));
        assertEquals("a\"b\nc", TomlDocument.parse(built.write()).getString("", "msg", null));
    }
}
