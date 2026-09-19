package com.alexlogvin.blockieseconomy.core.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a large payload across several packets and puts it back together.
 *
 * <p>Needed because a modpack price table can outgrow what one custom payload may carry.
 * Vanilla's own limit is version-dependent and the loaders each narrow it differently, so
 * rather than find out the hard way on someone's 12,000-item pack, everything over
 * {@link #MAX_FRAME_BODY} is framed here — well under every limit involved.
 *
 * <p>Frame layout: {@code varint transferId, varint index, varint total, raw body}. The
 * transfer id lets a second rebuild started while the first is still arriving be told
 * apart from it, instead of interleaving into a corrupt blob.
 */
public final class ChunkedTransfer {

    /** Comfortably below the smallest custom-payload limit across the supported versions. */
    public static final int MAX_FRAME_BODY = 24 * 1024;

    /** Refuse a transfer claiming more frames than any real table could need. */
    private static final int MAX_FRAMES = 4096;

    private ChunkedTransfer() {
    }

    /** Splits a payload into wire-ready frames. Always returns at least one. */
    public static List<byte[]> split(int transferId, byte[] payload) {
        int total = Math.max(1, (payload.length + MAX_FRAME_BODY - 1) / MAX_FRAME_BODY);
        List<byte[]> frames = new ArrayList<byte[]>(total);
        for (int index = 0; index < total; index++) {
            int offset = index * MAX_FRAME_BODY;
            int length = Math.min(MAX_FRAME_BODY, payload.length - offset);
            ByteWriter out = new ByteWriter(length + 16);
            out.writeVarInt(transferId);
            out.writeVarInt(index);
            out.writeVarInt(total);
            out.writeRaw(payload, offset, length);
            frames.add(out.toByteArray());
        }
        return frames;
    }

    /**
     * Collects frames until a transfer is complete.
     *
     * <p>Single-threaded by contract: the receive path hands frames over on the client
     * thread, after the loader layer has taken them off netty.
     */
    public static final class Reassembler {

        private final Map<Integer, byte[][]> pending = new HashMap<Integer, byte[][]>();

        /**
         * Accepts one frame.
         *
         * @return the completed payload, or null while frames are still outstanding
         */
        public byte[] accept(byte[] frame) {
            ByteReader in = new ByteReader(frame);
            int transferId = in.readVarInt();
            int index = in.readVarInt();
            int total = in.readVarInt();

            if (total <= 0 || total > MAX_FRAMES || index < 0 || index >= total) {
                throw new IllegalArgumentException(
                        "frame " + index + " of " + total + " is out of range");
            }

            byte[] body = in.readRemaining();
            if (total == 1) {
                pending.remove(Integer.valueOf(transferId));
                return body;
            }

            byte[][] slots = pending.get(Integer.valueOf(transferId));
            if (slots == null) {
                // A newer transfer supersedes anything still in flight: the older one can
                // only be a table this client is about to be told is out of date anyway.
                pending.clear();
                slots = new byte[total][];
                pending.put(Integer.valueOf(transferId), slots);
            }
            if (slots.length != total) {
                throw new IllegalArgumentException("frame count changed mid-transfer");
            }
            slots[index] = body;

            int size = 0;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) {
                    return null;
                }
                size += slots[i].length;
            }

            byte[] joined = new byte[size];
            int offset = 0;
            for (int i = 0; i < slots.length; i++) {
                System.arraycopy(slots[i], 0, joined, offset, slots[i].length);
                offset += slots[i].length;
            }
            pending.remove(Integer.valueOf(transferId));
            return joined;
        }

        /** Drops anything half-received. Called on disconnect. */
        public void clear() {
            pending.clear();
        }
    }
}
