package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.web.websockets.KeelWebSocketHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

public class ConsulateServerHandler extends KeelWebSocketHandler {
    private final NetClient netClient;
    private NetSocket socketToTargetServer;

    private final Queue<Buffer> readBufferQueue = new ArrayDeque<>();

    public ConsulateServerHandler(ServerWebSocket webSocket) {
        super(webSocket);

        netClient = Keel.getVertx().createNetClient();
    }

    protected String getSocks5Host() {
        return config().getString("socks5_host");
        //return "127.0.0.1";
    }

    protected int getSocks5Port() {
        return config().getInteger("socks5_port");
        //return 10000;
    }

    @Override
    protected KeelLogger prepareLogger() {
        return Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID());
    }

    @Override
    protected void accept() {
        // accept a new ws connection from client
        getLogger().info("ACCEPT FROM " + this.getWebSocketRemoteAddress() + " BY LOCAL " + this.getWebSocketLocalAddress());

        // this.writeText("ACCEPTED");
        netClient.connect(getSocks5Port(), getSocks5Host())
                .onSuccess(socket -> {
                    getLogger().info("ConsulateServerHandler::socketToTargetServer 建立");
                    this.socketToTargetServer = socket;
                    this.socketToTargetServer
                            .handler(buffer -> {
                                // from actual server
                                getLogger().info("socketToTargetServer RECEIVED FROM Actual Server: " + buffer.toString());
                                this.writeBuffer(buffer)
                                        .onComplete(asyncResult -> {
                                            if (asyncResult.failed()) {
                                                getLogger().exception("socketToTargetServer WRITE TO CLIENT FAILED", asyncResult.cause());
                                            } else {
                                                getLogger().info("socketToTargetServer WRITE TO CLIENT DONE");
                                            }
                                        });
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("socketToTargetServer ERROR", throwable);
                            })
                            .endHandler(ended -> {
                                getLogger().info("socketToTargetServer ENDED");
                                this.undeployMe();
                            });
                    handleReadBuffers();
                })
                .onFailure(throwable -> {
                    getLogger().exception("socketToTargetServer 建立到 Socks5 Proxy Server 的连接失败", throwable);
                });
    }

    @Override
    protected void handleBuffer(Buffer buffer) {
        // turn to socks5 proxy
        getLogger().info("RECEIVED FROM Consulate Client: " + buffer.toString());
        this.readBufferQueue.add(buffer);
        handleReadBuffers();
    }

    @Override
    protected void handleException(Throwable throwable) {
        getLogger().exception("Consulate Server ERROR", throwable);
    }

    @Override
    protected void handleEnd() {
        getLogger().info("Consulate Server END");
    }

    private synchronized void handleReadBuffers() {
        if (this.socketToTargetServer == null) return;
        while (!this.readBufferQueue.isEmpty()) {
            Buffer buffer = this.readBufferQueue.remove();
            this.socketToTargetServer.write(buffer);
        }
    }
}
