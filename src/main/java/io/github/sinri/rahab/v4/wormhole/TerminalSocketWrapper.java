package io.github.sinri.rahab.v4.wormhole;

import io.github.sinri.keel.web.tcp.KeelAbstractSocketWrapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

/**
 * terminal - [PORT]wormhole - [PORT]target
 */
class TerminalSocketWrapper extends KeelAbstractSocketWrapper {
    private NetSocket socketToTarget;
    private final String targetHost;
    private final int targetPort;
    private final NetClient clientToTarget;

    private TerminalSocketWrapper(NetSocket socketFromTerminal, NetClient clientToTarget, String targetHost, int targetPort) {
        super(socketFromTerminal);
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.clientToTarget = clientToTarget;
    }

    public static TerminalSocketWrapper build(NetSocket socketFromTerminal, NetClient clientToTarget, String targetHost, int targetPort) {
        return new TerminalSocketWrapper(socketFromTerminal, clientToTarget, targetHost, targetPort);
    }

    @Override
    protected Future<Void> whenBufferComes(Buffer bufferFromTerminal) {
        getLogger().info("[terminal->wormhole] read " + bufferFromTerminal.length() + " bytes");
        getLogger().buffer(bufferFromTerminal);
        return getSocketToTarget()
                .compose(socket -> {
                    // socket should be this.socketToTarget
                    Future<Void> future = socket.write(bufferFromTerminal)
                            .onSuccess(written -> {
                                getLogger().debug("[wormhole->target] write done " + bufferFromTerminal.length() + " bytes");
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
    }

    @Override
    protected void whenClose() {
        super.whenClose();
        if (this.socketToTarget != null) {
            this.socketToTarget.close()
                    .onSuccess(v -> {
                        this.socketToTarget = null;
                    });
        }
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
                                        this.write(bufferFromTarget)
                                                .onSuccess(written -> {
                                                    getLogger().debug("[terminal<-wormhole] write " + bufferFromTarget.length() + " bytes");
                                                })
                                                .onFailure(throwable -> {
                                                    getLogger().exception("[terminal<-wormhole] write failed", throwable);
                                                });
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
                                        this.close();
                                    });
                            return Future.succeededFuture(this.socketToTarget);
                        },
                        throwable -> {
                            getLogger().exception("[wormhole--target] create failed", throwable);
                            return this.close()
                                    .compose(v -> {
                                        return Future.failedFuture(throwable);
                                    });
                        }
                );
    }
}
