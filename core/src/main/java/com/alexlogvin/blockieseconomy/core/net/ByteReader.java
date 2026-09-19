package com.alexlogvin.blockieseconomy.core.net;

import java.io.UnsupportedEncodingException;

/**
 * Reads what {@link ByteWriter} produced.
 *
 * <p>Every read is bounds-checked and every length is sanity-capped. This parses bytes a
 * client sent, so a malformed or hostile payload must produce an exception the receive
 * path can catch and log — never an allocation of {@code Integer.MAX_VALUE} bytes.
 */
public final class ByteReader {

    /** No single string or array in this protocol is anywhere near this large. */
    private static final int MAX_LENGTH = 8 * 1024 * 1024;

    private final byte[] data;
    private int position;

    public ByteReader(byte[] data) {
        this.data = data;
    }

    public int readByte() {
        require(1);
        return data[position++] & 0xFF;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public int readInt() {
        require(4);
        int value = ((data[position] & 0xFF) << 24)
                | ((data[position + 1] & 0xFF) << 16)
                | ((data[position + 2] & 0xFF) << 8)
                | (data[position + 3] & 0xFF);
        position += 4;
        return value;
    }

    public long readLong() {
        long high = readInt() & 0xFFFFFFFFL;
        long low = readInt() & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public int readVarInt() {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalArgumentException("varint is too long");
            }
        }
    }

    public String readString() {
        byte[] bytes = readBytes();
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the JVM spec", e);
        }
    }

    public byte[] readBytes() {
        int length = readLength();
        require(length);
        byte[] out = new byte[length];
        System.arraycopy(data, position, out, 0, length);
        position += length;
        return out;
    }

    /** Everything not yet consumed. */
    public byte[] readRemaining() {
        byte[] out = new byte[data.length - position];
        System.arraycopy(data, position, out, 0, out.length);
        position = data.length;
        return out;
    }

    public int remaining() {
        return data.length - position;
    }

    private int readLength() {
        int length = readVarInt();
        if (length > MAX_LENGTH) {
            throw new IllegalArgumentException("declared length " + length + " is implausible");
        }
        return length;
    }

    private void require(int count) {
        if (count < 0 || position + count > data.length) {
            throw new IllegalArgumentException(
                    "payload ended early: wanted " + count + " more byte(s) at " + position);
        }
    }
}
