package io.github.sinri.rahab.v5;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PhotonTest {
    @Test void fragmentedAndCoalescedFrames() {
        Photon.Decoder decoder = new Photon.Decoder();
        List<Photon> frames = new ArrayList<>();
        Buffer wire = new Photon(Photon.DATA, "viewer", Buffer.buffer("hello")).encode()
                .appendBuffer(new Photon(Photon.CLOSE, "viewer", Buffer.buffer()).encode());
        for (byte b : wire.getBytes()) decoder.accept(Buffer.buffer(new byte[]{b}), frames::add);
        assertEquals(2, frames.size());
        assertEquals("hello", frames.get(0).data().toString());
        assertEquals(Photon.CLOSE, frames.get(1).type());
        frames.clear();
        decoder.accept(wire, frames::add);
        assertEquals(2, frames.size());
    }
    @Test void rejectsMalformedLengthsAndTypes() {
        for (int length : new int[]{-1, 0, 2, Integer.MAX_VALUE, Photon.MAX_FRAME + 1}) {
            assertThrows(IllegalArgumentException.class, () ->
                    new Photon.Decoder().accept(Buffer.buffer().appendInt(length), p -> fail()));
        }
        assertThrows(IllegalArgumentException.class, () -> new Photon.Decoder().accept(
                Buffer.buffer().appendInt(3).appendByte((byte)99).appendShort((short)0), p -> fail()));
        assertThrows(IllegalArgumentException.class, () -> new Photon.Decoder().accept(
                Buffer.buffer().appendInt(3).appendByte((byte)2).appendShort((short)100), p -> fail()));
    }
    @Test void truncatedFrameWaitsForRemainingBytes() {
        Buffer wire = new Photon(Photon.DATA, "id", Buffer.buffer("abc")).encode();
        List<Photon> frames = new ArrayList<>();
        Photon.Decoder decoder = new Photon.Decoder();
        decoder.accept(wire.getBuffer(0, wire.length() - 1), frames::add);
        assertTrue(frames.isEmpty());
        decoder.accept(wire.getBuffer(wire.length() - 1, wire.length()), frames::add);
        assertEquals(1, frames.size());
    }
}
