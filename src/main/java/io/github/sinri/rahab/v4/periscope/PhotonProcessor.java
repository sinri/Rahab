package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.web.socket.piece.KeelPieceKit;
import io.vertx.core.buffer.Buffer;

class PhotonProcessor extends KeelPieceKit<Photon> {

    @Override
    protected Photon parseFirstPieceFromBuffer() {
        var buffer = getBuffer();

        if (buffer.length() == 0) return null;

        long z = buffer.getLong(0);// 4+x+4+y
        if (buffer.length() < 8 + z) {
            return null;
        }
        int x = buffer.getInt(8);
        Buffer identityBuffer = buffer.getBuffer(8 + 4, 8 + 4 + x);
        int y = buffer.getInt(8 + 4 + x);
        Buffer contentBuffer = buffer.getBuffer(8 + 4 + x + 4, 8 + 4 + x + 4 + y);
        Photon photon = Photon.create(identityBuffer, contentBuffer);

        this.cutBuffer(8 + 4 + x + 4 + y);

        return photon;
    }
}
