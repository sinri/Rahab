package io.github.sinri.rahab.v4.periscope;

import io.vertx.core.buffer.Buffer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class PhotonProcessor {

    private Buffer buffer;
    private final Queue<Photon> photonQueue;

    public PhotonProcessor() {
        this.buffer = Buffer.buffer();
        this.photonQueue = new ConcurrentLinkedQueue<>();
    }

    /**
     * THREAD SAFE NEEDED
     */
    public void receive(Buffer incomingBuffer) {
        if (incomingBuffer != null && incomingBuffer.length() > 0) {
            buffer.appendBuffer(incomingBuffer);

            while (true) {
                Photon photon = this.parseFirstPhoton();
                if (photon == null) break;
                this.photonQueue.offer(photon);
            }
        }
    }

    public Queue<Photon> getPhotonQueue() {
        return photonQueue;
    }

    /**
     * THREAD SAFE NEEDED
     */
    private Photon parseFirstPhoton() {
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

        buffer = buffer.getBuffer(8 + 4 + x + 4 + y, buffer.length());

        return photon;
    }
}
