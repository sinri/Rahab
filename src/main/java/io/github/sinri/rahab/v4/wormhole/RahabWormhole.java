package io.github.sinri.rahab.v4.wormhole;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;

public class RahabWormhole {
    private final int wormholePort;
    private final String targetHost;
    private final int targetPort;
    private final KeelLogger logger;
    private final NetServer server;
    private final NetClient clientToTarget;

    public RahabWormhole(int wormholePort, String targetHost, int targetPort) {
        this.wormholePort = wormholePort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.logger = Keel.standaloneLogger("RahabWormholeServer");
        this.server = Keel.getVertx().createNetServer();
        this.clientToTarget = Keel.getVertx().createNetClient();
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void run() {
        this.server
                .connectHandler(socket -> {
                    new RahabWormholeWorker(socket, clientToTarget, targetHost, targetPort)
                            .handle();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("server exception", throwable);
                })
                .listen(wormholePort)
                .onFailure(throwable -> {
                    getLogger().exception("server listen failed, close", throwable);
                    Keel.getVertx().close();
                })
                .onSuccess(server -> {
                    getLogger().info("server listening on " + server.actualPort());
                });
    }
}
