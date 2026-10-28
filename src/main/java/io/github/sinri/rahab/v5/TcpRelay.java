package io.github.sinri.rahab.v5;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

/** A connection owns both sockets; pipes apply backpressure to their source. */
final class TcpRelay {
    private TcpRelay() {}

    static void connect(NetSocket source, NetClient client, String host, int port) {
        source.pause();
        boolean[] closed = {false};
        source.closeHandler(v -> closed[0] = true);
        source.exceptionHandler(e -> source.close());
        client.connect(port, host).onFailure(e -> source.close()).onSuccess(target -> {
            if (closed[0]) {
                target.close();
                return;
            }
            bridge(source, target, Buffer.buffer());
        });
    }

    static void bridge(NetSocket source, NetSocket target, Buffer pending) {
        source.closeHandler(v -> target.close());
        target.closeHandler(v -> source.close());
        source.exceptionHandler(e -> { source.close(); target.close(); });
        target.exceptionHandler(e -> { source.close(); target.close(); });
        target.pause();
        // Flush pipelined protocol bytes before resuming the source.
        target.write(pending).onFailure(e -> { source.close(); target.close(); }).onSuccess(v -> {
            source.pipeTo(target).onFailure(e -> { source.close(); target.close(); });
            target.pipeTo(source).onFailure(e -> { source.close(); target.close(); });
            source.resume();
            target.resume();
        });
    }
}
