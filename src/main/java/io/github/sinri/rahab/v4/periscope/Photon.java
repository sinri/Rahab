package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.web.socket.piece.KeelPiece;
import io.vertx.core.buffer.Buffer;

class Photon implements KeelPiece {
    private Buffer identityBuffer;
    private Buffer contentBuffer;

    public Buffer getIdentityBuffer() {
        return identityBuffer;
    }

    public String getIdentity() {
        return identityBuffer.toString();
    }

    public Buffer getContentBuffer() {
        return contentBuffer;
    }

    /**
     * Bytes:
     * [8 LONG] z=4+x+4+y
     * [4 INT] x
     * [x] identityBuffer
     * [4 INT] y
     * [y] contentBuffer
     */
    @Override
    public Buffer toBuffer() {
        if (this.identityBuffer == null || this.identityBuffer.length() == 0) {
            return null;
        }
        if (this.contentBuffer == null || this.contentBuffer.length() == 0) {
            return null;
        }
        Buffer photonBuffer = Buffer.buffer();
        int x = this.identityBuffer.length();
        int y = this.contentBuffer.length();
        long z = x + y + 8;
        photonBuffer.appendLong(z);// 8 bytes
        photonBuffer.appendInt(x); // 4 bytes
        photonBuffer.appendBuffer(this.identityBuffer);// x bytes
        photonBuffer.appendInt(y);// 4 bytes
        photonBuffer.appendBuffer(this.contentBuffer); // y bytes
        return photonBuffer; // 8+4+x+4+y bytes
    }

    public static Photon create(Buffer identityBuffer, Buffer contentBuffer) {
        Photon photon = new Photon();
        photon.identityBuffer = identityBuffer;
        photon.contentBuffer = contentBuffer;
        return photon;
    }

    public static Photon create(String identity, Buffer contentBuffer) {
        Photon photon = new Photon();
        photon.identityBuffer = Buffer.buffer(identity);
        photon.contentBuffer = contentBuffer;
        return photon;
    }
}
