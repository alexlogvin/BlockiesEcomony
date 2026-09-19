package com.alexlogvin.blockieseconomy.core.net;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A minimal growable byte writer, matched by {@link ByteReader}.
 *
 * <p>Deliberately not Minecraft's {@code FriendlyByteBuf}. That class lives in a package
 * whose networking API changed shape in 1.20.5 — {@code CustomPacketPayload} and
 * {@code StreamCodec} replaced raw buffers — so building payloads with it would push a
 * version fork into every packet class. Encoding to a plain {@code byte[]} here means the
 * loader layer only ever has to move an opaque array, and that operation is identical on
 * every version.
 *
 * <p>Varints are used for lengths and counts because a price table is mostly small
 * numbers; the format is the standard 7-bits-plus-continuation encoding.
 */
public final class ByteWriter {

    private final ByteArrayOutputStream out;

    public ByteWriter() {
        this(256);
    }

    public ByteWriter(int initialCapacity) {
        this.out = new ByteArrayOutputStream(initialCapacity);
    }

    public ByteWriter writeByte(int value) {
        out.write(value & 0xFF);
        return this;
    }

    public ByteWriter writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public ByteWriter writeInt(int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    public ByteWriter writeLong(long value) {
        writeInt((int) (value >>> 32));
        writeInt((int) value);
        return this;
    }

    public ByteWriter writeDouble(double value) {
        return writeLong(Double.doubleToLongBits(value));
    }

    /** Unsigned varint. Negative values are rejected rather than silently taking 5 bytes. */
    public ByteWriter writeVarInt(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("varint must not be negative: " + value);
        }
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write(remaining);
        return this;
    }

    /** Length-prefixed UTF-8. */
    public ByteWriter writeString(String value) {
        byte[] bytes;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the JVM spec", e);
        }
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
        return this;
    }

    public ByteWriter writeBytes(byte[] value) {
        writeVarInt(value.length);
        out.write(value, 0, value.length);
        return this;
    }

    /** Raw, with no length prefix. The reader must already know how many to expect. */
    public ByteWriter writeRaw(byte[] value, int offset, int length) {
        out.write(value, offset, length);
        return this;
    }

    public int size() {
        return out.size();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
