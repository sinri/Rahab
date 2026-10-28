package io.github.sinri.rahab.v5;

import io.vertx.core.buffer.Buffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** V5 frame: length:int, type:byte, idLength:unsigned short, UTF-8 id, payload. */
record Photon(int type, String id, Buffer data) {
    static final int OPEN = 1, DATA = 2, CLOSE = 3, PING = 4, PONG = 5, AUTH = 6, READY = 7;
    static final int MAX_FRAME = 1024 * 1024;

    Buffer encode() {
        byte[] name = id.getBytes(StandardCharsets.UTF_8);
        int size = 3 + name.length + data.length();
        if (name.length > 256 || size > MAX_FRAME) throw new IllegalArgumentException("Frame too large");
        return Buffer.buffer().appendInt(size).appendByte((byte) type)
                .appendUnsignedShort(name.length).appendBytes(name).appendBuffer(data);
    }

    static final class Decoder {
        private Buffer pending = Buffer.buffer();
        void accept(Buffer input, Consumer<Photon> consumer) {
            // Consume each complete frame before retaining subsequent frames.
            int offset = 0;
            while (offset < input.length()) {
                int needed = pending.length() < 4 ? 4 - pending.length() : 4 + size() - pending.length();
                int take = Math.min(needed, input.length() - offset);
                pending.appendBuffer(input.getBuffer(offset, offset + take));
                offset += take;
                if (pending.length() < 4) continue;
                int length = size();
                if (pending.length() < 4 + length) continue;
                int type = pending.getUnsignedByte(4);
                int nameLength = pending.getUnsignedShort(5);
                if (type < OPEN || type > READY || nameLength > 256 || 3 + nameLength > length)
                    throw new IllegalArgumentException("Invalid frame header");
                Photon photon = new Photon(type, pending.getString(7, 7 + nameLength, "UTF-8"),
                        pending.getBuffer(7 + nameLength, 4 + length));
                pending = Buffer.buffer();
                consumer.accept(photon);
            }
        }
        private int size() {
            int size = pending.getInt(0);
            if (size < 3 || size > MAX_FRAME) throw new IllegalArgumentException("Invalid frame length");
            return size;
        }
    }
}
