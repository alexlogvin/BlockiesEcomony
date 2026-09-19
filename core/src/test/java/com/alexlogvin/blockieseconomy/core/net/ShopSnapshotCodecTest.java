package com.alexlogvin.blockieseconomy.core.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShopSnapshotCodecTest {

    private static Map<String, Long> prices() {
        Map<String, Long> prices = new LinkedHashMap<String, Long>();
        prices.put("minecraft:stick", Long.valueOf(5L));
        prices.put("minecraft:diamond", Long.valueOf(2500L));
        prices.put("minecraft:oak_log", Long.valueOf(40L));
        return prices;
    }

    @Test
    @DisplayName("a full table survives a round trip")
    void fullRoundTrip() {
        Map<String, Long> prices = prices();
        byte[] hash = ShopSnapshotCodec.hash(prices, 0.75);

        ShopSnapshot decoded = ShopSnapshotCodec
                .decode(ShopSnapshotCodec.encodeFull(prices, 0.75, hash), null)
                .snapshot();

        assertNotNull(decoded);
        assertEquals(3, decoded.size());
        assertEquals(2500L, decoded.buyPrice("minecraft:diamond"));
        assertEquals(0.75, decoded.sellMultiplier());
        assertArrayEquals(hash, decoded.hash());
    }

    @Test
    @DisplayName("the hash depends on content, not on map ordering")
    void hashIgnoresOrdering() {
        Map<String, Long> insertionOrder = prices();
        Map<String, Long> sorted = new TreeMap<String, Long>(insertionOrder);

        assertArrayEquals(ShopSnapshotCodec.hash(insertionOrder, 0.75),
                ShopSnapshotCodec.hash(sorted, 0.75));
    }

    @Test
    @DisplayName("the sell multiplier is part of the hash")
    void hashCoversSellMultiplier() {
        // Otherwise an admin halving the sell rate would leave every client showing the
        // old sell prices, because their cached hash would still match.
        assertFalse(ShopSnapshotCodec.sameHash(
                ShopSnapshotCodec.hash(prices(), 0.75),
                ShopSnapshotCodec.hash(prices(), 0.50)));
    }

    @Test
    @DisplayName("sell prices round down, matching the server")
    void sellPriceRoundsDown() {
        Map<String, Long> prices = new TreeMap<String, Long>();
        prices.put("minecraft:stick", Long.valueOf(5L));
        ShopSnapshot snapshot = new ShopSnapshot(prices, 0.75, new byte[] {1});

        assertEquals(3L, snapshot.sellPrice("minecraft:stick"), "5 * 0.75 = 3.75 -> 3");
        assertEquals(-1L, snapshot.sellPrice("minecraft:bedrock"), "unpriced reads as -1");
        assertEquals(-1L, snapshot.buyPrice("minecraft:bedrock"));
    }

    @Test
    @DisplayName("a delta applies onto the table it was built against")
    void deltaApplies() {
        Map<String, Long> base = prices();
        byte[] baseHash = ShopSnapshotCodec.hash(base, 0.75);
        ShopSnapshot held = ShopSnapshotCodec
                .decode(ShopSnapshotCodec.encodeFull(base, 0.75, baseHash), null).snapshot();

        Map<String, Long> updated = new TreeMap<String, Long>(base);
        updated.put("minecraft:diamond", Long.valueOf(3000L));   // changed
        updated.put("minecraft:emerald", Long.valueOf(900L));    // added
        updated.remove("minecraft:stick");                       // removed
        byte[] newHash = ShopSnapshotCodec.hash(updated, 0.75);

        byte[] message = ShopSnapshotCodec.encodeDelta(base, updated, 0.75, baseHash, newHash);
        ShopSnapshot result = ShopSnapshotCodec.decode(message, held).snapshot();

        assertNotNull(result);
        assertEquals(3000L, result.buyPrice("minecraft:diamond"));
        assertEquals(900L, result.buyPrice("minecraft:emerald"));
        assertFalse(result.has("minecraft:stick"));
        assertArrayEquals(newHash, result.hash());

        // The applied result must be byte-identical to a full send of the same table,
        // or the client's next hash check would pointlessly pull the whole thing again.
        assertArrayEquals(ShopSnapshotCodec.hash(result.buyPrices(), 0.75), newHash);
    }

    @Test
    @DisplayName("a delta against an unknown base is refused rather than applied")
    void deltaRefusesWrongBase() {
        Map<String, Long> base = prices();
        Map<String, Long> updated = new TreeMap<String, Long>(base);
        updated.put("minecraft:diamond", Long.valueOf(3000L));

        byte[] message = ShopSnapshotCodec.encodeDelta(base, updated, 0.75,
                ShopSnapshotCodec.hash(base, 0.75), ShopSnapshotCodec.hash(updated, 0.75));

        // The client holds something else entirely.
        Map<String, Long> other = new TreeMap<String, Long>();
        other.put("minecraft:dirt", Long.valueOf(5L));
        ShopSnapshot held = new ShopSnapshot(other, 0.75,
                ShopSnapshotCodec.hash(other, 0.75));

        ShopSnapshotCodec.Decoded decoded = ShopSnapshotCodec.decode(message, held);
        assertEquals(ShopSnapshotCodec.KIND_DELTA, decoded.kind());
        assertNull(decoded.snapshot(), "applying it would silently corrupt the cache");
    }

    @Test
    @DisplayName("an unchanged reply carries no table")
    void unchanged() {
        ShopSnapshotCodec.Decoded decoded =
                ShopSnapshotCodec.decode(ShopSnapshotCodec.encodeUnchanged(), null);
        assertEquals(ShopSnapshotCodec.KIND_UNCHANGED, decoded.kind());
        assertNull(decoded.snapshot());
    }

    @Test
    @DisplayName("an empty or absent hash never counts as a match")
    void emptyHashNeverMatches() {
        // A client with no cache sends a zero-length hash; treating that as equal to the
        // server's would leave it with no prices at all.
        assertFalse(ShopSnapshotCodec.sameHash(new byte[0], new byte[0]));
        assertFalse(ShopSnapshotCodec.sameHash(null, new byte[] {1}));
    }

    @Test
    @DisplayName("a large table is chunked and reassembled intact")
    void chunkedRoundTrip() {
        Map<String, Long> big = new TreeMap<String, Long>();
        for (int i = 0; i < 12_000; i++) {
            big.put("testmod:item_" + i, Long.valueOf(i + 1));
        }
        byte[] payload = ShopSnapshotCodec.encodeFull(big, 0.75,
                ShopSnapshotCodec.hash(big, 0.75));

        List<byte[]> frames = ChunkedTransfer.split(7, payload);
        assertTrue(frames.size() > 1, "a 12k-item table must not fit in one frame");

        ChunkedTransfer.Reassembler reassembler = new ChunkedTransfer.Reassembler();
        byte[] joined = null;
        for (int i = 0; i < frames.size(); i++) {
            byte[] result = reassembler.accept(frames.get(i));
            if (i < frames.size() - 1) {
                assertNull(result, "incomplete transfer must not yield a payload");
            } else {
                joined = result;
            }
        }

        assertArrayEquals(payload, joined);
        assertEquals(12_000, ShopSnapshotCodec.decode(joined, null).snapshot().size());
    }

    @Test
    @DisplayName("frames arriving out of order still reassemble")
    void chunksOutOfOrder() {
        byte[] payload = new byte[ChunkedTransfer.MAX_FRAME_BODY * 3 + 17];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }

        List<byte[]> frames = ChunkedTransfer.split(1, payload);
        ChunkedTransfer.Reassembler reassembler = new ChunkedTransfer.Reassembler();
        assertNull(reassembler.accept(frames.get(2)));
        assertNull(reassembler.accept(frames.get(0)));
        assertNull(reassembler.accept(frames.get(3)));
        assertArrayEquals(payload, reassembler.accept(frames.get(1)));
    }

    @Test
    @DisplayName("a malformed payload throws instead of allocating wildly")
    void rejectsGarbage() {
        // The receive path parses bytes a client sent, so this has to fail cleanly.
        assertThrows(RuntimeException.class,
                () -> ShopSnapshotCodec.decode(new byte[] {(byte) 0xFF, 0x7F, 0x01}, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkedTransfer.Reassembler().accept(new byte[] {0, 5, 2}));
    }
}
