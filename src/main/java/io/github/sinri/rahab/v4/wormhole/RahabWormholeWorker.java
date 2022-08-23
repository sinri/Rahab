package io.github.sinri.rahab.v4.wormhole;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

/**
 * terminal - [PORT]wormhole - [PORT]target
 */
public class RahabWormholeWorker {
    private final NetSocket socketFromTerminal;
    private NetSocket socketToTarget;
    private final String targetHost;
    private final int targetPort;
    private final NetClient clientToTarget;
    private final KeelLogger logger;

    public RahabWormholeWorker(NetSocket socketFromTerminal, NetClient clientToTarget, String targetHost, int targetPort) {
        this.socketFromTerminal = socketFromTerminal;
        this.clientToTarget = clientToTarget;
        this.targetPort = targetPort;
        this.targetHost = targetHost;
        this.logger = Keel.standaloneLogger("RahabWormholeServerWorker");
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void handle() {
        this.socketFromTerminal
                .handler(bufferFromClient -> {
                    getLogger().info("[terminal->wormhole] read " + bufferFromClient.length() + " bytes");
                    getLogger().buffer(bufferFromClient);
                    getSocketToTarget()
                            .compose(socket -> {
                                // socket should be this.socketToTarget
                                Future<Void> future = socket.write(bufferFromClient)
                                        .onSuccess(written -> {
                                            getLogger().debug("[wormhole->target] write done " + bufferFromClient.length() + " bytes");
                                        })
                                        .onFailure(throwable -> {
                                            getLogger().exception("[wormhole->target] write failed", throwable);
                                        });
                                if (socket.writeQueueFull()) {
                                    getLogger().warning("[wormhole->target] PAUSE");
                                    socket.pause();
                                }
                                return future;
                            });
                })
                .endHandler(end -> {
                    getLogger().debug("[terminal->wormhole] end");
                })
                .drainHandler(drain -> {
                    getLogger().debug("[terminal<-wormhole] drain");
                    this.socketFromTerminal.resume();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("[terminal--wormhole] exceptionHandler", throwable);
                })
                .closeHandler(closed -> {
                    getLogger().notice("[terminal--wormhole] closing");
                    if (this.socketToTarget != null) {
                        this.socketToTarget.close();
                    }
                });

    }

    private Future<NetSocket> getSocketToTarget() {
        if (this.socketToTarget != null) {
            return Future.succeededFuture(this.socketToTarget);
        }
        return this.clientToTarget
                .connect(targetPort, targetHost)
                .compose(
                        socket -> {
                            getLogger().info("[wormhole--target] created");
                            this.socketToTarget = socket;

                            this.socketToTarget
                                    .handler(bufferFromTarget -> {
                                        getLogger().info("[wormhole<-target] read " + bufferFromTarget.length() + " bytes");
                                        getLogger().buffer(bufferFromTarget);
                                        this.socketFromTerminal.write(bufferFromTarget)
                                                .onSuccess(written -> {
                                                    getLogger().debug("[terminal<-wormhole] write " + bufferFromTarget.length() + " bytes");
                                                })
                                                .onFailure(throwable -> {
                                                    getLogger().exception("[terminal<-wormhole] write failed", throwable);
                                                });
                                        if (this.socketFromTerminal.writeQueueFull()) {
                                            getLogger().warning("[terminal<-wormhole] PAUSE");
                                            this.socketFromTerminal.pause();
                                        }
                                    })
                                    .endHandler(end -> {
                                        getLogger().info("[wormhole<-target] end");
                                    })
                                    .drainHandler(drain -> {
                                        getLogger().info("[wormhole->target] drain");
                                        this.socketToTarget.resume();
                                    })
                                    .exceptionHandler(throwable -> {
                                        getLogger().exception("[terminal--wormhole] exceptionHandler", throwable);
                                    })
                                    .closeHandler(closed -> {
                                        getLogger().notice("[terminal--wormhole] closing");
                                        this.socketFromTerminal.close();
                                    });
                            return Future.succeededFuture(this.socketToTarget);
                        },
                        throwable -> {
                            getLogger().exception("[wormhole--target] create failed", throwable);
                            this.socketFromTerminal.close();
                            return Future.failedFuture(throwable);
                        }
                );
    }
}
