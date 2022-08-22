package io.github.sinri.rahab.v4.liaison.entity;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

public class SocketWrapper {
    private String socketName;
    private NetSocket socket;
    private final KeelLogger logger;

    public SocketWrapper() {
        this.socketName = null;
        this.socket = null;
        this.logger = Keel.standaloneLogger("LiaisonSocketWrapper");
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void update(String socketName, NetSocket socket) {
        this.socketName = socketName;
        this.socket = socket;
        this.logger.setContentPrefix(socketName);
    }

    public Future<Void> write(Buffer buffer) {
        if (this.socket == null) {
            getLogger().error("socket is null");
            return Future.failedFuture("SocketWrapper: socket is null for name " + socketName);
        }
        getLogger().debug("to write " + buffer.length() + " bytes");
        getLogger().buffer(buffer);
        return socket.write(buffer)
                .compose(v -> {
                    getLogger().debug("written " + buffer.length() + " bytes");
                    if (socket.writeQueueFull()) {
                        getLogger().warning("socket PAUSE");
                        socket.pause();
                    }
                    return Future.succeededFuture();
                }, throwable -> {
                    getLogger().exception("write failed for " + buffer.length() + " bytes", throwable);
                    if (socket.writeQueueFull()) {
                        getLogger().warning("socket PAUSE");
                        socket.pause();
                    }
                    return Future.failedFuture(throwable);
                });
    }

    public Future<Void> close() {
        if (this.socket != null) {
            getLogger().info("close");
            return this.socket.close();
        } else {
            return Future.succeededFuture();
        }
    }
}
