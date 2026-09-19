package com.alexlogvin.blockieseconomy.core.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ByteCodecTest {

    @Test
    @DisplayName("every primitive survives a round trip")
    void roundTrip() {
        byte[] encoded = new ByteWriter()
                .writeByte(200)
                .writeBoolean(true)
                .writeInt(-123_456)
                .writeLong(Long.MIN_VALUE)
                .writeDouble(0.75)
                .writeVarInt(0)
                .writeVarInt(Integer.MAX_VALUE)
                .writeString("minecraft:oak_log")
                .writeString("Кирпич")
                .writeBytes(new byte[] {1, 2, 3})
                .toByteArray();

        ByteReader in = new ByteReader(encoded);
        assertEquals(200, in.readByte());
        assertTrue(in.readBoolean());
        assertEquals(-123_456, in.readInt());
        assertEquals(Long.MIN_VALUE, in.readLong());
        assertEquals(0.75, in.readDouble());
        assertEquals(0, in.readVarInt());
        assertEquals(Integer.MAX_VALUE, in.readVarInt());
        assertEquals("minecraft:oak_log", in.readString());
        assertEquals("Кирпич", in.readString(), "non-ASCII must survive as UTF-8");
        assertArrayEquals(new byte[] {1, 2, 3}, in.readBytes());
        assertEquals(0, in.remaining());
    }

    @Test
    @DisplayName("small varints stay small")
    void varintIsCompact() {
        assertEquals(1, new ByteWriter().writeVarInt(127).size());
        assertEquals(2, new ByteWriter().writeVarInt(128).size());
    }

    @Test
    @DisplayName("reading past the end throws rather than returning junk")
    void boundsChecked() {
        ByteReader in = new ByteReader(new byte[] {1, 2});
        assertThrows(IllegalArgumentException.class, () -> in.readLong());
    }

    @Test
    @DisplayName("an implausible declared length is refused before allocating")
    void rejectsHugeLength() {
        // A hostile client can claim any length it likes; believing it is how you turn a
        // bad packet into an OutOfMemoryError.
        byte[] hostile = new ByteWriter().writeVarInt(Integer.MAX_VALUE).toByteArray();
        assertThrows(IllegalArgumentException.class,
                () -> new ByteReader(hostile).readBytes());
    }

    @Test
    @DisplayName("a varint with no terminator is refused")
    void rejectsRunawayVarint() {
        byte[] hostile = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80};
        assertThrows(IllegalArgumentException.class,
                () -> new ByteReader(hostile).readVarInt());
    }
}
