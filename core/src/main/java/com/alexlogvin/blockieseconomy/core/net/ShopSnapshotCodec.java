package com.alexlogvin.blockieseconomy.core.net;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Serialises the shop price table for the wire and for the client's disk cache.
 *
 * <p>Three shapes share one format byte:
 *
 * <ul>
 *   <li><b>UNCHANGED</b> — the client's cached hash already matches; nothing follows.</li>
 *   <li><b>FULL</b> — the whole table.</li>
 *   <li><b>DELTA</b> — only what changed since a table the client is known to hold. On a
 *       server where an admin edits a handful of prices, this is the difference between
 *       resending twenty kilobytes and sending eighty bytes.</li>
 * </ul>
 *
 * <p>The hash is SHA-256 over the <em>uncompressed</em> full form, so it identifies the
 * table's content and not the compressor's settings. Both sides compute it the same way,
 * which is what lets the client prove to the server what it already has.
 */
public final class ShopSnapshotCodec {

    public static final byte KIND_UNCHANGED = 0;
    public static final byte KIND_FULL = 1;
    public static final byte KIND_DELTA = 2;

    /** Bumped whenever this format changes, so a stale disk cache is discarded. */
    public static final int FORMAT_VERSION = 1;

    private ShopSnapshotCodec() {
    }

    // ---- hashing ---------------------------------------------------------------------

    /** Content hash of a price map plus its sell multiplier. */
    public static byte[] hash(Map<String, Long> buyPrices, double sellMultiplier) {
        ByteWriter out = new ByteWriter(buyPrices.size() * 24 + 16);
        writeBody(out, buyPrices, sellMultiplier);
        try {
            return MessageDigest.getInstance("SHA-256").digest(out.toByteArray());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }

    public static boolean sameHash(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /** A short prefix of the hash, for logs. */
    public static String hashToString(byte[] hash) {
        if (hash == null || hash.length == 0) {
            return "none";
        }
        StringBuilder text = new StringBuilder(16);
        for (int i = 0; i < Math.min(8, hash.length); i++) {
            text.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
            text.append(Character.forDigit(hash[i] & 0xF, 16));
        }
        return text.toString();
    }

    // ---- encoding --------------------------------------------------------------------

    /** The whole table, compressed. */
    public static byte[] encodeFull(Map<String, Long> buyPrices, double sellMultiplier,
                                    byte[] hash) {
        ByteWriter out = new ByteWriter(buyPrices.size() * 24 + 64);
        out.writeByte(KIND_FULL);
        out.writeBytes(hash);
        writeBody(out, buyPrices, sellMultiplier);
        return compress(out.toByteArray());
    }

    /**
     * Only the entries that differ from a table the client already holds.
     *
     * @param base what the client has, keyed by item id
     * @param current what it should have
     */
    public static byte[] encodeDelta(Map<String, Long> base, Map<String, Long> current,
                                     double sellMultiplier, byte[] baseHash, byte[] newHash) {
        TreeMap<String, Long> changed = new TreeMap<String, Long>();
        for (Map.Entry<String, Long> e : current.entrySet()) {
            Long previous = base.get(e.getKey());
            if (previous == null || previous.longValue() != e.getValue().longValue()) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        List<String> removed = new ArrayList<String>();
        for (String itemId : base.keySet()) {
            if (!current.containsKey(itemId)) {
                removed.add(itemId);
            }
        }

        ByteWriter out = new ByteWriter(changed.size() * 24 + removed.size() * 16 + 64);
        out.writeByte(KIND_DELTA);
        out.writeBytes(baseHash);
        out.writeBytes(newHash);
        out.writeDouble(sellMultiplier);
        out.writeVarInt(changed.size());
        for (Map.Entry<String, Long> e : changed.entrySet()) {
            out.writeString(e.getKey());
            out.writeLong(e.getValue().longValue());
        }
        out.writeVarInt(removed.size());
        for (int i = 0; i < removed.size(); i++) {
            out.writeString(removed.get(i));
        }
        return compress(out.toByteArray());
    }

    public static byte[] encodeUnchanged() {
        return compress(new ByteWriter(4).writeByte(KIND_UNCHANGED).toByteArray());
    }

    private static void writeBody(ByteWriter out, Map<String, Long> buyPrices,
                                  double sellMultiplier) {
        // Sorted so the same content always produces the same bytes, and therefore the
        // same hash. A LinkedHashMap from the solver is in solve order, which is not it.
        TreeMap<String, Long> sorted = buyPrices instanceof TreeMap
                ? (TreeMap<String, Long>) buyPrices
                : new TreeMap<String, Long>(buyPrices);
        out.writeDouble(sellMultiplier);
        out.writeVarInt(sorted.size());
        for (Map.Entry<String, Long> e : sorted.entrySet()) {
            out.writeString(e.getKey());
            out.writeLong(e.getValue().longValue());
        }
    }

    // ---- decoding --------------------------------------------------------------------

    /** What a decoded message turned out to be. */
    public static final class Decoded {
        private final byte kind;
        private final ShopSnapshot snapshot;
        private final byte[] baseHash;

        Decoded(byte kind, ShopSnapshot snapshot, byte[] baseHash) {
            this.kind = kind;
            this.snapshot = snapshot;
            this.baseHash = baseHash;
        }

        public byte kind() {
            return kind;
        }

        /** Null for UNCHANGED, and for a DELTA whose base did not match. */
        public ShopSnapshot snapshot() {
            return snapshot;
        }

        /** For a DELTA, the hash of the table it was computed against. */
        public byte[] baseHash() {
            return baseHash;
        }
    }

    /**
     * Decodes a message, applying a delta to what the client already had.
     *
     * @param existing the client's current snapshot, or null
     */
    public static Decoded decode(byte[] compressed, ShopSnapshot existing) {
        ByteReader in = new ByteReader(decompress(compressed));
        byte kind = (byte) in.readByte();

        if (kind == KIND_UNCHANGED) {
            return new Decoded(kind, null, null);
        }

        if (kind == KIND_FULL) {
            byte[] hash = in.readBytes();
            double sellMultiplier = in.readDouble();
            int count = in.readVarInt();
            TreeMap<String, Long> prices = new TreeMap<String, Long>();
            for (int i = 0; i < count; i++) {
                String itemId = in.readString();
                prices.put(itemId, Long.valueOf(in.readLong()));
            }
            return new Decoded(kind, new ShopSnapshot(prices, sellMultiplier, hash), null);
        }

        if (kind == KIND_DELTA) {
            byte[] baseHash = in.readBytes();
            byte[] newHash = in.readBytes();
            if (existing == null || !sameHash(existing.hash(), baseHash)) {
                // The server built this against a table we do not have. Nothing to apply;
                // the caller asks for a full one instead of silently corrupting the cache.
                return new Decoded(kind, null, baseHash);
            }
            double sellMultiplier = in.readDouble();
            TreeMap<String, Long> prices = new TreeMap<String, Long>(existing.buyPrices());
            int changed = in.readVarInt();
            for (int i = 0; i < changed; i++) {
                String itemId = in.readString();
                prices.put(itemId, Long.valueOf(in.readLong()));
            }
            int removed = in.readVarInt();
            for (int i = 0; i < removed; i++) {
                prices.remove(in.readString());
            }
            return new Decoded(kind, new ShopSnapshot(prices, sellMultiplier, newHash), baseHash);
        }

        throw new IllegalArgumentException("unknown price message kind " + kind);
    }

    // ---- compression -----------------------------------------------------------------

    private static byte[] compress(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            // The uncompressed length is prefixed so the reader can size its buffer once
            // rather than growing it, and can reject an implausible claim up front.
            ByteWriter framed = new ByteWriter(out.size() + 8);
            framed.writeVarInt(raw.length);
            byte[] body = out.toByteArray();
            framed.writeRaw(body, 0, body.length);
            return framed.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] decompress(byte[] framed) {
        ByteReader in = new ByteReader(framed);
        int expected = in.readVarInt();
        if (expected > 64 * 1024 * 1024) {
            throw new IllegalArgumentException("implausible decompressed size " + expected);
        }
        byte[] body = in.readRemaining();

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(body);
            byte[] out = new byte[expected];
            int written = 0;
            while (written < expected && !inflater.finished()) {
                int n = inflater.inflate(out, written, expected - written);
                if (n == 0 && inflater.needsInput()) {
                    throw new IllegalArgumentException("compressed payload ended early");
                }
                written += n;
            }
            if (written != expected) {
                throw new IllegalArgumentException(
                        "decompressed " + written + " bytes, expected " + expected);
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("corrupt compressed payload", e);
        } finally {
            inflater.end();
        }
    }
}
